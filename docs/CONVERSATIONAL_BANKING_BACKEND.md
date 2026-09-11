# Conversational banking backend foundation

> For the merged banking core, current setup and schema cutover, see [Banking integration](BANKING_INTEGRATION.md). Pre-merge implementation and activation notes below are historical.

## Architecture

Java 17 / Spring Boot 3.5, existing JWT authentication, Oracle, Flyway, JPA and
JdbcTemplate are retained. No application or ML dependencies were added.

Flow: ConversationInterpreter → IntentClassifier → EntityExtractor → DomainRouter
→ domain query/preparation service → versioned BankingContent → saved conversation.
The NLP package has no database dependency. REST and chat share domain services.
Existing authentication, conversation ownership, cursor history, immutable turns,
and retry/client IDs remain in place. Voice input stores a canonical intent and
relevant extracted meaning, never audio or a verbatim transcript.

Accounts and transactions use the existing core tables. Beneficiaries and transfer
status use the existing payment tables. Mandates, bills, cards, scheduled payments
and loans use typed records behind BankingProductRepository. Its JDBC implementation
reads banking_products, a bounded read-model store with relational user ownership
and a composite foreign key to the same user's source account. Display payloads
are JSON, not a new ledger or payment engine. Replace the repository adapter with
real bank feeds without changing controllers, routing or frontend response types.

## API routes

All routes below require authentication and begin with /api/v1. There is no userId
request parameter; ownership comes from CurrentUserProvider.

| Routes | Behaviour |
| --- | --- |
| GET /accounts, /accounts/{id}, /accounts/{id}/balance | Existing accounts, types, masked numbers, balances, currency, status; currentBalance aliases ledgerBalance |
| GET /transactions?accountId=… | Paginated transactions with category, from/to YYYY-MM-DD, direction DEBIT/CREDIT, literal search, page and size |
| GET /transactions/{id} | Owner-scoped detail, reference, payment method when recorded, debit/credit direction |
| GET /accounts/{id}/transactions | Existing endpoint remains compatible |
| GET /mandates, /mandates/{id} | Payee, cap, frequency, start/end, next debit, linked account, status, reference |
| GET /bills, /bills/{id}, /bills/{id}/payments | Discovery/detail/history, minimum where applicable, amount, due date and state |
| GET /cards, /cards/{id}, /cards/{id}/transactions | Credit/debit card information, masked numbers and recorded card transactions |
| GET /credit-cards, /credit-cards/{id}, /credit-cards/{id}/transactions | Credit-only card queries |
| GET /beneficiaries, /beneficiaries/{id} | Owned payees, bank, masked account and verification status |
| GET /scheduled-payments, /scheduled-payments/{id} | Payee, amount, source account, scheduled date, state and reference |
| GET /loans, /loans/{id}, /loans/{id}/payments | Loan type, outstanding, EMI, due date, interest rate and recorded payment history |
| GET /transfers/{id} | Recorded transfer state, never an inferred success |
| POST /actions/prepare | Validated preparation only |
| POST /conversations/{id}/turns | Existing persisted chat API; classify, extract, route and return structured data |

Product lists accept status, page (default 0) and size (default 30, maximum 100).
Credit-only views filter their underlying card page. Beneficiaries return up to
100 records. Product histories are small supplied snapshots in this initial adapter;
large feeds should add dedicated paginated history adapters. Chat previews show
up to 30 products or five account transactions. Dates in account transaction queries
use UTC day boundaries. The initial chat defaults to the first owned account/card
unless an explicit reference or supported account type is supplied.

## Chat contract

POST a turn using the existing clientId, source TEXT or VOICE, and text fields.
The returned Turn retains assistantText and banking for the current frontend and
adds response with type, message, data, errorCode and optional meta. The banking
payload's type remains the frontend renderer key, and version remains 1.

Example: response.type is MANDATE_LIST, response.message is “Here are your mandates.”,
and response.data contains version: 1, type: MANDATES, mandates: [...]. The same
payload is available as banking for backwards compatibility. The database stores
only one payload; the HTTP response exposes both shapes during this transition.

Envelope types: TEXT, ACCOUNT_LIST, TRANSACTION_LIST, TRANSACTION_DETAIL,
MANDATE_LIST, MANDATE_DETAIL, BILL_LIST, BILL_DETAIL, CREDIT_CARD_LIST, CARD_LIST,
CARD_TRANSACTION_LIST, BENEFICIARY_LIST, SCHEDULED_PAYMENT_LIST, LOAN_SUMMARY,
TRANSFER_STATUS, ACTION_REQUIRED. Failures retain the existing HTTP error shape
and status and additionally expose response.type ERROR, message and errorCode.

Renderer keys: ACCOUNTS, TRANSACTIONS, MANDATES, BILLS, CARDS, BENEFICIARIES,
SCHEDULED_PAYMENTS, LOANS, ACTION_REQUIRED and TRANSFER_STATUS. Text/clarifications
remain ordinary bubbles. Detail queries can return a one-item domain view. Monetary
snapshot values use decimal strings. Unknown future view types retain text fallback.
No full card numbers, CVVs or PINs exist in these response models. Identifiers are
masked again at the backend read-model boundary and in the frontend.

Errors distinguish invalid request (400), authentication (401), missing/other-owner
record (404), conflict (409), banking unavailable (503), and unexpected failure
(500). Low-confidence input returns UNKNOWN with LOW_CONFIDENCE_INTENT and a
clarification. Missing action references return ACTION_DETAILS_REQUIRED; missing
detail references return REFERENCE_REQUIRED. SQL details and stack traces are not
returned to clients.

## Basic NLP and replacement

IntentRepository holds examples for balances/accounts, recent transaction/detail,
mandates/detail, bills/detail, credit cards/all cards/card transactions, beneficiaries,
scheduled payments, loans/detail, transfer preparation/status, bill/card payment
preparation, mandate cancellation preparation, help and unknown.

BasicEmbeddingProvider creates normalized 2,048-dimensional hashed word vectors
with small synonym/stop-word rules. InMemoryVectorIndex embeds the examples once
and searches by cosine similarity, keeping the best score per intent.
BasicIntentClassifier rejects scores below NLP_INTENT_THRESHOLD (default 0.70)
and close competing matches (margin below 0.08). Negations, compound/conditional
requests and ambiguous “coming up” questions are deliberately conservative. This
is lexical matching, not a production language model or calibrated probability.

EntityExtractor extracts explicit domain IDs, simple INR amounts, payee wording,
account type, debit/credit, a quoted search term, explicit from/to dates, and last
month. It does not resolve arbitrary names into payment destinations. Ask about one
target at a time. Complex language, Hindi understanding and general temporal
reasoning remain unsupported. Lists/details are read-only regardless of similarity.

Spring's NlpConfiguration supplies ConditionalOnMissingBean defaults for
EmbeddingProvider, IntentRepository, VectorIndex, IntentClassifier and
EntityExtractor. Supply a model-backed bean of the same interface to replace a
provider; the index rebuilds from that provider at startup. No domain-service change
is needed. DomainRouter is an interface with BankingDomainRouter as its explicit
implementation.

NLP_DEBUG_METADATA defaults to false. When enabled, responses include intent and
confidence, and DEBUG logging for ConversationInterpreter emits intent, score and
response type. It deliberately omits the original message, voice transcript,
financial values and identifiers. Do not enable raw input logging for debugging.

## Examples

- “Show my recent transactions.”
- “What did I spend recently?”
- “Show transactions from last month.”
- “What mandates do I have?”
- “Mandate details mnd_demo_0.”
- “Which bills are due?”
- “Bill details bil_demo_0.”
- “What's my credit card outstanding?”
- “Show my credit card transactions.”
- “Show my cards.”
- “What payments are coming up?”
- “What's my savings balance?”
- “Show my beneficiaries.”
- “Show my loans.”
- “Loan details lon_demo_personal.”
- “Transfer status trf_demo_draft.”

## Financial actions

POST /actions/prepare accepts operation (START_TRANSFER, PAY_BILL, PAY_CARD,
CANCEL_MANDATE), accountId, targetId and optional decimal amount. Transfers need
an amount and an owned ACTIVE beneficiary ID. Bills/cards can default the amount
from their authoritative snapshot. Preparation checks source ownership/status,
target ownership/status, currency where supplied, payment ranges, positive amount,
precision and sufficient available funds. Mandate cancellation checks the linked
account and current cancellable state.

The result is PREPARED, confirmationRequired: true, executionAvailable: false.
This is a stateless review, not an authorization token. There is no confirmation or
execution endpoint. A future executor must revalidate current state, obtain explicit
confirmation, enforce idempotency and record verified execution before reporting
success. No balances, bill statuses, mandate states or transfer records are mutated
by preparation or NLP. Real payment rails remain intentionally unconnected.

## Development data and activation

V9 adds the owned read-model table, beneficiary bank name and optional transaction
payment method. Local-only V10 adds a second zero-balance account with its ledger
account, two fictional payees, five mandate statuses, five bill statuses, a credit
card plus debit card, card activity, two scheduled payments, a loan with EMI history,
and a DRAFT transfer. All link to the existing demo user's account. Product due dates
are relative to migration execution, so an old demo database can have aged dates.
These are fictional persisted records, not hardcoded controller responses.

Use the existing local profile and configured NEXA_DB_PASSWORD to restart the API.
Flyway applies V7/V8 if pending, then V9 and local V10. Production profiles do not
load the local seed location. Do not edit previously applied migrations.

## Verification and limits

Backend tests cover intent variants and ambiguity, entity extraction, provider and
threshold replacement, user scoping, paging and validation, status handling,
transaction predicates, action preparation, REST contracts, chat envelopes, auth
protection, and sanitized failures. Existing authentication/account/conversation tests
remain included. Frontend type checks and regression tests cover renderer selection,
amount formatting, masking and legacy compatibility.

A standalone Google Java Format tool formats the changed Java files. No lint task is
configured in the repository. Compiler checks, formatter checks and git diff --check
are used instead. Oracle JET builds use isolated staging to avoid the running preview's
locked theme files.

Live Oracle migration verification is pending because this tool session has no
NEXA_DB_PASSWORD. Test-harness endpoint verification does not prove the migrations
have run on the user's database. Real payment rails, live bank discovery, large-scale
history adapters, full conversational stateful payment authorization and a trained
embedding/model provider remain future integrations.

Final local verification: 58 backend tests passed with zero failures/errors; Maven package produced target/nexa-api-0.0.1-SNAPSHOT.jar. Nine frontend tests, TypeScript compilation, isolated Oracle JET build, Google Java Format checks and git diff --check passed. No live Oracle migration run was performed.

## Live activation — 10 September 2026

Connected successfully to local NEXA_APP and verified existing migrations V1–V8. Flyway applied V9 and local V10 successfully. The updated API is running on port 8081 with health UP. Verified authenticated domain endpoints: 2 accounts, 5 mandates, 5 bills, 2 cards (1 credit), 2 beneficiaries, 2 scheduled payments, 1 loan, and 2 existing account transactions. Live conversational checks returned MANDATE_LIST, BILL_LIST, CARD_TRANSACTION_LIST, LOAN_SUMMARY, BENEFICIARY_LIST and SCHEDULED_PAYMENT_LIST. The temporary verification conversation and temporary API server were removed. Earlier migration-pending notes above are superseded by this successful activation. Database credentials were supplied only through the server process environment, not committed to source.
