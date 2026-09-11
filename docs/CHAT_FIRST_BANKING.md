# Chat-first banking

The existing JET/Preact and Spring Boot application now lands in persistent chat. Accounts, transactions, payments, products, profile/security and administrator screens remain supporting routes. Existing authentication, domain queries, transaction posting, journals and ledger entries are reused.

## Architecture

ConversationWorkspace → authenticated Conversation API → ConversationService → WorkflowService / ConversationInterpreter → banking domain services.

ConversationService owns conversation authorization, row locking, retry keys and immutable turn history. WorkflowService persists multi-turn selections and proposals separately from banking records. CustomerTransferService checks customer ownership before calling the existing core transaction service. SpendingQueryService aggregates authoritative transactions rather than a page of chat history. The existing local intent classifier is retained; no LLM receives database access or decides authorization.

The original turn API remains compatible. Turns can now contain a versioned `workflow` with action ID, operation, status, missing field, structured choices, source/target labels, decimal amount, currency, expiry, confirmation requirement, execution availability and transaction reference. States are COLLECTING, REVIEW, COMPLETED, CANCELLED, EXPIRED and UNAVAILABLE.

`POST /api/v1/conversations/{conversationId}/actions/{actionId}` accepts a client UUID, a type of SELECT/CONFIRM/CANCEL, and an optional selection value. Confirmation accepts no source, destination or amount overrides. Plain text such as “yes” redisplays the proposal; only the explicit action-bound confirmation command can execute it.

## Capabilities and UX

| Capability | Behavior |
| --- | --- |
| Balances, accounts, transactions/details | Existing ownership-scoped domain services and rich banking views |
| Spending this/last month, including food | Complete server aggregate, grouped by category/currency; excludes own-account transfers and includes withdrawals |
| Salary questions | Actual incoming payments for the first account and requested/current month; asks users to identify the sender because payroll verification is unavailable |
| Safely transferable amount | Actual balances, explicitly distinguished from affordability or a budget forecast |
| Bills/cards/mandates/loans/payees/scheduled payments | Existing domain queries and supporting surfaces; bills support due-period filtering |
| Own-account transfer | Destination → source → amount → review → explicit confirmation → real ledger posting and reference |
| Beneficiary transfer / bill payment | Multi-turn resolution and domain validation; unavailable for execution without a payment adapter |
| Card freeze/unfreeze | Explains the missing issuer integration and links to Cards; does not simulate a state change |
| Decline reasons | Explains that reasons are not recorded and directs users to details and their bank |

The composer is the primary control. History, suggested prompts, structured choices, expandable previous steps, review cards, loading, retry and completion states are integrated into the timeline. Supporting pages are linked from the sidebar/mobile drawer. Existing voice review, history paging, session recovery and unsaved-draft guards are preserved. Mobile keyboard focus, visual-viewport sizing and composer layout were checked.

## Safety, state and audit

- Conversation routes require CUSTOMER or ADMIN. Every request checks conversation ownership; domain services separately check ownership of financial resources.
- Conversation row locks serialize commands. Duplicate client IDs return the original turn. A completed current action returns its original transaction reference on repeated confirmation, including a different client key. Stale action IDs cannot execute.
- Proposals expire after ten minutes. Missing details, cancellation and expiry prevent confirmation.
- Execution revalidates ownership, account status, currency, positive amounts, precision and funds. Core account locking and version checks protect concurrent posting.
- Posting, workflow outcome, turn and audit event share one transaction and roll back together on failure.
- V12 adds workflow state and per-turn snapshots. V13 adds independent action audit records. Audit retains user/action IDs, operation/status, account/target IDs, amount, transaction reference, correlation ID and timestamp, without raw messages. It survives conversation deletion alongside the existing ledger.
- Operational logs contain IDs, intent, state and latency, without raw messages or financial parameters. Correlation IDs are restricted to safe characters.
- Voice preserves essence-only storage. Credential-keyword/apparent-full-card-number input is replaced before interpretation and persistence. Ordinary typed messages remain in private history. This heuristic is not comprehensive sensitive-data classification.

## Verification

Maven `verify`: **74 tests passed**, including **9 new full-application integration tests** against isolated H2 databases using the new conversation migrations. Frontend typecheck, lint, **38 tests**, and JET build pass.

Tests cover ledger-backed execution, same/different-key replay, concurrent confirmations, ownership, unauthorized requests, expired JWT, forbidden role, missing details, invalid/insufficient amounts, balance changes before confirmation, expiry, cancellation, ambiguous beneficiaries, read interruption/recovery, aggregate spending, privacy redaction, audit preservation, confirmation UI, busy/inactive/expired states, selection IDs and chat-first routing.

The running local Oracle-backed API was exercised through the browser: sign-in, chat landing/history, real account selection, validated ₹5,000 proposal, uncertain-request retry and cancellation. **No live funds were moved by browser verification.** Completed money movement was tested in isolation. Desktop and 390px mobile layouts were inspected. Successful workflows showed no application console errors. The JET build has an existing Node deprecation warning.

## Remaining product/integration boundaries

External beneficiaries contain masked destinations and have no usable payment connector. Bills/cards and related products lack execution adapters. These require real integrations before external payments or issuer changes can execute; existing review forms remain available.

The local intent classifier is bounded, not a general-purpose LLM. Streaming is not simulated. Spending uses UTC calendar periods and recorded categories. Salary responses show at most 100 incoming records for the first account; date-filtered bills inspect at most 100 records. Supporting screens remain available for further exploration.

Production decisions remain for step-up authentication and transfer limits, payment/issuer adapters, conversation retention and automated purge, audit retention/access, and stronger sensitive-data classification. No automatic purge was enabled without an approved retention policy. Deleting chat removes its workflow snapshots but preserves financial records and independent audit events.
