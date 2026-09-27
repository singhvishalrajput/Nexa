# Chat-first banking

The existing JET/Preact and Spring Boot application now lands in persistent chat. Accounts, transactions, payments, products, profile/security and administrator screens remain supporting routes. Existing authentication, domain queries, transaction posting, journals and ledger entries are reused.

## Architecture

ConversationWorkspace → authenticated Conversation API → ConversationService → WorkflowService / ConversationInterpreter → banking domain services.

ConversationService owns conversation authorization, row locking, retry keys and immutable turn history. WorkflowService persists multi-turn selections and proposals separately from banking records. CustomerTransferService checks customer ownership before calling the existing core transaction service. SpendingQueryService aggregates authoritative transactions rather than a page of chat history. The existing local intent classifier is retained; no LLM receives database access or decides authorization.

The original turn API remains compatible. Turns can now contain a versioned `workflow` with action ID, operation, status, missing field, structured choices, source/target labels, decimal amount, currency, expiry, confirmation requirement, execution availability and transaction reference. States are COLLECTING, REVIEW, COMPLETED, FAILED, CANCELLED, EXPIRED and UNAVAILABLE.

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
| Beneficiary transfer | Explicit confirmation posts a real transfer to a verified saved Nexa recipient |
| Bill payment | Link the recipient under Bills first; chat reviews the actual recipient and outstanding amount, then explicit confirmation posts one internal Nexa payment. See [bill payments](BILL_PAYMENTS.md) |
| Loan application | An explicit application request opens the existing loan form inside chat. The customer selects a loan type, account, amount and tenure and uploads the three required salary slips before submitting for administrator review. Chat text alone creates no application. |
| Card freeze/unfreeze | Explains the missing issuer integration and links to Cards; does not simulate a state change |
| Decline reasons | Explains that reasons are not recorded and directs users to details and their bank |

The composer is the primary control. History, suggested prompts, structured choices, expandable previous steps, review cards, loading, retry and completion states are integrated into the timeline. Supporting pages are linked from the sidebar/mobile drawer. Existing voice review, history paging, session recovery and unsaved-draft guards are preserved. Mobile keyboard focus, visual-viewport sizing and composer layout were checked.

The **Send Money** chat shortcut submits the same request as typing “send money”; it keeps the customer in the conversation to select and review payment details. Explicit bill commands such as “pay bill”, “pay bills” and “paybill” start or resume the bill workflow instead of inheriting an unrelated earlier read, including a loan summary. A balance question can interrupt a pending payment without authorizing it. Posting still requires the latest review's explicit confirmation.

Loan application prompts use the server-authored version-1 `LOAN_APPLICATION` response and a stable request key for that chat turn. They reuse the multipart loan endpoint and its document validation; salary-slip contents are never sent as conversational text. `GET /api/v1/loans/applications/by-request/{applicationKey}` retrieves only the signed-in customer's matching application with `Cache-Control: no-store`, or returns 404. This read lets chat recover a submitted application after a reload or lost response without creating another loan or requiring another upload. Requests to view loans, repay loans or ask how applications work retain their separate flows.

## Safety, state and audit

- Conversation routes require CUSTOMER or ADMIN. Every request checks conversation ownership; domain services separately check ownership of financial resources.
- Conversation row locks serialize commands. Duplicate client IDs return the original turn. A completed current action returns its original transaction reference on repeated confirmation, including a different client key. Stale action IDs cannot execute.
- Bill payment reviews expire after five minutes; other conversation proposals retain their existing expiry. Missing details, cancellation and expiry prevent confirmation.
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

External beneficiaries contain masked destinations and have no usable payment connector. Internal Nexa bill payments execute through the ledger; external bill-provider settlement and card-network changes still require integrations. Cached bill simulations cannot execute or become real debits.

The local intent classifier is bounded, not a general-purpose LLM. Streaming is not simulated. Spending uses UTC calendar periods and recorded categories. Salary responses show at most 100 incoming records for the first account; date-filtered bills inspect at most 100 records. Supporting screens remain available for further exploration.

Production decisions remain for step-up authentication and transfer limits, payment/issuer adapters, conversation retention and automated purge, audit retention/access, and stronger sensitive-data classification. No automatic purge was enabled without an approved retention policy. Deleting chat removes its workflow snapshots but preserves financial records and independent audit events.
