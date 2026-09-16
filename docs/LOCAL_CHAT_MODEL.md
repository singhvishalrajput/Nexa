# Local chat model

Nexa's local Spring profile now enables Ollama at `http://localhost:11434`, using the installed `qwen3.5:4b` model. Start/restart the API to load the integration. No database migration or cloud API key is needed for this change.

## Configuration

The local profile accepts these environment variables:

| Variable | Default |
| --- | --- |
| `NEXA_AI_ENABLED` | `true` |
| `NEXA_AI_BASE_URL` | `http://localhost:11434` |
| `NEXA_AI_MODEL` | `qwen3.5:4b` |
| `NEXA_AI_TIMEOUT_SECONDS` | `45` |

Chat turn requests allow 65 seconds, while other requests retain their 20-second timeout. The model timeout is capped at 60 seconds. Cold model loading may exceed the budget; warm the model with `ollama run qwen3.5:4b` before using chat. The adapter requests a ten-minute keep-alive, disables thinking, and uses a 4096-token context. Non-local deployments can configure the equivalent `nexa.ai.*` Spring properties explicitly. Set `NEXA_AI_ENABLED=false` to restore basic interpretation in the local profile.

## Behavior

Clear, self-contained requests such as `balance`, `show my savings account balance`, `recent transactions`, `my bills`, and `my cards` take a deterministic fast path straight to the banking services. They do not call Ollama or load conversation history. The matcher checks the entire request; dates, unfamiliar filters, negations, compound requests and contextual follow-ups continue to the model instead of having their extra words discarded.

The adapter asks Ollama for a constrained JSON routing plan, validates intent, filters, dates and explicitly supplied identifiers, then invokes the existing authenticated domain router. The router supplies authoritative data and the existing `BankingContent` UI cards. Model-generated account balances, receipts, SQL, HTML and execution commands are not accepted.

Four recent turns from the same owned conversation provide follow-up context. Existing voice-message retention and sensitive-message filtering still apply. Banking/card payloads are not sent; messages and assistant text are sent to the configured Ollama endpoint. This endpoint should remain local for local inference.

Existing explicit workflows and their action-bound confirmation buttons take precedence. Model-recognized transfer wording outside those workflows directs the user to Send money, where accounts, amount and recipient are reviewed and confirmed. The model cannot confirm, execute, or modify a pending transfer. Natural-language editing of existing transfer proposals is not added by this integration.

Unsupported or ambiguous requests ask for clarification. Unreachable Ollama, timeouts, malformed output and invalid identifiers fall back to basic interpretation, with a 30-second backoff. Logs identify the failure class without recording model prompts or responses.

Examples to try: `mere account mein kitne paise hain?`, followed by `only the savings one`; `show payments from yesterday`; `what did I spend this month?`, followed by `and last month?`.

Vector/document retrieval is not enabled. The installed embedding model can be integrated separately when FAQs and policy documents are available.

## Testing

`OllamaInterpreterTest` uses a loopback stub to test request format, validation, fallback, domain routing and action isolation. Maven disables model routing in ordinary regression tests so they do not depend on Ollama. Set `NEXA_OLLAMA_SMOKE=true` to additionally run the opt-in test against local `qwen3.5:4b` with synthetic messages; the explicit smoke-test instance still runs when opted in.

Verified: 89 backend tests pass (the opt-in live test is skipped in that regression run); the separate live Qwen test passes for Hinglish, a savings-account follow-up and a negated transfer. All 46 frontend tests, typecheck, lint and build pass. No live banking transaction was executed.
