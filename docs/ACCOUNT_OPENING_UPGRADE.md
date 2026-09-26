# Account opening and teammate upgrade

This change ports the reviewed account-opening flow into the current app while retaining its navigation, overview, styling and existing banking features. It adds a customer application panel under Accounts and an admin account-application queue.

## Behavior

1. Register a customer and complete the profile. Apply for a personal INR savings account with date of birth, identity details, consent and an opening deposit of INR 1,000–10,000,000 (at most two decimals).
2. The server requires an adult applicant and rejects another savings account or live application for that customer. Existing historical accounts are preserved.
3. Submit the application. An administrator checks the original identity document in person and records the decision and reason. This is manual review, not government identity verification. Aadhaar stores only the last four digits; PAN/passport identifiers are encrypted and normally masked.
4. Approval changes the status to awaiting cash. It does not create a funded account. The administrator sees the customer's stored deposit amount as read-only and confirms actual receipt of that amount.
5. The server generates the receipt number and posts the cash into a protected opening holding account. Client-supplied amount and receipt-number fields are not accepted. Retrying the same confirmed request returns the saved receipt and does not credit twice.
6. Open the account. The server generates its account number and allocates the receipted amount to it, with balanced journal entries. Cancellation before cash and a recorded refund workflow after cash are supported.

Instant account creation through the former customer endpoint and generic admin customer-account creation are blocked. The protected opening holding account cannot be edited or funded through generic account-management routes. Cash operations remain manual acknowledgments: no payment gateway or external KYC service is connected.

## After pulling

An existing schema is reusable only when it was created by this repository's migration history. The separate `C:\Users\Purva\Project\Nexa` reference project uses consolidated V1–V4 migrations, beginning with `V1__initialize_nexa_schema.sql`; this checkout starts with `V1__create_core_banking_schema.sql` and upgrades through V25. These histories are incompatible even though both applications have similarly named tables. Do not point this checkout at that project's `NEXA_APP`, reset it, or repair Flyway history to force compatibility.

When both projects share one Oracle installation, create a separate application schema from this repository root:

```powershell
.\scripts\setup-db.ps1 -Schema NEXA_BANK_APP
```

This creates a missing dedicated schema without deleting `NEXA_APP`. In `apps/api/.env`, set both `BANKING_DB_USERNAME=NEXA_BANK_APP` and `BANKING_DB_SCHEMA=NEXA_BANK_APP`, and use the password chosen for that new schema. The two schemas can use the same Oracle server, port and PDB. Later setup runs must retain `-Schema NEXA_BANK_APP`; add `-ExistingSchema` once it exists. Setup exports matching username/schema variables for the terminal that launches the API. These environment variables override `.env`, so use matching settings or a new terminal if earlier runs targeted another schema.

No existing migration file was rewritten. V24 adds the application, cash-receipt, event and compatibility document tables plus an empty opening holding account. V25 retires only the exact historical Vishal fixture; see [demo retirement](DEMO_RETIREMENT.md). Existing legitimate customers and balances are preserved. Do not reset a database or repair Flyway checksums to install this change.

For an existing schema created by this repository, from the repository root in PowerShell 7.2+ (replace `NEXA_APP` with that schema's actual name):

```powershell
.\scripts\configure-local.ps1
.\scripts\setup-db.ps1 -ExistingSchema -Schema NEXA_APP
```

For a new database, omit `-ExistingSchema`. Setup asks for the local database password; it does not use another developer's credentials. New databases do not seed Vishal or shared customer accounts. Old demo migration files remain only so existing Flyway histories can validate their original checksums. A partially applied old demo history stops with an explanation instead of silently adding missing demo data.

`configure-local.ps1` creates an ignored `apps/api/.env` containing private identity and JWT keys. It never overwrites an existing file. Start the API from `apps/api` so Spring loads it. Add `NEXA_ADMIN_EMAIL` and `NEXA_ADMIN_PASSWORD` to that private file (or set them in the process environment) to provision your own administrator. Use 16–72 characters for the administrator password. The database variables set by setup last only in that terminal.

```powershell
cd apps/api
.\mvnw.cmd spring-boot:run
```

In a second terminal:

```powershell
cd apps/web
npm ci
npm run dev
```

Keep the identity key with its database backup. Do not regenerate it for an existing database with encrypted applications, put it in Git, or share a personal database password. Developers with independent databases use independent keys. Deployments sharing one database must securely use the same configured identity key and key ID. Missing/invalid keys disable identity operations and are reported by application readiness.

## Submission check

Register a fresh customer, submit an application, sign in as your configured admin, review it, confirm the displayed cash amount and open the account. Check that the opening balance equals the customer's amount, the receipt exists, and repeated actions do not change that balance. Confirm the old demo login fails after V25. Restart and verify the application and receipt persist.

Automated verification uses isolated H2 databases, including schema constraints, authenticated workflow requests and replay checks, plus frontend component/service tests and a production build. An actual Oracle upgrade and browser submission check require the target database credentials; passing H2 tests alone does not certify Oracle deployment.

## Scope and remaining work

No unused directories were removed. The broader findings and directory inventory remain in [the project audit](audit/PROJECT_AUDIT.md). This focused change does not claim to resolve all of those findings or make the learning showcase a production banking platform. Old six-table documentation describes the core model; the new opening tables are deliberate additions.

## Verification on 25 September 2026

- Backend Maven `verify`: 444 tests, zero failures/errors, one skipped; executable JAR built.
- Frontend: 319 tests passed; typecheck, lint and Oracle JET build passed. Optional Sass compiler warning remains.
- Teammate database helper: compiled with the configured JDK 17; 19 offline checks passed without connecting to a database.
- Private-key setup: creates two separate 32-byte keys, preserves them on a repeated run, and keeps the local file ignored by Git.
- Oracle JET development server started successfully. Browser automation reported no available browser, so authenticated browser interactions and visual screenshots remain unverified.
- No live Oracle migration, real customer transaction, commit or push was performed.
