# Authentication and Postman verification

## Start the API

Stop any older running instance before restarting so Flyway can apply authentication migrations V3 and V4.

```powershell
cd "C:\Users\Vishal Singh\ojet\Backend\nexa-api"
$env:NEXA_DB_PASSWORD = 'your-nexa-app-database-password'
.\mvnw.cmd spring-boot:run
```

Successful startup includes Flyway reporting schema version 4 and Tomcat listening on port 8081.

## 1. Verify public and protected routes

`GET http://localhost:8081/api/v1/health` must return `200 OK` without a token.

`GET http://localhost:8081/api/v1/me` must return `401 Unauthorized` without a token.

## 2. Login with the local demo customer

Create a Postman `POST` request:

```text
http://localhost:8081/api/v1/auth/login
```

Select **Body > raw > JSON** and send:

```json
{
  "email": "vishal@example.com",
  "password": "NexaDemo@123"
}
```

The response contains `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, and the authenticated user. Never log or commit real tokens.

## 3. Call protected APIs

Copy `accessToken`. In Postman select **Authorization > Bearer Token** and paste it for these requests:

```text
GET http://localhost:8081/api/v1/me
GET http://localhost:8081/api/v1/accounts
GET http://localhost:8081/api/v1/accounts/acc_01JDEMO000000000000001/balance
GET http://localhost:8081/api/v1/accounts/acc_01JDEMO000000000000001/transactions
```

The seeded demo account returns its profile, account, balance, and transaction history. A valid customer token cannot retrieve an account owned by another customer.

## 4. Refresh the session

Send the current refresh token to:

```text
POST http://localhost:8081/api/v1/auth/refresh
```

```json
{
  "refreshToken": "paste-current-refresh-token"
}
```

The response contains a new access token and a new refresh token. The old refresh token is immediately revoked and cannot be reused.

## 5. Logout

Send the newest refresh token to:

```text
POST http://localhost:8081/api/v1/auth/logout
```

```json
{
  "refreshToken": "paste-current-refresh-token"
}
```

The response is `204 No Content`. Logout revokes the refresh token. An already-issued access token remains valid until its short 15-minute expiry.

## 6. Register another customer

```text
POST http://localhost:8081/api/v1/auth/register
```

```json
{
  "fullName": "Test Customer",
  "email": "test.customer@example.com",
  "password": "Strong@123",
  "phoneNumber": "+91 90000 00000"
}
```

Registration creates a `CUSTOMER` user and profile and returns tokens immediately. A newly registered customer has no bank account until an account-opening workflow is implemented.
