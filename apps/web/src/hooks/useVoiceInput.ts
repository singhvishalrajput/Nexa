import { useEffect, useRef, useState } from "preact/hooks";

export type VoicePhase = "idle" | "listening" | "stopping" | "review";

/** Speech text is held in memory only; no MediaRecorder or audio upload. */
export function useVoiceInput(onError: (message: string) => void) {
  const [phase, setPhase] = useState<VoicePhase>("idle");
  const [seconds, setSeconds] = useState(0);
  const [transcript, setTranscript] = useState("");
  const recognition = useRef<any>(null);
  const generation = useRef(0);

  const cancel = () => {
    generation.current += 1;
    recognition.current?.abort();
    recognition.current = null;
    setTranscript("");
    setSeconds(0);
    setPhase("idle");
  };

  useEffect(() => {
    if (phase !== "listening") return;
    const began = Date.now();
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - began) / 1000)), 250);
    return () => window.clearInterval(timer);
  }, [phase]);

  useEffect(() => () => {
    generation.current += 1;
    recognition.current?.abort();
  }, []);

  const start = (language: "en-IN" | "hi-IN") => {
    const Constructor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Constructor) {
      onError("Voice input is unavailable in this browser. You can still type your message.");
      return;
    }
    cancel();
    window.speechSynthesis?.cancel();
    const run = generation.current;
    const recognizer = new Constructor();
    recognition.current = recognizer;
    recognizer.lang = language;
    recognizer.continuous = true;
    recognizer.interimResults = false;
    let finalText = "";
    let failed = false;
    recognizer.onresult = (event: any) => {
      if (run !== generation.current) return;
      finalText = Array.from(event.results).filter((result: any) => result.isFinal)
        .map((result: any) => result[0].transcript).join(" ").trim();
      setTranscript(finalText);
    };
    recognizer.onerror = (event: any) => {
      if (run !== generation.current) return;
      failed = true;
      setTranscript("");
      setPhase("idle");
      onError(event.error === "not-allowed"
        ? "Microphone access is off. Allow it in your browser to speak, or type your message."
        : "I couldn’t hear that clearly. Please try again or type your message.");
    };
    recognizer.onend = () => {
      if (run !== generation.current) return;
      recognition.current = null;
      if (failed) return;
      if (finalText && finalText.length <= 2000) setPhase("review");
      else {
        setPhase("idle"); setTranscript("");
        onError(finalText ? "That voice message is too long. Please try a shorter message." : "No speech was captured. Please try again.");
      }
    };
    try { recognizer.start(); setPhase("listening"); }
    catch (_) { cancel(); onError("The microphone couldn’t start. Please try again or type your message."); }
  };

  const stop = () => {
    if (!recognition.current) return;
    setPhase("stopping");
    recognition.current.stop();
  };

  return { phase, seconds, transcript, start, stop, cancel, editTranscript: setTranscript };
}
