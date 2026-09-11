# Conversation storage and interaction

> For the merged banking core, current setup and schema cutover, see [Banking integration](BANKING_INTEGRATION.md). Pre-merge implementation and activation notes below are historical.

Authenticated chat now uses `/api/v1/conversations`. Apply Flyway migration V7 by restarting the API before using the new web build.

Each conversation belongs to the signed-in customer. Each immutable turn stores the input source, interpreted intent, user text/voice essence, assistant reply, server timestamp and retry key. A turn is an exchange, not a payment record. Balance and transaction answers use the existing account services and their ownership checks. Financial mutations are not implemented in the conversational endpoint.

Voice recognition happens through the browser speech service. Nexa receives only final text, interprets it temporarily and stores a canonical essence instead of that transcript. There is no audio upload, recording field or audio storage. Unknown speech stores only “Unclear request; clarification needed.” The initial interpreter supports English balance/history/help requests and simple INR transfer requests; it is rule-based, not a general language model. Conditions, negations and unsupported wording trigger clarification. Browser/provider handling of audio is outside Nexa's storage guarantee, and is disclosed beside the microphone.

Typed messages retain their text. No new history is written to browser localStorage. A failed request is held in memory for an explicit retry using the same client ID, and is released on success, discard or leaving the screen. Existing legacy localStorage histories are not imported: they cannot reliably distinguish voice from typed messages. They remain on devices from earlier app versions; this change does not retroactively sanitize or delete them.

History fetches 30 conversation headers at a time, and 30 exchanges at a time for only the selected conversation. Message pagination uses the indexed sequence cursor. Writes append one exchange, rather than rewriting full conversation JSON. A unique conversation/client key plus a conversation row lock prevents concurrent retries from creating duplicates. A retry returns the original saved result. Deleting a conversation cascades to its turns, after the customer says “delete conversation” and “confirm delete”. Database backups follow deployment backup policies, not this API deletion.

The default banking screen is now a conversation, without action cards leading into simulated banking forms. Type or speak banking requests. “Read aloud”, “stop reading”, “repeat”, “new conversation”, “show history”, “open conversation 1”, “earlier messages”, “more conversations”, “delete conversation”, “confirm delete”, “cancel” and “sign out” control the experience. Microphone, send and history controls remain keyboard accessible. Microphone activation is explicit; stop to review recognized text, then send or cancel. English and Hindi speech capture are selectable; the banking interpreter remains English-only. Spoken replies are opt-in. Existing prototype workspaces remain in source but are not linked from this screen.

## Deployment considerations and remaining work

- Run the API tests and web build, then apply V7 against the configured Oracle database. Unit/controller tests do not replace an Oracle migration smoke test.
- Do not enable request-body logging or transcription logging in gateways, telemetry or error monitoring. Application code does not log those values.
- Configure deployment encryption, access policies and a retention period before production. Automatic expiry and backup erasure are not implemented here.
- Before enabling money movement, implement authenticated server-side draft state, recipient resolution, amount/currency validation, explicit confirmation tied to that draft, expiry, idempotent execution and authoritative transaction receipts. A saved chat intent is never authorization to move funds.
- A broader multilingual conversational interpreter should produce validated intent/entities and ask clarifying questions when uncertain. Do not silently convert ambiguous speech into a financial action.
