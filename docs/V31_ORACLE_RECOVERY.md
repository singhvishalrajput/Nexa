# V31 Oracle duplicate-index recovery

V21 already creates `IDX_LOAN_REVIEW_QUEUE` on `ACCOUNTS(ACCOUNT_TYPE, PRODUCT_STATUS, CREATED_AT)`. The original V31 tried to create another equivalent index named `IDX_CARD_REVIEW_QUEUE`. Oracle rejects that statement with `ORA-01408`. The corrected V31 reuses the V21 index.

The failure occurs at the last V31 statement. Oracle DDL can commit statement by statement, so the four card columns, two unique indexes and two check constraints may already be installed even though Flyway records V31 as failed. Simply retrying the original `ALTER TABLE` statements would conflict with the existing columns. Generic Flyway repair alone does not reconcile that schema state.

## Recover this exact failure

1. Stop the Nexa backend, including any other process using this schema to migrate or change accounts. Keep the Oracle server running.
2. Use this corrected checkout and run from the repository root:

   ```powershell
   .\scripts\setup-db.ps1 -ExistingSchema -Schema NEXA_BANK_APP -RecoverCardMigration
   ```

3. Enter the existing schema password at the masked prompt. The recovery checks the Oracle owner, migration history and actual card column/index/constraint definitions. It accepts only the known failed V31 with the earlier migrations still valid. It then reconciles that one history entry with the fully installed corrected V31 definition and validates the result. Application rows and earlier migration records are preserved.
4. After success, start the backend from the same terminal:

   ```powershell
   cd apps/api
   .\mvnw.cmd spring-boot:run
   ```

The maintenance command is scoped to V31. Normal backend startup performs its usual Flyway validation. Existing databases that have never attempted V31 can use normal setup with the corrected source.

Newer migration files, such as V32 bank funding, may be present while V31 is still the latest recorded migration. The recovery accepts those files only when Flyway reports them as pending; it still verifies every recorded migration through V31. Their presence does not cause them to be marked applied by recovery. A later recorded migration, another failure or an earlier history mismatch still stops this recovery path.

An earlier recovery helper incorrectly stopped at `A recorded migration is missing or differs from the original resolved script: V1` because Flyway 12.4 does not populate two optional metadata accessors. That rejection happened before any recovery write. The corrected helper compares the actual resolved migration resource, retaining script, description, type and checksum checks. With the updated code, rerun the same recovery command; no manual history edit is needed for that false rejection.

For an inspection without database changes, use:

```powershell
.\scripts\setup-db.ps1 -ExistingSchema -Schema NEXA_BANK_APP -InspectCardMigration
```

Both maintenance flags require `-ExistingSchema`, and they cannot be combined. If a check fails, keep the reported diagnostic and investigate that mismatch before proceeding. Do not delete the schema, drop card columns, disable constraints or run blanket Flyway repair to bypass a failed check.

## Setup isolation and verification

The setup launcher compiles a temporary source copy under ignored `.tools/db-setup-*`. This avoids triggering the running backend's DevTools through changes to `apps/api/target`. Passwords are passed through the child process environment and redacted from its output. The temporary copy is removed after the command.

Automated checks cover the duplicate-index regression, the guarded recovery, and maintenance flag validation. H2 tests do not certify Oracle DDL execution; the recovery verifies the Oracle schema when you run it with your local credentials.

References: [Oracle ORA-01408](https://docs.oracle.com/en/error-help/db/ora-01408/) and [Flyway repair behaviour](https://documentation.red-gate.com/flyway/reference/commands/repair).
