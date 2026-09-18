export type Recognition = {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult:
    | ((event: {
        results: ArrayLike<{ 0: { transcript: string }; isFinal: boolean }>;
      }) => void)
    | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
};

// Browsers can end even continuous recognition during silence. Keep the user's
// listening session alive, while allowing Stop and cleanup to cancel retries.
export function createVoiceSession(
  RecognitionClass: new () => Recognition,
  seed: string,
  callbacks: {
    transcript(text: string): void;
    listening(active: boolean): void;
    error(code: string): void;
  },
) {
  let active = false;
  let current: Recognition | null = null;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let committed = seed.trim();
  const join = (a: string, b: string) => [a, b].filter(Boolean).join(" ");
  const cancel = () => {
    active = false;
    clearTimeout(retry);
    if (current) {
      current.onresult = current.onerror = current.onend = null;
      current.abort();
      current = null;
    }
  };
  const begin = () => {
    if (!active) return;
    const r = new RecognitionClass();
    current = r;
    let segment = "";
    r.lang = "en-IN";
    r.continuous = true;
    r.interimResults = true;
    r.onresult = (event) => {
      if (current !== r) return;
      // Each event contains the current session's complete results. Replacing
      // the segment avoids repeating interim words or already-final phrases.
      segment = Array.from(event.results)
        .map((result) => result[0].transcript.trim())
        .filter(Boolean)
        .join(" ");
      callbacks.transcript(join(committed, segment));
    };
    r.onerror = ({ error }) => {
      if (current !== r || error === "no-speech") return;
      cancel();
      callbacks.listening(false);
      callbacks.error(error);
    };
    r.onend = () => {
      if (current !== r) return;
      committed = join(committed, segment);
      current = null;
      if (active) retry = setTimeout(begin, 300);
    };
    try {
      r.start();
    } catch {
      cancel();
      callbacks.listening(false);
      callbacks.error("start-failed");
    }
  };
  return {
    start() {
      if (active) return;
      active = true;
      callbacks.listening(true);
      begin();
    },
    stop() {
      active = false;
      clearTimeout(retry);
      callbacks.listening(false);
      // Let the browser deliver its last result; never restart after Stop.
      current?.stop();
    },
    cancel,
  };
}
