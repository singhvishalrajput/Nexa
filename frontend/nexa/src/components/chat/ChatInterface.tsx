import { h } from "preact";
import { useRef, useState } from "preact/hooks";

type ChatInterfaceProps = { onClose: () => void };
type Message = { role: "user" | "assistant"; text: string };
const suggestions = ["What can I safely spend this week?", "Show subscriptions I rarely use", "Move ₹5,000 to savings"];

export function ChatInterface({ onClose }: ChatInterfaceProps) {
  const [input, setInput] = useState("");
  const [listening, setListening] = useState(false);
  const recognitionRef = useRef<any>(null);
  const [messages, setMessages] = useState<Message[]>([{ role: "assistant", text: "Good evening, Vishal. What would you like to do with your money today?" }]);

  const submit = (value?: string) => {
    const next = (value || input).trim();
    if (!next) return;
    setMessages((current) => [...current, { role: "user", text: next }, { role: "assistant", text: "I’ve understood the request. Before anything changes, I’ll show you a clear review with the account, amount, and outcome." }]);
    setInput("");
  };

  const toggleVoice = () => {
    if (listening) {
      recognitionRef.current?.stop();
      setListening(false);
      return;
    }

    const browserWindow = window as any;
    const SpeechRecognition = browserWindow.SpeechRecognition || browserWindow.webkitSpeechRecognition;
    if (!SpeechRecognition) return;

    const recognition = new SpeechRecognition();
    recognition.lang = "en-IN";
    recognition.interimResults = true;
    recognition.continuous = false;
    recognition.onresult = (event: any) => {
      const transcript = Array.from(event.results).map((result: any) => result[0].transcript).join("");
      setInput(transcript);
    };
    recognition.onend = () => setListening(false);
    recognition.onerror = () => setListening(false);
    recognitionRef.current = recognition;
    recognition.start();
    setListening(true);
  };

  return (
    <div class="nexa-chat-shell">
      <main class="nexa-chat-main">
        <header class="nexa-chat-header"><button class="nexa-chat-brand" type="button" onClick={onClose} aria-label="Back to Nexa home">Nexa</button><span class="nexa-chat-presence"><i /> Private session</span><button class="nexa-chat-close" type="button" onClick={onClose}>Close <span>×</span></button></header>
        <section class="nexa-conversation" aria-live="polite">
          <div class="nexa-conversation-intro"><h2>How can I help?</h2><p>Ask naturally. You’ll review every action before anything changes.</p></div>
          <div class="nexa-message-list">{messages.map((message, index) => <div class={`nexa-message is-${message.role}`} key={`${message.role}-${index}`}><span>{message.role === "assistant" ? "N" : "You"}</span><p>{message.text}</p></div>)}</div>
          {messages.length === 1 && <div class="nexa-chat-suggestions">{suggestions.map((suggestion) => <button type="button" onClick={() => submit(suggestion)}>{suggestion}<span>↗</span></button>)}</div>}
        </section>
        <form class="nexa-chat-composer" onSubmit={(event) => { event.preventDefault(); submit(); }}>
          <label for="nexa-chat-input">{listening ? "Listening…" : "Ask Nexa"}</label>
          <div><textarea id="nexa-chat-input" rows={1} value={input} onInput={(event) => setInput((event.currentTarget as HTMLTextAreaElement).value)} placeholder={listening ? "Speak now…" : "Type or speak a request…"} /><button class={`nexa-voice-button ${listening ? "is-listening" : ""}`} type="button" onClick={toggleVoice} aria-label={listening ? "Stop listening" : "Speak to Nexa"}><svg aria-hidden="true" viewBox="0 0 24 24"><rect x="8" y="3" width="8" height="12" rx="4" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M5.5 11.5v.5a6.5 6.5 0 0 0 13 0v-.5M12 18.5V22M9 22h6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg></button><button class="nexa-send-button" type="submit" aria-label="Send message">↑</button></div>
          <span>{listening ? "Voice input is active" : "Nothing happens without your approval"}</span>
        </form>
      </main>
    </div>
  );
}
