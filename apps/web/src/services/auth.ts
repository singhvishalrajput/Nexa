const configuredApiUrl = (window as Window & { NEXA_API_BASE_URL?: string }).NEXA_API_BASE_URL;
const API_BASE_URL = configuredApiUrl || "http://localhost:8081/api/v1";
const AUTH_STORAGE_KEY = "nexa-auth-session";

export type AuthUser = {
  id: string;
  email: string;
  role: "CUSTOMER" | "ADMIN";
};

export type CustomerProfile = {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  status: string;
  role: "CUSTOMER" | "ADMIN";
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
  fieldErrors?: Array<{ field: string; message: string }>;
};

export class ApiRequestError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "ApiRequestError";
  }
}

async function request<T>(path: string, init: RequestInit = {}, accessToken?: string): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  } catch (_) {
    throw new ApiRequestError(0, "Cannot reach the Nexa API. Make sure the backend is running on port 8081.");
  }

  if (!response.ok) {
    let body: ApiErrorBody = {};
    try {
      body = await response.json();
    } catch (_) {}
    const fieldMessage = body.fieldErrors?.map((error) => error.message).join(" ");
    throw new ApiRequestError(response.status, fieldMessage || body.detail || `Request failed (${response.status}).`);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function authenticatedRequest<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  return request<T>(path, init, accessToken);
}

function readStoredAuthentication(): StoredAuthentication | null {
  try {
    const value = window.sessionStorage.getItem(AUTH_STORAGE_KEY);
    return value ? JSON.parse(value) as StoredAuthentication : null;
  } catch (_) {
    return null;
  }
}

function saveAuthentication(authentication: StoredAuthentication) {
  window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authentication));
}

function clearAuthentication() {
  window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
}

async function profileFor(authentication: StoredAuthentication): Promise<AuthSession> {
  const profile = await request<CustomerProfile>("/me", {}, authentication.accessToken);
  return { ...authentication, profile };
}

async function authenticate(path: "/auth/login" | "/auth/register", payload: object): Promise<AuthSession> {
  const authentication = await request<AuthenticationResponse>(path, {
    method: "POST",
    body: JSON.stringify(payload)
  });
  saveAuthentication(authentication);
  try {
    return await profileFor(authentication);
  } catch (error) {
    clearAuthentication();
    throw error;
  }
}

async function rotateRefreshToken(authentication: StoredAuthentication): Promise<StoredAuthentication> {
  const refreshed = await request<AuthenticationResponse>("/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken: authentication.refreshToken })
  });
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

export function updateProfile(accessToken: string, fullName: string, phoneNumber: string): Promise<CustomerProfile> {
  return authenticatedRequest<CustomerProfile>("/me", accessToken, {
    method: "PATCH",
    body: JSON.stringify({
      fullName,
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
      clearAuthentication();
      return null;
    }
  }

  try {
    return await profileFor(await rotateRefreshToken(authentication));
  } catch (_) {
    clearAuthentication();
    return null;
  }
}

export async function logout(): Promise<void> {
  const authentication = readStoredAuthentication();
  try {
    if (authentication) {
      await request<void>("/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken: authentication.refreshToken })
      });
    }
  } finally {
    clearAuthentication();
  }
}
