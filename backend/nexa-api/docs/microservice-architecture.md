# Nexa banking service architecture

## Runtime boundaries

The codebase is organised so these boundaries can run independently. Until each
service has its own deployment and database, the existing `nexa-api` is the
edge-facing composition service and must use module APIs rather than reach into
another module's persistence layer.

| Service | Owns | Primary responsibilities |
|---|---|---|
| Identity service | users, refresh tokens | login, token issuance, customer roles, session revocation |
| Accounts and ledger service | bank accounts, ledger accounts, journals, postings, transactions | account lifecycle, balances, double-entry posting |
| Payments service | beneficiaries, verifications, transfers, approvals, idempotency records | recipient verification and the transfer lifecycle |
| Platform events service | audit events, outbox events | immutable audit trail and reliable integration-event publication |
| API gateway | no banking data | authentication at the edge, routing, rate limits and correlation IDs |

Each service owns its tables. Other services use a versioned HTTP/gRPC contract
or an event; they must not read or update a foreign service's tables.

## Core payment flow

```text
Gateway -> Payments: create draft (Idempotency-Key)
Payments -> Accounts: validate ownership, status, available balance
Payments: create READY_FOR_APPROVAL transfer and approval challenge
Gateway -> Payments: approve challenge
Payments -> Accounts/Ledger: atomically post debit and credit
Payments: mark COMPLETED, add audit and outbox records in the same transaction
Platform events: publish TransferCompleted for notifications and projections
```

The current migration adds the payment and platform-owned tables. The money
movement remains in the accounts/ledger boundary: a transfer cannot be marked
completed until an append-only, balanced journal entry exists. A future service
extraction should move table ownership into separate database schemas before
cutting over traffic; do not use distributed transactions or share ORM entities.

## Event envelope

The transactional outbox payload must use a versioned envelope:

```json
{
  "eventId": "evt_...",
  "eventType": "payments.transfer.completed.v1",
  "aggregateType": "transfer",
  "aggregateId": "trf_...",
  "occurredAt": "2026-09-09T10:00:00Z",
  "data": {}
}
```

Consumers must be idempotent using `eventId`. Kafka or another broker is an
implementation detail that can be added by an outbox publisher without changing
the payment transaction.
