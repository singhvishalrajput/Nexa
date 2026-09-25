import { useEffect, useRef, useState } from "preact/hooks";
import type { Locale } from "../services/locale";
import type { Turn } from "../services/conversations";
import { createSpeechReader, speechLocale } from "../services/read-aloud";
import { spokenResponse } from "../services/spoken-response";
import { replyForSpeech } from "../services/reply-localization";

export function useReadAloud(enabled: boolean, language: Locale, paused = false) {
  const [error, setError] = useState("");
  const preferences = useRef({ enabled, language, paused });
  preferences.current = { enabled, language, paused };
  const reader = useRef<ReturnType<typeof createSpeechReader> | null>(null);
  if (!reader.current) reader.current = createSpeechReader(setError);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; reader.current?.cancel(); }; }, []);
  useEffect(() => { reader.current?.cancel(); setError(""); }, [language, paused]);
  useEffect(() => { if (!enabled) reader.current?.cancel(); }, [enabled]);

  // Pending chat requests must use the latest language/toggle, not their old render.
  const speak = (value: string | Turn, force = false) => {
    const latest = preferences.current;
    if (!mounted.current || latest.paused || (!latest.enabled && !force)) return;
    const locale = speechLocale(typeof value === "string" ? value : value.assistantText, latest.language);
    reader.current?.speak(typeof value === "string" ? replyForSpeech(value, locale) : spokenResponse(value, locale), locale);
  };
  const cancel = () => reader.current?.cancel();
  return { speak, cancel, error, clearError: () => setError("") };
}
