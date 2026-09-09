# Local Oracle setup

The Nexa API uses the local Oracle AI Database Free pluggable database, `FREEPDB1`. It uses its own `NEXA_APP` user; never configure the API with `SYSTEM`, `SYS`, or `PDBADMIN`.

## Create the application schema user

Connect as `SYSTEM` to `FREEPDB1`:

```text
sqlplus system@localhost:1521/FREEPDB1
```

Run the following, replacing only the quoted password locally. Do not commit or share the password.

```sql
CREATE USER NEXA_APP IDENTIFIED BY "choose-a-strong-local-password";

GRANT CREATE SESSION TO NEXA_APP;
GRANT CREATE TABLE TO NEXA_APP;
GRANT CREATE SEQUENCE TO NEXA_APP;
GRANT CREATE INDEX TO NEXA_APP;
GRANT CREATE TRIGGER TO NEXA_APP;
GRANT CREATE PROCEDURE TO NEXA_APP;
GRANT CREATE TYPE TO NEXA_APP;
GRANT CREATE VIEW TO NEXA_APP;
ALTER USER NEXA_APP QUOTA UNLIMITED ON USERS;
```

For a local development database, these privileges allow Flyway to create and evolve the Nexa schema. Production will use narrower separately managed migration and runtime users.

## Run the API

In a new PowerShell terminal:

```powershell
cd "D:\Nexa\apps\api"
$env:NEXA_DB_PASSWORD = "your-local-nexa-app-password"
.\mvnw.cmd spring-boot:run
```

Override connection details only when your local listener differs from the default:

```powershell
$env:NEXA_DB_URL = "jdbc:oracle:thin:@localhost:1521/FREEPDB1"
$env:NEXA_DB_USERNAME = "NEXA_APP"
```

On first start, Flyway runs the core migration and local demo seed migration. Verify that the history exists with:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```
