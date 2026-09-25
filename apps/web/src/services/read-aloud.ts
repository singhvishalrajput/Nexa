import type { Locale } from "./locale";

export const speechLocale = (text: string, selected: Locale): Locale =>
  selected === "hi-IN" || /[\u0900-\u097f]/.test(text) ? "hi-IN" : "en-IN";

export function selectSpeechVoice(voices: SpeechSynthesisVoice[], locale: Locale): SpeechSynthesisVoice | undefined {
  const tag = (voice: SpeechSynthesisVoice) => voice.lang.replace(/_/g, "-").toLowerCase();
  const exact = voices.filter(voice => tag(voice) === locale.toLowerCase());
  const matches = exact.length ? exact : voices.filter(voice => tag(voice).split("-")[0] === locale.slice(0, 2));
  return matches.find(voice => voice.localService) || matches[0];
}

const unavailable = (locale: Locale) => locale === "hi-IN"
  ? "इस ब्राउज़र में हिन्दी आवाज़ उपलब्ध नहीं है। डिवाइस की स्पीच सेटिंग में हिन्दी आवाज़ जोड़ें या हिन्दी आवाज़ वाला ब्राउज़र इस्तेमाल करें, फिर ‘पिछला जवाब सुनाएँ’ चुनें।"
  : "An English voice is not available in this browser. Enable an English voice in your device’s speech settings, then choose Read last reply.";
const failed = (locale: Locale) => locale === "hi-IN"
  ? "जवाब सुनाया नहीं जा सका। ‘पिछला जवाब सुनाएँ’ चुनकर फिर कोशिश करें। आप जवाब स्क्रीन पर पढ़ सकते हैं।"
  : "The reply could not be read aloud. Choose Read last reply to try again, or read it on screen.";

/** Never let the browser silently substitute an English voice for Hindi. */
export function createSpeechReader(
  onError: (message: string) => void,
  synthesis: SpeechSynthesis | undefined = window.speechSynthesis,
  makeUtterance: (text: string) => SpeechSynthesisUtterance = text => new SpeechSynthesisUtterance(text)
) {
  let generation = 0;
  let clearWaiting: (() => void) | undefined;
  // Retain the utterance until completion (some browsers otherwise lose its events).
  let current: SpeechSynthesisUtterance | undefined;
  const cancel = () => {
    generation++;
    clearWaiting?.();
    clearWaiting = undefined;
    current = undefined;
    synthesis?.cancel();
  };
  const speak = (text: string, locale: Locale) => {
    cancel();
    onError("");
    if (!text.trim()) return;
    if (!synthesis) { onError(unavailable(locale)); return; }
    const epoch = generation;
    const trySpeaking = () => {
      if (generation !== epoch) return true;
      try {
        const voice = selectSpeechVoice(synthesis.getVoices(), locale);
        if (!voice) return false;
        clearWaiting?.();
        clearWaiting = undefined;
        const utterance = makeUtterance(text);
        utterance.lang = voice.lang.replace(/_/g, "-");
        utterance.voice = voice;
        current = utterance;
        utterance.onend = () => { if (current === utterance) current = undefined; };
        utterance.onerror = event => {
          if (current !== utterance || generation !== epoch) return;
          current = undefined;
          if (event.error !== "canceled" && event.error !== "interrupted") {
            onError(event.error === "language-unavailable" || event.error === "voice-unavailable" ? unavailable(locale) : failed(locale));
          }
        };
        synthesis.speak(utterance);
      } catch {
        clearWaiting?.();
        clearWaiting = undefined;
        current = undefined;
        onError(failed(locale));
      }
      return true;
    };
    if (trySpeaking()) return;
    // Voice lists can initially be empty or contain only the default English voice.
    const voicesChanged = () => { trySpeaking(); };
    const timer = window.setTimeout(() => {
      clearWaiting?.();
      clearWaiting = undefined;
      if (generation === epoch && !trySpeaking()) onError(unavailable(locale));
    }, 2000);
    clearWaiting = () => {
      window.clearTimeout(timer);
      synthesis.removeEventListener("voiceschanged", voicesChanged);
    };
    synthesis.addEventListener("voiceschanged", voicesChanged);
    trySpeaking();
  };
  return { speak, cancel };
}
