# Teammate database setup

After cloning/pulling Nexa, use [scripts/setup-db.ps1](../../../scripts/setup-db.ps1). It creates a dedicated Oracle schema and runs the existing Flyway migrations, including conversation/audit and account-opening tables. Fresh databases have no demo customer. Existing applied demo checksums remain valid; see [the account-opening upgrade](../../../docs/ACCOUNT_OPENING_UPGRADE.md).

## Prerequisites

- Oracle Database Free installed and running with a writable PDB, normally `FREEPDB1`, and listener on port `1521`. The script configures Nexa's schema; it does not install the Oracle server.
- The `SYSTEM` password for that PDB, or a PDB administrator able to create users, grant privileges and query `DBA_USERS`.
- JDK 17+ with `JAVA_HOME` configured.
- PowerShell **7.2+** (`pwsh`), not Windows PowerShell 5.1.
- Internet access for the Maven wrapper and first dependency download. Separate Maven and SQL*Plus installations are unnecessary.

## First run

If `NEXA_APP` was created by the separate reference project with consolidated V1–V4 migrations, it is incompatible with this repository's migration chain. Keep it intact and use `-Schema NEXA_BANK_APP` for this checkout. Set matching `BANKING_DB_USERNAME` and `BANKING_DB_SCHEMA` in your private configuration. See [schema separation](../../../docs/ACCOUNT_OPENING_UPGRADE.md#after-pulling).

Open PowerShell 7, change to the repository root, and run:

```powershell
.\scripts\setup-db.ps1 -Schema NEXA_BANK_APP
.\scripts\configure-local.ps1
```

Enter the administrator password, then choose an application password of at least 12 characters. Double quotes and control characters are not supported in the application password. Passwords are masked at the prompts and passed through the Java child process environment, not command-line arguments or files.

Defaults: `localhost:1521/FREEPDB1`, schema `NEXA_BANK_APP`, administrator `SYSTEM`, tablespace `USERS`. The separate default avoids targeting the reference project's existing `NEXA_APP`. Override the schema explicitly when upgrading a compatible installation:

```powershell
.\scripts\setup-db.ps1 -DbHost localhost -Port 1521 -Service FREEPDB1 -Schema NEXA_TEAM -Tablespace USERS
```

Schema names must be uppercase, start with `NEXA_` and fit the 30-character identifier limit. The script rejects root/seed containers. New schemas receive `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE` and a 500 MB tablespace quota. Indexes on the user's own tables need no separate `CREATE INDEX` privilege.

Setup sets `BANKING_DB_URL`, `BANKING_DB_USERNAME`, `BANKING_DB_SCHEMA`, `BANKING_DB_PASSWORD` and `SPRING_PROFILES_ACTIVE=local` in the **current PowerShell process**. Start the API in the same terminal:

```powershell
cd apps/api
.\mvnw.cmd spring-boot:run
```

From a different shell, use `pwsh -NoExit -File ./scripts/setup-db.ps1` to keep the PowerShell process and connection variables available afterward. Closing the terminal discards these environment settings. On macOS/Linux with PowerShell 7, run `./scripts/setup-db.ps1`, then `sh ./mvnw spring-boot:run` from `apps/api`.

The API defaults to port **8088**. In another terminal, run `npm ci` and `npm run dev` from `apps/web`. Register your own customer. Configure `NEXA_ADMIN_EMAIL` and `NEXA_ADMIN_PASSWORD` privately for your administrator; no shared demo login is created.

## Later pulls / new terminals

From the repository root:

```powershell
.\scripts\setup-db.ps1 -ExistingSchema -Schema NEXA_BANK_APP
```

This asks only for the current application password, validates migration checksums and applies pending migrations. Normal API startup also runs Flyway. Existing passwords, grants and data are preserved. No clean, repair, baseline or drop operation is performed. A first-run invocation that finds an existing schema also preserves it and verifies its current password before migrating.

## Troubleshooting

- `ORA-01408` on the last statement of `V31__local_card_applications.sql`: use the [verified V31 recovery procedure](../../../docs/V31_ORACLE_RECOVERY.md). The fixed migration reuses the existing V21 index; the dedicated recovery verifies the partially committed schema before reconciling only the known failed V31 history entry.
- Connection/service errors: check Oracle, the listener and the requested PDB. `CDB$ROOT` and `PDB$SEED` are deliberately refused.
- `ORA-01017`: check the password and PDB service. Existing passwords are never reset.
- A generic setup failure is preceded by the captured Java/Oracle diagnostic, including the failing connection/setup stage. Report that diagnostic with passwords omitted. Maven `BUILD SUCCESS` only means the helper compiled and dependencies resolved; it does not mean Oracle setup succeeded.
- Privilege/quota errors: ask the PDB administrator to check privileges and the tablespace quota. If user creation succeeded but granting privileges failed, fix the grants rather than dropping the user.
- Maven errors: check `JAVA_HOME`, network/proxy settings and Maven configuration. `-MavenRepository <path>` selects a different local cache when necessary.
- Flyway/cutover errors: do not repair or delete history to bypass them. Oracle DDL can commit partially. Inspect the failed migration and follow the [cutover notes](../../../docs/BANKING_INTEGRATION.md) for existing data.

This is a local development workflow. Do not target an unrelated populated schema. Production credentials, privileges and backups need a separate deployment process.

Setup compiles in an isolated temporary `.tools/db-setup-*` directory so the build does not restart an already-running API through DevTools. Stop the API before migrating or recovering its schema.

## Validation

The helper is source-launched Java outside the application source tree, so it adds no second application entry point. `scripts/tests/SetupDatabaseTest.java` checks identifier restrictions, password quoting and root/seed rejection without connecting to Oracle. Run `pwsh -File scripts/tests/SetupLauncherTest.ps1` to check visible child-process errors, exit codes, large concurrent output, password redaction and the separate default schema without connecting to Oracle. Maven dependency resolution, helper compilation and PowerShell syntax are checked independently of actual database provisioning. A fresh Oracle provisioning run requires the teammate's local administrator credentials.
