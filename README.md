# Nexa

Nexa is a conversational banking application with an Oracle JET web client and a Spring Boot API.

## Repository layout

```text
apps/
  api/   Spring Boot API (Java 21, Maven)
  web/   Oracle JET web application (Node.js)
docs/    Shared product, architecture, and handoff documentation
```

## Run locally

Start the API from `apps/api`:

```powershell
./mvnw.cmd spring-boot:run
```

Start the web application from `apps/web`:

```powershell
npm install
npm run dev
```

See `apps/api/README.md` and `docs/BACKEND_HANDOFF.md` for configuration and architecture details.
