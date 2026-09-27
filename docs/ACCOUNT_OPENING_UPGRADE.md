# Account opening and teammate upgrade

This change ports the reviewed account-opening flow into the current app while retaining its navigation, overview, styling and existing banking features. It adds a customer application panel under Accounts and an admin account-application queue.

## Behavior

1. Register a customer and complete the profile. Apply for a personal INR savings account with phone number, date of birth, identity details, consent and an opening deposit of INR 1,000–10,000,000 (at most two decimals). Phone remains optional at registration but is required for account opening; an existing profile phone is prefilled. The application accepts 10–15 digits with an optional leading `+` and removes spaces, hyphens and parentheses.
2. The server requires an adult applicant and rejects another savings account or live application for that customer. Existing historical accounts are preserved.
3. Submit the application. An administrator checks the original identity document in person and records the decision and reason. This is manual review, not government identity verification. Aadhaar stores only the last four digits; PAN/passport identifiers are encrypted and normally masked.
4. Approval changes the status to awaiting cash. It does not create a funded account. The administrator sees the customer's stored deposit amount as read-only and confirms actual receipt of that amount.
5. The server generates the receipt number and posts the cash into a protected opening holding account. Client-supplied amount and receipt-number fields are not accepted. Retrying the same confirmed request returns the saved receipt and does not credit twice.
6. Open the account. The server generates its account number and allocates the receipted amount to it, with balanced journal entries. Cancellation before cash and a recorded refund workflow after cash are supported.

Instant account creation through the former customer endpoint and generic admin customer-account creation are blocked. The protected opening holding account cannot be edited or funded through generic account-management routes. Cash operations remain manual acknowledgments: no payment gateway or external KYC service is connected.

**Review and update** allows the customer to correct phone, date of birth, deposit and optionally the identity document while the application is `DRAFT` or `CHANGES_REQUESTED`. Saving without selecting identity replacement retains the saved identifier. Name and email remain profile snapshots. Edits are version-checked, recorded in application history and must be submitted for review. After cash is recorded, these details cannot be changed. The application phone updates the customer profile when account opening succeeds.

The existing customer table requires unique phone numbers. A number already assigned to another customer is rejected with a clear message, including equivalent saved spacing/parentheses/hyphens. Availability is rechecked before submission, approval, receipt and opening. A conflict during the final phone update rolls back account creation and allocation; previously received cash remains refundable.

Admin application rows open when clicked, with the existing link retained for keyboard navigation. The technical service-readiness panel and identifier-reveal panel are removed. Readiness still gates actions internally; **Refresh applications** or **Refresh application** refreshes the checks, and unavailable actions show a concise message. Approval still requires the original-document confirmation. Receipt history displays **Cash receipt recorded** with status **Cash received**.

## After pulling

An existing schema is reusable only when it was created by this repository's migration history. The separate `C:\Users\Purva\Project\Nexa` reference project uses consolidated V1–V4 migrations, beginning with `V1__initialize_nexa_schema.sql`; this checkout starts with `V1__create_core_banking_schema.sql` and upgrades through V27. These histories are incompatible even though both applications have similarly named tables. Do not point this checkout at that project's `NEXA_APP`, reset it, or repair Flyway history to force compatibility.

When both projects share one Oracle installation, create a separate application schema from this repository root:

```powershell
.\scripts\setup-db.ps1 -Schema NEXA_BANK_APP
```

This creates a missing dedicated schema without deleting `NEXA_APP`. In `apps/api/.env`, set both `BANKING_DB_USERNAME=NEXA_BANK_APP` and `BANKING_DB_SCHEMA=NEXA_BANK_APP`, and use the password chosen for that new schema. The two schemas can use the same Oracle server, port and PDB. Later setup runs must retain `-Schema NEXA_BANK_APP`; add `-ExistingSchema` once it exists. Setup exports matching username/schema variables for the terminal that launches the API. These environment variables override `.env`, so use matching settings or a new terminal if earlier runs targeted another schema.

No existing migration file was rewritten. V24 adds the application, cash-receipt, event and compatibility document tables plus an empty opening holding account. V25 retires only the exact historical Vishal fixture; see [demo retirement](DEMO_RETIREMENT.md). V26 adds the `APPLICATION_UPDATED` audit-event type and its customer/editable-state constraint. Existing legitimate customers and balances are preserved. Do not reset a database or repair Flyway checksums to install this change.

V27 adds durable bill-payment reviews and attempts, with references to existing bills, saved payees, accounts and posted transactions. It does not seed payees, bills or money. See [internal bill payments](BILL_PAYMENTS.md) for behavior and verification steps.

V28 adds encrypted external-bank payees and V29 adds sandbox transfer reviews and receipts. These migrations do not seed or move funds. See [external bank transfer setup](EXTERNAL_BANK_TRANSFERS.md); provider credentials are optional and external submission remains disabled until configured.

For a working installation already on V25, restart the backend with the existing database settings and keys: Flyway applies V26 through V29 automatically. Rebuild/restart the frontend and refresh the page. Existing drafts without a phone can be corrected through **Review and update**; an administrator can request corrections for a pending application missing its phone. Already-opened accounts remain unchanged.

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

Register a fresh customer without a phone, then verify account opening requires one. Save an application, use **Review and update** to correct phone, birth date and deposit, and submit it. Sign in as your configured admin, click its queue row, review it, confirm the displayed cash amount and open the account. Check that the opening balance equals the customer's revised amount, the receipt exists, and repeated actions do not change that balance. Confirm the old demo login fails after V25. Restart and verify the application and receipt persist.

Transaction-history categories now come from the selected account's complete recorded history. The dropdown uses readable names and resets when changing accounts. Search accepts a shop/biller name or a reference copied from transaction details. Bill creation now supplies the missing SQL value, permits bills without a funding account to appear in the owner's list, and validates inputs against existing column limits; these fixes require no additional migration.

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

## Verification on 26 September 2026

- Backend Maven `verify`: 467 tests, zero failures/errors, one optional model test skipped; executable JAR built from an isolated copy matching the current API source. Live Oracle tests were disabled.
- Frontend: 329 tests passed; typecheck and lint passed. The standard build encountered a locked generated theme asset, so Oracle JET was also built successfully from an isolated source copy using the existing dependencies. Optional Sass and Node deprecation warnings remain.
- Coverage includes required and conflicting phones, full application corrections, unchanged-identity preservation, ownership/version/retry checks, rollback after a late opening failure, actual bill SQL and account-category queries, admin row navigation, and dropdown/error recovery behavior.
- Verification output is ignored under `.tools`. No live Oracle upgrade, authenticated browser walkthrough, commit or push was performed in this change. Restart both applications and perform the submission check above on the intended local schema.

After adding [internal bill payments](BILL_PAYMENTS.md), final verification ran 489 backend tests with zero failures/errors and one optional model test skipped. Frontend typecheck, lint and all 343 tests passed, as did the isolated Oracle JET production release build. V27 now forms part of the teammate upgrade. No live database migration or customer transfer was performed during these checks.

After adding [Cashfree sandbox external transfers](EXTERNAL_BANK_TRANSFERS.md), final verification ran 547 backend tests with zero failures/errors and one optional model test skipped. Frontend typecheck, lint, all 356 tests and the isolated production release build passed. V28 and V29 now form part of the teammate upgrade. Provider credentials stay in each developer's ignored local configuration. Actual Cashfree sandbox verification requires those credentials; no live database or financial operations were performed by these checks.
