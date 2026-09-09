# Nexa API

Spring Boot backend for the Nexa conversational-banking prototype.

## Current foundation

- Java 21 Maven application
- Public API health check at `GET /api/v1/health`
- Spring Boot Actuator health check at `GET /actuator/health`
- CORS limited by profile configuration
- Request correlation IDs returned as `X-Correlation-ID`
- Consistent JSON error responses
- Local Oracle AI Database Free configuration, with Flyway migrations
- BCrypt password hashing and stateless JWT authentication
- Rotating, server-side revocable refresh tokens
- Customer ownership checks for accounts and transactions
- Payment-service entities for beneficiaries, transfers, strong approvals, and idempotency
- An audit and transactional-outbox boundary for service-to-service events

## Service boundaries

The application is prepared for independently deployable Identity, Accounts and
Ledger, Payments, Platform Events, and API Gateway services. Their ownership
rules and the transfer event contract are documented in
[docs/microservice-architecture.md](docs/microservice-architecture.md).

The current `nexa-api` remains the composition runtime while those services are
extracted. This avoids sharing financial tables or introducing distributed
transactions before the payment workflow is implemented end to end.

## Run locally

Prerequisite: Java 21 available on `PATH`. The included Maven Wrapper downloads the required Maven version automatically.

```powershell
cd D:\Nexa\apps\api
.\mvnw.cmd spring-boot:run
```

The default `local` profile accepts the Oracle JET application at `http://localhost:8000` and connects to `localhost:1521/FREEPDB1` as `NEXA_APP`.

Before starting the application, create the `NEXA_APP` database user and set its password only in your terminal:

```powershell
$env:NEXA_DB_PASSWORD = "your-local-nexa-app-password"
```

The schema is created and seeded automatically by Flyway on the first application start. `SYSTEM`, `SYS`, and `PDBADMIN` must never be used by the application.

The local profile seeds a development-only login:

- Email: `vishal@example.com`
- Password: `NexaDemo@123`

Use this account only for local development. The local JWT signing secret can be overridden with `NEXA_JWT_SECRET`; deployed environments must always supply a unique secret of at least 32 bytes.

## Authentication API

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Access tokens last 15 minutes by default. Send one to protected endpoints as `Authorization: Bearer <accessToken>`. Refresh tokens are rotated: after a successful refresh, discard the old refresh token and retain the new one.

See [docs/authentication.md](docs/authentication.md) for the complete Postman verification flow.

## Read-only API

- `GET /api/v1/me`
- `GET /api/v1/accounts`
- `GET /api/v1/accounts/{accountId}`
- `GET /api/v1/accounts/{accountId}/balance`
- `GET /api/v1/accounts/{accountId}/transactions?page=0&size=50&category=&from=&to=`

All read APIs require a valid access token and derive the current user from its signed JWT subject. Account queries additionally verify that the requested account belongs to that user.

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/health
Invoke-RestMethod http://localhost:8081/actuator/health
```

## Configuration

`application-local.yml` contains only safe local defaults. Copy `application-example.yml` or set environment variables when a deployment needs different settings. Do not commit database credentials, tokens, or `.env` files.

AI integration is intentionally not part of the authentication foundation.
