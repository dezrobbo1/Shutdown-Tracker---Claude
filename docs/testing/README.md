# Testing

## Strategy

Testing should protect the Microsoft Project boundary, imported snapshot integrity, auditability, permissions, offline safety, and export correctness.

## Import/Export Fixture Tests

- Keep fixture project files out of Git unless explicitly approved.
- Store fixture metadata and expected parse outcomes in text form where possible.
- Follow the [Import/Export Fixture Strategy](import-export-fixture-strategy.md) before adding any fixture metadata or future approved fixture files.
- Current MPXJ import spike tests use synthetic in-memory MPXJ objects and do not commit real Project files.
- Test MPXJ parsing for tasks, summary tasks, resources, assignments, calendars, baselines, and warnings.
- Test MSPDI/XML export artifacts with manual Microsoft Project reopen checks.

## Offline Sync Tests

- Verify queued operations are idempotent.
- Verify retry behavior is visible and recoverable.
- Verify Background Sync is not required for correctness.
- Verify conflicts and blocked sync states do not silently drop field updates.

## Permission and Audit Tests

- Test project-scoped RBAC.
- Test export approval permissions.
- Test evidence access permissions.
- Test immutable audit events for critical state changes.

## Migration Validation

- Migrations should apply in version order against a clean PostgreSQL database.
- Migrations are idempotent through migration tooling only; do not expect raw SQL files to be re-run manually.
- PRs that add or change migrations should run the local validation scripts in [scripts/db](../../scripts/db) where possible.
- CI runs migrations against a clean PostgreSQL database through Docker Compose.
- Future tests should verify indexes and constraints important to import/export, audit, approvals, export eligibility, task lineage, offline sync, and Critical WP reporting.
- Future tests should confirm the schema does not introduce scheduler-like fields such as critical path, float calculation, recovery scheduling, resource levelling, or automatic date movement.
- Successful local migration validation does not replace later repository, API, service, or end-to-end tests.

## Backend Tests

- The Maven backend scaffold contains Spring Boot context-load tests for `services/api` and `services/project-worker`.
- Run `mvn test` from the repository root when Maven and Java 21 are available.
- CI runs the Maven backend test suite on Java 21 for pushes to `main` and pull requests targeting `main`.
- Backend context-load tests use the `test` profile, which disables datasource and Flyway auto-configuration so PostgreSQL is not required for those tests.
- The API service has Actuator, `GET /api/version`, and a validation-only `POST /api/source-files/validate` endpoint. Source-file validation tests use synthetic byte arrays only and verify no file is stored or parsed.
- Source-file validation tests cover missing multipart field handling, uppercase accepted extensions, validation-owned oversized responses, and validation-style JSON for hard multipart failures where practical.
- The project worker has a worker-only MPXJ import summary spike; no committed Project files, persistence, background jobs, queue integration, export generation, or scheduler logic exists yet.
- Migrations remain under [infra/migrations](../../infra/migrations); local migration validation remains under [scripts/db](../../scripts/db); Spring Boot `local` profiles point Flyway to `filesystem:infra/migrations`.
- The migration validation scripts apply SQL directly and do not create Flyway history; use a clean PostgreSQL volume when checking runtime Flyway migration through Spring Boot.

## CI Validation

The GitHub Actions workflow in [.github/workflows/ci.yml](../../.github/workflows/ci.yml) validates:

- The Maven backend test suite with Java 21.
- SQL migrations `V001` through `V006` against a clean PostgreSQL database through Docker Compose and [scripts/db/validate-migrations.sh](../../scripts/db/validate-migrations.sh).

The workflow does not add MPXJ processing, frontend builds, secrets, seed data, or real Project files.

## Frontend Tests

- Unit test UI state and validation logic.
- Component test task lists, problem/action forms, evidence flows, and sync indicators.
- Keep schedule-authoring UI out of the MVP.

## Playwright E2E Tests

- Cover import review, task execution updates, problem/action creation, evidence metadata, handover, export preview, and approval workflows once implemented.

## Manual Microsoft Project Round-Trip Tests

- Import representative Microsoft Project files.
- Review parse warnings.
- Generate MSPDI/XML export artifacts.
- Reopen artifacts in Microsoft Project.
- Confirm only approved leaf-task progress/actual fields are eligible for export.
