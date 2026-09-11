# Nexa banking API

Java 17, Spring Boot 4.1.1, Maven, Oracle JDBC 17 and Flyway. The banking application's implementation now lives in `com.nexa.api.core` inside this API; there is one application entry point and one Maven build.

## Run

Set `BANKING_DB_PASSWORD` and, when needed, `BANKING_DB_URL` and `BANKING_DB_USERNAME`. Existing `NEXA_DB_*` variables remain supported for deployments; `BANKING_DB_*` takes precedence. Defaults target the existing dedicated `NEXA_APP` schema at `jdbc:oracle:thin:@localhost:1521/FREEPDB1`. The source application's SYSTEM account default is replaced with this required application-schema integration.

Run `./mvnw.cmd spring-boot:run` or `mvn spring-boot:run`. The default port is **8088**, configurable through `SERVER_PORT`. The frontend defaults to `http://localhost:8088/api/v1`; set `window.NEXA_API_BASE_URL` for another address.

The default local profile permits `http://localhost:8000`, seeds the existing development login `vishal@example.com` / `NexaDemo@123`, and supplies a development JWT secret. Deployments must select a non-local profile and provide `NEXA_JWT_SECRET` (at least 32 bytes) and `NEXA_CORS_ALLOWED_ORIGINS`. Never use the local seed login or development secret in production.

Flyway owns schema creation and migration; Hibernate validates it. Read [the cutover notes](../../docs/BANKING_INTEGRATION.md) before upgrading an existing database. Application banking timestamps use UTC.

## APIs

- Public: `/api/v1/health`, `/actuator/health`, and `/api/v1/auth/{register,login,refresh,logout}`.
- Customer: `/api/v1/me`, `/api/v1/accounts`, balances and histories, `/api/v1/transactions`, conversations, beneficiaries and banking products. Ownership comes from the signed JWT subject.
- Administrator: the source APIs at `/api/customers`, `/api/accounts`, `/api/transactions`, `/api/journal-entries`, `/api/ledger-entries`. All original CRUD/search/filter routes are retained. Deposit, withdraw and transfer use `POST /api/transactions/{deposit,withdraw,transfer}` with `sourceAccountId`, `destinationAccountId` and positive `amount` as appropriate.

Customer account opening accepts `displayName`, `accountType` (SAVINGS/CURRENT), `currencyCode` (INR), `dateOfBirth` and `address`. It creates an account with zero balance. Deposits and withdrawals require an active SYSTEM/CASH account, provisioned by the migration. No automatic demo credit is issued.

The management APIs use numeric account/customer/journal/ledger IDs and positive transaction amounts. Customer API IDs are their string representation; customer history signs amounts relative to the requested account. Both API surfaces read and write the same core records. The management API retains its `error` response envelope; customer APIs retain their structured error envelope.

## Verification

Run `mvn verify` (or the Maven wrapper). Tests cover authentication, conversations, product routing, source banking rules, database-backed postings and a complete authenticated account/deposit/history flow. Tests use isolated H2 databases; they do not connect to or migrate your Oracle database.
