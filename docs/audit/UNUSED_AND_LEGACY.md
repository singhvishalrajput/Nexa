# Used, unused, legacy and generated inventory — 25 September 2026

> Historical audit. On 27 September 2026, `apps/frontend` and `tools/demo` were removed from `purvak` after current reference checks. See the [current repository layout](../REPOSITORY_LAYOUT.md). The classifications below describe the original baseline, not the current directory tree.

This note is intentionally separate from [the flaws/improvement report](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/PROJECT_AUDIT.md). Classification is based on source/configuration/import references at commit 2d5ce83, not production telemetry. “Not part of the integrated runtime” does not automatically mean safe to delete.

The [full tracked-file inventory](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/FILE_INVENTORY.md) lists all 443 baseline files. Dependency caches, Git internals and generated binaries are not application source.

## Directory map

| Directory | Role and usage | Recommendation |
|---|---|---|
| apps/api | Active Java 17 / Spring Boot 4.1.1 modular monolith | Keep. It is one deployable API, despite historical microservice planning docs. |
| apps/api/src/main/java/com/nexa/api/beans | Entities, request/response models and typed query/workflow data | Active; some entity discovery is reflective, so import counting alone is insufficient. |
| apps/api/src/main/java/com/nexa/api/repository | JPA and JDBC persistence/projections, plus in-memory intent examples | Active. |
| apps/api/src/main/java/com/nexa/api/service | Banking, auth, loans, chat, knowledge, posting/query services | Active overall; unused aggregation method noted below. |
| apps/api/src/main/java/com/nexa/api/controller | Customer/admin/legacy REST surfaces | Active Spring-discovered routes. Older routes are not dead just because the UI no longer calls them. |
| apps/api/src/main/java/com/nexa/api/security, config, exep | Security, wiring, request/error handling | Active. “exep” is a naming cleanup issue, not an unused package. |
| apps/api/src/main/java/db/migration | Java Flyway migration V17 | Migration-time active; preserve. |
| apps/api/src/main/resources/db/migration | Historical and current schema migrations | Preserve every applied version. Old table references are expected. |
| apps/api/src/main/resources/db/local-migration | Local/demo seed migrations | Used by local profile, which is currently the default. Not dead. |
| apps/api/src/main/resources/knowledge | Versioned knowledge-base JSON | Active via classpath loading. |
| apps/api/src/test | Unit, MVC, H2 integration and optional Oracle/model tests | Development verification; keep. |
| apps/web | Integrated application used by root README and CI | Active frontend. |
| apps/web/src/components | Auth, landing, chat, navigation and shared design | Referenced by the entrypoint graph. |
| apps/web/src/features/banking | Customer/admin accounts, products, payments, loans and API adapters | Active; one unreachable OperationsPage branch noted below. |
| apps/web/src/services, hooks | Authentication, banking/chat clients, locale, navigation/voice | Active. |
| apps/web/src/styles | Theme, layouts and assets | All 16 CSS files and five image assets referenced. |
| apps/web/tests, scripts | Regression fixtures, lint and JET lifecycle hooks | Test/tooling usage; not browser runtime. Some hook scaffolding is redundant. |
| apps/frontend | Independently runnable earlier visual/demo prototype, JET 21 | Retained reference; not imported by integrated apps/web, which uses JET 20. Archive only after deciding whether the reference is still wanted. |
| docs, apps/api/docs | Current and historical architecture/runbook/audit notes | Useful but mixed in age; label historical snapshots and consolidate current guidance. |
| scripts | DB setup, inspection and cutover utilities/tests | Operational, not normal request runtime. Some actions can be destructive; preserve deliberate operator boundaries. |
| tools/demo | Fictional-data seeding/maintenance | Demo-only; stale against the salary-slip loan contract. Repair or archive rather than calling it production tooling. |
| .github/workflows | Backend/frontend CI | Active for rb-fe and configured path filters. |
| .git | Repository metadata/history | Essential infrastructure; never a cleanup candidate. |
| apps/api/.mvn and mvnw files | Maven bootstrap | Used; retain. |
| apps/api/target | Generated Maven build/test output (created during this audit) | Ignored and rebuildable; not dead source. |

## Confirmed or strong cleanup candidates

1. **Unreachable OperationsPage branch inside active code.**  
   [apps/web/src/features/banking/BankingApp.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/BankingApp.tsx:65) routes ADMIN sessions directly to AdminApp. The later Workspace operations branch requires ADMIN at line 111, so it is unreachable. [OperationsPage](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Payments.tsx:39) and associated coreAccounts/postTransaction/authenticatedCoreRequest functions support this dormant path. Payments.tsx also contains live functionality: do not delete the entire file. Decide whether to remove the branch or deliberately expose a reviewed admin workflow.

2. **Unused spending aggregation method/injection.**  
   [apps/api/src/main/java/com/nexa/api/service/SpendingQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/SpendingQueryService.java:23) has no runtime method caller found. ConversationInsights injects it but computes spending through TransactionQueryService instead. This class is still a Spring bean; “method not used” is more precise than “directory unused”. Fix the truncated spending totals first, potentially by reusing a corrected aggregate path, then remove any remaining dead injection.

3. **Orphan Oracle starter components in the reference frontend.**  
   Entrypoint traversal does not reach:
   - [apps/frontend/src/components/header.tsx](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/components/header.tsx)
   - [apps/frontend/src/components/footer.tsx](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/components/footer.tsx)
   - [apps/frontend/src/components/content/index.tsx](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/components/content/index.tsx)

   These are not the integrated application's current landing header/footer.

4. **Unreferenced starter assets in the reference frontend.**  
   No filename references found in its HTML/CSS/JS/TS:
   - [apps/frontend/src/styles/fonts/App_iconfont.woff](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/styles/fonts/App_iconfont.woff)
   - [apps/frontend/src/styles/images/avatar_24px.png](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/styles/images/avatar_24px.png)
   - [apps/frontend/src/styles/images/avatar_24px_2x.png](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/styles/images/avatar_24px_2x.png)
   - [apps/frontend/src/styles/images/JET-Favicon-Red-32x32.png](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/styles/images/JET-Favicon-Red-32x32.png)
   - [apps/frontend/src/styles/images/oracle_logo.svg](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend/src/styles/images/oracle_logo.svg)

5. **No-op and dangling build hooks.**  
   Both frontend scripts/hooks directories contain mostly starter hooks returning configuration or logging. Registered hooks may still be called by JET, so do not equate their lack of business logic with nonuse. Both hooks.json configurations reference a nonexistent before_release.js. Review registration and verify a clean release build before simplifying.

6. **Potential redundant direct build dependency: glob.**  
   No direct source/config consumer of the root glob package was found; JET tooling has its own resolution. This is a candidate, not confirmed safe removal. fs-extra/underscore are tooling dependencies, and yargs-parser appears in oraclejetconfig.json. Framework/transitive path mappings may be consumed indirectly; do not remove them using a simple import search.

## Keep despite misleading names or few imports

- [apps/web/src/features/banking/Showcase.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Showcase.tsx) and [apps/web/src/features/banking/demo-api.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/demo-api.ts) are active. Some actions simulate providers; internal payee transfers now post real Nexa money.
- [apps/api/src/main/java/com/nexa/api/beans/TransactionInstruction.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionInstruction.java) is scanned as a JPA entity and contributes to test schema/validation despite no ordinary service reference.
- UserEntity remains a used JDBC DTO even though USERS is no longer the runtime identity table.
- BankingProductRepository is used to project current accounts/transactions even though the old BANKING_PRODUCTS table is legacy.
- CardController/CreditCardsController and AccountController/AccountsController expose distinct API paths; similar names do not make them unused.
- main.js, RequireJS mappings, JET configuration, lifecycle hooks, CSS imports and classpath knowledge resources can be loaded without TypeScript imports.
- messenger/admin fixtures and source-loader/jet-node-stubs are test/manual-QA utilities. They are not production fallback code.
- Migration SQL referring to old tables must remain so fresh installs and Flyway history stay valid.
- Eighteen legacy database tables are conditional retirement candidates, not filesystem junk; see [the full table inventory and retirement safeguards](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/DATABASE_REVIEW.md).

## Generated files and hygiene

node_modules and built frontend web directories were absent at audit start. Ignore patterns are configuration, not evidence that such directories physically exist. The active web generated paths are ignored, but the prototype's generated web/theme folders are not covered by the same web-specific rules; building that prototype could produce repository noise.

Maven target was generated by verification and is already ignored. Lockfiles are tracked reproducibility inputs, not generated junk to remove. The ~5.8 MB of four active PNGs are optimization candidates, not unused assets; image compression or responsive variants should preserve the approved appearance.

## Stack actually used versus planned

**Used:** Java 17, Spring Boot 4.1.1, Spring MVC/Security/Validation/Actuator, JPA/Hibernate plus JdbcTemplate, Oracle JDBC/Flyway, JWT/BCrypt, H2/JUnit/Mockito for tests; Oracle JET 20.1 in apps/web, Preact/TypeScript/RequireJS, hash routing, authenticated fetch, browser speech APIs, locale dictionary; deterministic intent/entity parsing, local hash-vector matching, versioned JSON knowledge, optional Ollama routing (local profile default model qwen3.5:4b).

**Not found as current implemented infrastructure:** Kafka/event-broker workers, Redis, standalone microservice deployments, external bank/UPI/card/biller settlement connectors, real OTP/KYC provider integration, a scheduled payment execution worker, or a production vector database. Historical plans and inactive OUTBOX_EVENTS storage do not establish those capabilities.

## Safe cleanup sequence

First stabilize correctness and tests; then decide whether to archive the reference frontend; remove only the confirmed orphan components/assets and unreachable feature branch after consumer checks; verify clean release build/tests; simplify dependencies/hooks separately; update historical/current docs. Do not combine cleanup with database retirement or a frontend redesign.

No listed code, directory, asset, dependency or table was deleted during this audit.
