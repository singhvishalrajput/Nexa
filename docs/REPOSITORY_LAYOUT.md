# Repository layout

Updated 27 September 2026 after the unused-folder cleanup on `purvak`.

## Retained directories

| Directory | Purpose |
| --- | --- |
| `apps/api` | Active Spring Boot backend, authentication, banking services, Oracle migrations and backend tests. |
| `apps/web` | Active Oracle JET frontend, its own artwork/styles, build configuration and frontend tests. |
| `scripts` | Database provisioning, local configuration, inspection, migration utilities and their checks. |
| `docs` and `apps/api/docs` | Setup guides, feature documentation and dated audit records. |
| `.github/workflows` | Backend and frontend CI definitions. |

Tests, migrations, Maven wrapper files, build hooks and static assets are used by development tools or loaded indirectly. They remain with their applications.

## Removed directories

| Directory | Reason |
| --- | --- |
| `apps/frontend` | Earlier standalone visual prototype. The integrated app builds and runs from `apps/web`, which already contains its required artwork and styles. |
| `tools/demo` | Retired three-customer demo seeding and maintenance utilities. Current account opening, loan applications and bank funding use the application workflows and current setup scripts. |

The cleanup removes 60 prototype files and 3 demo-tool files. Reference checks found no consumers of these directories in the active application, build configuration, tests, CI or setup scripts. Application source and database migrations are unchanged.

The retired [frontend prototype](https://github.com/singhvishalrajput/Nexa/tree/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/apps/frontend) and [demo tools](https://github.com/singhvishalrajput/Nexa/tree/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/tools/demo) remain available in Git history. The files in `docs/audit` describe their original dated baseline; use this page for the current folder layout.

## Local files outside the branch

`.tools`, `node_modules`, Maven `target`, generated `apps/web/web`, private `.env` files and database backups are ignored by Git. They remain local; this branch cleanup preserves them.
