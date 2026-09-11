# Structured banking in chat

> For the merged banking core, current setup and schema cutover, see [Banking integration](BANKING_INTEGRATION.md). Pre-merge implementation and activation notes below are historical.

Conversation text remains in message bubbles. A turn may additionally contain a
versioned `banking` payload. The frontend renders that payload directly and never
extracts balances, statuses or transaction IDs from prose.

The API now also routes mandates, bills, cards, beneficiaries, scheduled payments,
loans and action preparation; see CONVERSATIONAL_BANKING_BACKEND.md for details.
Account summaries contain only
display-safe fields. Transaction previews contain at most five results, their
source account, and the total count. Amounts in saved snapshots are decimal strings.
V8 stores the payload as nullable JSON in a CLOB beside the immutable exchange.
Retries return the original snapshot. Existing turns with no payload still render.
Restart the configured API to apply V8 (and V7 if it has not been applied yet).

The frontend uses shared `MoneyAmount`, `StatusBadge`, `DetailRow`, `AccountSummary`,
`TransactionList`, and `TransactionDetails` components. It formats amounts using
Indian digit grouping and rounds decimal strings without first converting to a
floating-point number. Account/card identifiers are masked again at rendering.
Statuses are always text-labelled. Failed or pending transactions are not described
as completed spending.

Account summaries open authenticated transaction history inside the same banking surface.
Recent transaction previews group entries by local calendar date. Rows open inline
details with account, date/time, status and category. References live in an expandable
section with a copy control. There is no payment-method field in the current API, so
the UI labels the supplied field “Transaction type” rather than inventing UPI/card
information. Help discloses that issue reporting is not connected.

“View all transactions” loads ten records per page through the existing owned-account
API. It does not append the entire history to the saved conversation. The original
snapshot remains unchanged; expanded history is freshly queried. The existing
transaction API sends numeric amounts, while conversation snapshots use exact
decimal strings; extending decimal-string serialization to all banking endpoints
would make their wire contracts consistent.

Read-only renderers and typed contracts are also provided for mandates, upcoming
payments, beneficiaries, cards and loans. The current backend does not emit these
variants. No fake financial actions, mandate management or payment confirmation
controls are enabled. When those APIs arrive, action controls must be tied to their
server drafts, explicit confirmation and authoritative execution result.

## Verification

- Backend: 34 tests pass, including structured balances/transaction previews,
  decimal serialization, timestamps, ownership, retries and voice privacy.
- Frontend: `node --test tests/banking-content.test.cjs` checks money grouping,
  rounding, masking, status wording and date labels. TypeScript checking applies.
- `tests/messenger-fixture.cjs` remains an isolated UI-only server. Ask for a balance
  or transactions to inspect structured test data. `test mandates` exercises the
  read-only mandate statuses. Fixture records are never used by production APIs.
- Oracle migration application and real backend deployment still need the local
  database configuration; unit tests do not substitute for that deployment check.
- Mobile (390px) and desktop preview checks verified multiple balances, signed
  transactions, pending/failed wording, inline details, reference copying, paging,
  keyboard focus and all five mandate statuses. No horizontal overflow was present.
- The normal staging build hit locked theme assets. Oracle JET assets and TypeScript
  compilation completed in an isolated staging folder; verified application files
  were then copied to the normal web preview output. This did not change project
  configuration or the running development server.

## Response presentation and extension

`AssistantResponse` selects the text bubble or independent banking surface.
`BankingResponse` uses a typed renderer registry: adding a variant to `BankingContent`
requires its renderer entry, without editing the conversation timeline. Unknown types
or versions preserve the assistant text and show a compatibility message.

Reusable renderers cover accounts, transactions, mandates, upcoming/scheduled
payments, beneficiaries, bills, cards and loans. Empty collections explain their state.
`SCHEDULED_PAYMENTS` uses the same `payments` fields as `UPCOMING`. `BILLS` contains
`bills`: id, billerName, amount, currencyCode, status, optional dueAt, category and
customerNumberMasked. Amounts should be decimal strings. These additional variants
are frontend contracts; the current API still emits only accounts and transactions.

Validation for the presentation update: TypeScript checks, isolated Oracle JET asset
build, and eight Node tests covering presentation selection, compatibility fallback,
registrations, empty bills, currency precision, masking, status and dates.

The backend foundation now routes the additional domains. See CONVERSATIONAL_BANKING_BACKEND.md for the current contract, seed activation and verification limits. Earlier notes about only accounts/transactions being emitted describe the prior implementation.
