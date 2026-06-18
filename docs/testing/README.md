# Testing

## Strategy

Testing should protect the Microsoft Project boundary, imported snapshot integrity, auditability, permissions, offline safety, and export correctness.

## Import/Export Fixture Tests

- Keep fixture project files out of Git unless explicitly approved.
- Store fixture metadata and expected parse outcomes in text form where possible.
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
- Future CI should run migrations against a clean PostgreSQL database.
- Future tests should verify indexes and constraints important to import/export, audit, approvals, export eligibility, task lineage, offline sync, and Critical WP reporting.
- Future tests should confirm the schema does not introduce scheduler-like fields such as critical path, float calculation, recovery scheduling, resource levelling, or automatic date movement.
- Successful local migration validation does not replace later repository, API, service, or end-to-end tests.

## Backend Tests

- The Maven backend scaffold contains Spring Boot context-load tests for `services/api` and `services/project-worker`.
- Run `mvn test` from the repository root when Maven and Java 21 are available.
- The API service is placeholder-only, with Actuator plus `GET /api/version`; no task, import, export, or domain endpoints exist yet.
- The project worker is placeholder-only; no MPXJ parsing, background jobs, queue integration, export generation, or scheduler logic exists yet.
- Migrations remain under [infra/migrations](../../infra/migrations); local migration validation remains under [scripts/db](../../scripts/db).

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
