# Retirement of the original local demo identity

Migration `V25__retire_known_demo_identity.sql` retires the old seeded login only when both of these stored values match:

- `CUSTOMERS.USER_ID = usr_01JDEMO000000000000001`
- `LOWER(CUSTOMERS.EMAIL) = vishal@example.com`

The migration does not match by a person's name. Either value alone is insufficient. It is a no-op on a seed-free installation or when the exact subject/email pair is absent.

For that single fixture, V25 clears the reusable password hash, revokes refresh credentials, sets the customer status to `DISABLED`, and changes its active customer accounts to `BLOCKED`. Closed accounts remain closed. Existing blocked accounts retain their state. Account versions increment only when active accounts are blocked.

Balances, customer/account IDs, financial transactions, journals, ledger entries, loans, loan documents, conversations and audit history are retained. Other customers, including real customers named Vishal, are untouched. Retained records are intentional financial provenance, not an active demonstration login. This is retirement, not physical deletion or a database reset.

Login and refresh already check that the customer is active. An issued access token must also pass the application's current active-user check to ensure immediate retirement before token expiry; database changes alone cannot revoke a signed JWT. Check the runtime security implementation when deploying this migration.

The migration is delivered through normal Flyway startup. Do not run the older demo seeder or cleanup utility to achieve this retirement. No live database was accessed while authoring it. Isolated H2 regression tests cover exact matching, partial-match non-targets, empty installations and preservation of monetary history; these tests do not certify execution against a live Oracle instance.
