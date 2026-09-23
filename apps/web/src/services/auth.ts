const configuredApiUrl = (window as Window & { NEXA_API_BASE_URL?: string }).NEXA_API_BASE_URL;
const API_BASE_URL = (configuredApiUrl || "http://localhost:8088/api/v1").replace(/\/$/, "");
const AUTH_STORAGE_KEY = "nexa-auth-session";

export type AuthUser = {
  id: string;
  email: string;
  role: "CUSTOMER" | "ADMIN" | "SUPPORT_AGENT" | "FRAUD_ANALYST";
};

export type CustomerProfile = {
  address?: string | null;
  id: string;
  userId: string;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  status: string;
  role: "CUSTOMER" | "ADMIN" | "SUPPORT_AGENT" | "FRAUD_ANALYST";
};

type AuthenticationResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: AuthUser;
};

type StoredAuthentication = AuthenticationResponse;

export type AuthSession = StoredAuthentication & {
  profile: CustomerProfile;
};

type ApiErrorBody = {
  detail?: string;
  error?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
};

export class ApiRequestError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "ApiRequestError";
  }
}

async function request<T>(path: string, init: RequestInit = {}, accessToken?: string, core = false, binary = false): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (typeof FormData !== "undefined" && init.body instanceof FormData) headers.delete("Content-Type");
  else if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", "Bearer " + accessToken);
  const controller = new AbortController();
  const abort = () => controller.abort();
  init.signal?.addEventListener("abort", abort, {once: true});
  // Local CPU inference needs more time; financial confirmations keep the normal timeout.
  const chatTurn = init.method === "POST" && /^\/conversations\/[^/]+\/turns$/.test(path);
  const timeout = window.setTimeout(abort, chatTurn ? 65000 : 20000);
  try {
    if (init.signal?.aborted) controller.abort();
    const response = await fetch((core ? API_BASE_URL.replace(/\/v1\/?$/, "") : API_BASE_URL) + path, {...init, headers, signal: controller.signal});
    if (response.status === 204) return undefined as T;
    if (binary && response.ok) return await response.blob() as T;
    let body: ApiErrorBody & T;
    try { body = await response.json(); }
    catch (error) {
      if (response.ok || controller.signal.aborted) throw error;
      body = {} as ApiErrorBody & T;
    }
    if (!response.ok) {
      const fields = Array.isArray(body?.fieldErrors) ? body.fieldErrors.map(error => error.message).join(" ") : "";
      const fallback = response.status === 403 ? "You do not have access to this information." : response.status === 404 ? "This information could not be found." : response.status >= 500 ? "The bank could not complete this request. Please try again later." : "This request could not be completed. Check your details and try again.";
      throw new ApiRequestError(response.status, response.status >= 500 ? fallback : fields || body?.detail || body?.error || fallback);
    }
    return body;
  } catch (error) {
    if (error instanceof ApiRequestError) throw error;
    throw new ApiRequestError(0, init.method && init.method !== "GET" ? "The response could not be confirmed. Check your account before submitting again." : "We couldn’t read the banking response. Check your connection and try again.");
  } finally {
    window.clearTimeout(timeout);
    init.signal?.removeEventListener("abort", abort);
  }
}

export function authenticatedRequest<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  return authorized<T>(path, accessToken, init);
}

export function authenticatedBlobRequest(path: string, accessToken: string): Promise<Blob> {
  return authorized<Blob>(path, accessToken, { cache: "no-store" }, false, true);
}

let refreshInFlight: Promise<StoredAuthentication> | null = null;
let sessionGeneration = 0;

async function authorized<T>(path: string, accessToken: string, init: RequestInit, core = false, binary = false): Promise<T> {
  const generation = sessionGeneration;
  const stored = readStoredAuthentication();
  const token = stored?.accessToken || accessToken;
  try { return await request<T>(path, init, token, core, binary); }
  catch (error) {
    if (!(error instanceof ApiRequestError) || error.status !== 401) throw error;
    if (generation !== sessionGeneration) throw error;
    const latest = readStoredAuthentication();
    if (!latest) { window.dispatchEvent(new Event("nexa-session-expired")); throw error; }
    let refreshed: StoredAuthentication;
    try {
      refreshed = latest.accessToken !== token ? latest : await refreshOnce(latest);
    } catch (cause) {
      if (generation === sessionGeneration && cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
        clearAuthentication(); window.dispatchEvent(new Event("nexa-session-expired"));
      }
      throw cause;
    }
    // A permission failure on the original resource does not invalidate a session.
    try { return await request<T>(path, init, refreshed.accessToken, core, binary); }
    catch (cause) {
      if (generation === sessionGeneration && cause instanceof ApiRequestError && cause.status === 401) {
        clearAuthentication(); window.dispatchEvent(new Event("nexa-session-expired"));
      }
      throw cause;
    }
  }
}

function refreshOnce(authentication: StoredAuthentication): Promise<StoredAuthentication> {
  if (!refreshInFlight) {
    const pending = rotateRefreshToken(authentication).finally(() => { if (refreshInFlight === pending) refreshInFlight = null; });
    refreshInFlight = pending;
  }
  return refreshInFlight;
}

export function authenticatedCoreRequest<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  return authorized<T>(path, accessToken, init, true);
}

function readStoredAuthentication(): StoredAuthentication | null {
  try {
    const value = window.sessionStorage.getItem(AUTH_STORAGE_KEY);
    const parsed = value ? JSON.parse(value) : null;
    return parsed && typeof parsed.accessToken === "string" && typeof parsed.refreshToken === "string" && typeof parsed.user?.id === "string" ? parsed as StoredAuthentication : null;
  } catch (_) {
    return null;
  }
}

function saveAuthentication(authentication: StoredAuthentication) {
  window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authentication));
}

function clearAuthentication() {
  sessionGeneration++;
  refreshInFlight = null;
  window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
}

async function profileFor(authentication: StoredAuthentication): Promise<AuthSession> {
  const profile = await request<CustomerProfile>("/me", {}, authentication.accessToken);
  return { ...authentication, profile };
}

async function authenticate(path: "/auth/login" | "/auth/register", payload: object): Promise<AuthSession> {
  const generation = ++sessionGeneration;
  refreshInFlight = null;
  const authentication = await request<AuthenticationResponse>(path, {
    method: "POST",
    body: JSON.stringify(payload)
  });
  if (generation !== sessionGeneration) throw new ApiRequestError(401, "This sign-in was cancelled. Please sign in again.");
  saveAuthentication(authentication);
  try {
    const session = await profileFor(authentication);
    if (generation !== sessionGeneration) throw new ApiRequestError(401, "This sign-in was cancelled. Please sign in again.");
    return session;
  } catch (error) {
    if (generation === sessionGeneration) clearAuthentication();
    throw error;
  }
}

async function rotateRefreshToken(authentication: StoredAuthentication): Promise<StoredAuthentication> {
  const generation = sessionGeneration;
  const refreshed = await request<AuthenticationResponse>("/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken: authentication.refreshToken })
  });
  if (generation !== sessionGeneration) throw new ApiRequestError(401, "Your session has ended. Please sign in again.");
  saveAuthentication(refreshed);
  return refreshed;
}

export function login(email: string, password: string): Promise<AuthSession> {
  return authenticate("/auth/login", { email, password });
}

export function register(fullName: string, email: string, password: string, phoneNumber: string): Promise<AuthSession> {
  return authenticate("/auth/register", {
    fullName,
    email,
    password,
    phoneNumber: phoneNumber.trim() || null
  });
}

export function updateProfile(accessToken: string, fullName: string, phoneNumber: string, address: string): Promise<CustomerProfile> {
  return authenticatedRequest<CustomerProfile>("/me", accessToken, {
    method: "PATCH",
    body: JSON.stringify({
      fullName,
      address: address.trim() || null,
      phoneNumber: phoneNumber.trim() || null
    })
  });
}

export async function restoreSession(): Promise<AuthSession | null> {
  const authentication = readStoredAuthentication();
  if (!authentication) return null;

  try {
    return await profileFor(authentication);
  } catch (error) {
    if (!(error instanceof ApiRequestError) || error.status !== 401) {
      throw error;
    }
  }

  try {
    return await profileFor(await refreshOnce(authentication));
  } catch (error) {
    if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) { clearAuthentication(); return null; }
    throw error;
  }
}

export async function logout(): Promise<void> {
  const authentication = readStoredAuthentication();
  clearAuthentication();
  if (authentication) await request<void>("/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken: authentication.refreshToken }) });
}
