# Nexa Knowledge Base

The authenticated conversation endpoint now checks a read-only knowledge boundary before it calls a banking workflow or the existing intent interpreter. It answers with reviewed content directly, without asking a language model to invent or paraphrase product facts. Existing conversations and receipts retain their storage format; no database migration is needed.

## Request separation

`KnowledgeRouter` classifies education, Nexa product information, navigation/help, account data, transactions, administrator requests and unknown messages. Personal reads continue through authenticated banking services. Supported actions continue through the existing saved-review and explicit-confirmation workflows. An informational question during a pending review cannot consume a selection or confirm that review. Structured confirmation commands still reach the workflow service directly after conversation ownership checks.

Requests needing screen workflows (account opening, adding payees, loan payoff or partial prepayment) receive guidance and never report execution. Scheduled payments currently have a viewing screen; creation and automatic recurring execution were not verified in the implementation. The KB does not promise them. This layer does not add new money-moving capabilities.

Administrator content is filtered by the authenticated `ROLE_ADMIN` authority, never by a user's assertion in chat. Customer chat receives an access explanation. Staff are directed to the administrator workspace for operations; the KB itself cannot execute them.

## Content and retrieval

Edit `apps/api/src/main/resources/knowledge/nexa.json` to maintain the reviewed catalogue. Entries have a stable ID, topic, aspect, scope (`NEXA` or `GENERAL`), version, effective dates, active flag, audience, search aliases, answer, optional Hindi/Hinglish answers and source. `effectiveUntil` is exclusive. Restart/redeploy the API to load changes.

Use a new version for changed content. The newest effective active version wins within its scope, with Nexa content ahead of general material. Retired, future and expired entries are excluded. Product names take precedence over generic words such as “interest rate”. Ambiguous or missing matches return clarification or an unavailable response. Retrieval is a deterministic alias matcher, not semantic vector search; add reviewed aliases and regression examples when extending supported phrasing.

Product terms use separate aspects: `RATE`, `FEES`, `LIMITS`, `REQUIREMENTS`, and `DOCUMENTS`. An overview is never treated as evidence for a missing term. Nexa-specific questions cannot fall back to general banking facts. Priority is reviewed Nexa content, then supported product configuration, then general concepts where appropriate. Missing fees, eligibility rules, savings rates and other terms are explicitly unavailable.

Loan rates come from the same `LoanCalculationService` configuration as the calculator. Amount and tenure limits share its constants. Explicit quotes with an INR amount and tenure in months use the real calculation service; ambiguous inputs ask for clarification. Existing-loan balances, accepted rates and repayment/prepayment comparisons must use authenticated records and the loan screens.

## Context, language and provenance

Knowledge answers retain `knowledgeTopic`, entry ID, version, source and language in the existing response metadata. Only the immediately preceding turn in the owned conversation supplies a KB topic. Rate/document follow-ups inherit it; explicit topic changes replace it, and data/action turns clear it. Voice messages persist the safe essence and metadata, so follow-ups do not require a retained transcript.

English is the catalogue fallback. Curated Hindi and Hinglish variants are returned when present; missing terms also have localized responses. No runtime translation model is allowed to introduce unsupported facts. More translations and aliases can be added without changing the routing boundary. The web client shows the answer as ordinary chat text without an empty banking-summary panel.

## Simulation boundaries

Internal Nexa transfers, accounts, loan payments, administrator adjustments and ledger records can persist banking state. Bill-payment outcomes must be read from the saved result: an internal posting and a simulated request are distinct, and neither establishes external provider settlement. Card-network changes and external bill/direct-debit/transfer actions remain demonstrations. A saved receipt is not proof of external funds movement.

## Validation

`KnowledgeRouterTest` covers information/data/action separation, missing terms, configuration-derived rates and quotes, follow-up context, language selection, staff restrictions and effective versions. `ChatFirstIntegrationTest` exercises the complete authenticated endpoint, including knowledge questions during a pending transfer and subsequent explicit confirmation. Existing chat workflow regression tests remain applicable.

Run the API's Maven verification and the web client's type check, lint and test commands. The API must be restarted before an already-running instance serves the new catalogue.
