# Local chat model

Nexa's local Spring profile is configured to use Ollama at `http://localhost:11434` with the model name `qwen3.5:4b`. Configuration does not install Ollama or download the model. Each developer must set up their local model separately. No database migration or cloud API key is needed for this integration.

## First-time setup on Windows

Install and start [Ollama for Windows](https://docs.ollama.com/windows), then open a new PowerShell terminal. From any folder, run:

```powershell
ollama --version
ollama pull qwen3.5:4b
ollama run qwen3.5:4b "Reply with OK."
```

The model must finish downloading before the smoke prompt can run. These are [Ollama CLI commands](https://docs.ollama.com/cli); the model tag is listed in the [official model library](https://ollama.com/library/qwen3.5:4b). If the CLI cannot connect, start the Ollama Windows app, or run `ollama serve` in another terminal. Do not start a second server if one is already listening on port 11434.

In the ignored `apps/api/.env`, use properties syntax (an equals sign after the variable name):

```properties
NEXA_AI_ENABLED=true
NEXA_AI_BASE_URL=http://localhost:11434
NEXA_AI_MODEL=qwen3.5:4b
```

Restart Nexa's API after changing these settings and keep Ollama running. The interactive model terminal is not required: Nexa calls Ollama's HTTP API. Downloaded models are managed by Ollama outside this repository and are not part of a Git pull.

To run without the model for now, set `NEXA_AI_ENABLED=false` and restart the API. Core banking and basic chat routing remain available; model-assisted interpretation is disabled.

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

Vector/document retrieval is not enabled. A suitable embedding model can be integrated separately when FAQs and policy documents are available.

## Testing

`OllamaInterpreterTest` uses a loopback stub to test request format, validation, fallback, domain routing and action isolation. Maven disables model routing in ordinary regression tests so they do not depend on Ollama. Set `NEXA_OLLAMA_SMOKE=true` to additionally run the opt-in test against local `qwen3.5:4b` with synthetic messages; the explicit smoke-test instance still runs when opted in.

Historical verification reported 89 backend tests passing (the opt-in live test skipped), a separate live Qwen test for Hinglish, a savings-account follow-up and a negated transfer, and 46 frontend tests plus typecheck, lint and build. Those earlier results do not establish that Ollama or this model is installed on the current developer's computer. No live model check was performed while adding these setup instructions; the current broader application checks are recorded in ACCOUNT_OPENING_UPGRADE.md.
