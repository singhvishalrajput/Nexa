# Banking integration

## Final structure and authority

The former banking-application Java sources are consolidated into the API's conventional layers: `apps/api/src/main/java/com/nexa/api/{beans,repository,service,controller,config,security,exep}`. Customer, Account, BankTransaction, JournalEntry and LedgerEntry are the only active core banking entities. The existing Nexa application entry point scans them alongside identity, conversations and product services. The separate source project's entry point, POM, configuration, duplicate exception classes and manual create/recreate scripts are superseded.

The merged build uses the source's Java 17, Spring Boot 4.1.1, Oracle JDBC 17 and devtools dependencies, with Nexa's security, validation, actuator and Flyway integrations. Spring Boot 4's modular test packages and Jackson 3 are used throughout. There is no second backend or parallel account/transaction store.

## Conflicts resolved

- Source account categories, types, ACTIVE/BLOCKED/CLOSED statuses, balance calculations, validation, deposits, withdrawals, transfers and double-entry journal/ledger postings take precedence.
- Automatic opening credits and the old BankAccountEntity/Repository, TransactionEntity/Repository/RecordingService and seven classes in the old ledger package are removed. Customer API projections use the source repositories and services.
- Source customer data is authoritative after a banking profile is created. Login identity records remain separate because they own credentials, roles and sessions. An explicit customer-to-user link determines ownership; existing customers are never claimed merely by an email match.
- Core management routes keep their source contracts but require ADMIN, preserving the main application's access boundary. Customer APIs keep scoped views needed by the web client and conversational responses.
- The source's port 8088 is the default everywhere. BANKING_DB variables take precedence; NEXA_DB variables and the dedicated NEXA_APP/PDB connection remain for existing deployments. Flyway replaces destructive manual schema recreation.
- Currency and merchant/category/payment metadata are retained for Nexa displays. Wider existing profile/account-name limits and incomplete historical DOB/address fields avoid data loss. New customer creation still requires the source fields. Account optimistic locking preserves the main application's protection against concurrent lost balance updates.

## Database cutover

V1–V10 remain unchanged so existing Flyway checksums remain valid. V11 installs the source schema and migrates the main application's accounts/history and dependent references. It supports an empty main schema (after preceding migrations) and the existing Nexa schema; it is not an importer for a separately populated external banking database.

1. Back up the existing Oracle schema and test V11 against an isolated restored copy before deployment. Oracle DDL auto-commits; restore that copy before retrying a failed cutover rather than rerunning partially applied DDL.
2. The preflight rejects CREDIT accounts, non-INR accounts, held balances, fractional-paise balances/transactions, zero or pending transactions, and duplicate customer phone numbers. These need explicit reconciliation instead of silent behavioral conversion.
3. Original tables are renamed to `legacy_bank_accounts`, `legacy_transactions`, `legacy_journal_entries`, `legacy_ledger_accounts` and `legacy_ledger_postings` for audit. No application code reads or writes them. `banking_account_migration` records old-to-new IDs for audit and cutover only.
4. Accounts receive numeric IDs, preserve balances, and map FROZEN to BLOCKED. A SYSTEM/CASH account starts with the migrated customer balance total. Existing signed transaction history becomes source DEPOSIT/WITHDRAWAL history; POSTED becomes SUCCESS. Original references/types/journals remain in the archive. Migration does not replay balances or fabricate absent historical journal entries. New transactions always use the source posting service.
5. Transfers, banking-product relationships, product payloads and stored conversation banking content are updated to numeric account references. Refresh external bookmarks or API clients that stored old account IDs. Structured snapshots retain historical display amounts and statuses.
6. Existing profiles retain unknown DOB/address as null. Account opening collects missing information, and account settings allows address completion. A customer created through the administrator API can be linked to an existing login by setting its USER_ID through the deployment's verified administrative process; it is not auto-linked by email.

The old standalone manual recreation script is intentionally removed because it drops financial tables and is incompatible with the retained login/product/conversation infrastructure.

## Preserved main functionality

Oracle JET UI and assets, JWT/refresh-token authentication, user ownership, CORS, correlation IDs, health checks, conversation storage/interpretation, banking product views, beneficiaries, external-transfer workflow records, audit/outbox infrastructure and all unaffected tests remain. External-transfer workflow records are distinct from the source core's immediately posted internal transfers.

The customer response projection is the necessary web integration, not an independent banking implementation. Monetary changes have a single posting service.

## Validation and limits

Backend: `mvn verify`. Frontend: `npm run typecheck`, `npm test`, `npm run build`. Neither project defines a lint command; whitespace and dangling-reference checks supplement compilation.

H2 integration tests validate entity/repository wiring, balance changes, journals, ledger entries, ownership and the authenticated HTTP flow. They do not certify Oracle-specific V11 DDL. No existing Oracle database was changed during the merge. Validate that cutover on an isolated Oracle copy before deployment. The frontend build may emit its existing optional Sass warning; styles are CSS and the build succeeds.
