# Testing

## Strategy

Testing should protect the Microsoft Project boundary, imported snapshot integrity, auditability, permissions, offline safety, and export correctness.

## Import/Export Fixture Tests

- Keep fixture project files out of Git unless explicitly approved.
- Store fixture metadata and expected parse outcomes in text form where possible.
- Follow the [Import/Export Fixture Strategy](import-export-fixture-strategy.md) before adding any fixture metadata or future approved fixture files.
- Current MPXJ import spike tests use synthetic in-memory MPXJ objects and do not commit real Project files.
- The approved `synthetic-basic-wbs` MSPDI fixture has structured expected-output JSON that is compared against the worker parse summary response.
- The same fixture now has text-only export artifact expected-output JSON that is compared against worker-generated temporary MSPDI/XML summary and readback fields.
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
- Source-file validation tests cover missing multipart field handling, uppercase accepted extensions, validation-owned oversized responses, exception-scoped multipart error advice, and validation-style JSON for hard multipart failures where practical.
- Source-file storage abstraction tests use synthetic byte arrays and temporary directories only. They verify local storage URI creation, filename sanitisation, content hashing, and cleanup on failed writes without committing source files or binaries.
- Export-artifact storage abstraction tests use synthetic IDs and temporary directories only. They verify storage target preparation, local URI creation, XML artifact filename rules, and root containment without generating or committing artifacts.
- Source-file upload orchestration tests use synthetic byte arrays only. They verify accepted uploads call storage, create source-file metadata, create a pending import batch, and record `source_file_uploaded`, while rejected uploads stop before storage, persistence, import-batch creation, and audit writes.
- Review project bootstrap and source-file metadata service tests use fake repositories and synthetic metadata only. They do not require PostgreSQL, create import batches, parse Project files, or commit source files.
- Import batch service tests use fake repositories and synthetic IDs only. They verify `pending` creation, status updates, parse summary update mapping, and that alternate statuses such as `queued`, `running`, and `completed` are not accepted enum values.
- Import summary persistence tests use synthetic worker summary responses only. They verify summary-only count metadata and do not create snapshots, imported tasks, parser executions, queue jobs, source files, or real Project fixtures.
- Imported project persistence tests use fake repositories and synthetic entities only. They verify existing project snapshot statuses, entity-type values, snapshot-scoped persistence counts, and validation matching database constraints without parsing files or requiring PostgreSQL.
- Import review API tests use fake repositories and synthetic imported rows only. They verify project-scoped snapshot review reads, parsed-only accept/reject decisions, existing snapshot status values, and no-write-back response copy without requiring PostgreSQL or real Project files.
- Import review audit tests verify snapshot accept/reject decisions record audit event requests with existing snapshot statuses, imported snapshot references, and no-write-back metadata.
- Task lineage review tests use fake repositories and synthetic task IDs only. They verify concrete previous-task to current-task link creation, existing review-state values, suggested-only accept/reject decisions, audit event requests for create/accept/reject, and no schedule-calculation/write-back response copy without requiring PostgreSQL or real Project files.
- Export preview and batch lifecycle tests use fake repositories and synthetic task/source IDs only. They verify existing approval/export enum values, allowed progress/actual fields, draft preview creation, leaf-task eligibility, summary-task exclusion, unapproved-source exclusion, draft-only approve/reject transitions, generated artifact metadata recording for approved batches, manual Project reopen/verification metadata transitions, audit event requests for preview/approval/rejection/generated/reopen/verified transitions, and no-worker/no-write-back response copy without requiring PostgreSQL or real Project files.
- MSPDI/XML export artifact tests use synthetic leaf-task candidates and temporary output directories only. They verify generated XML can be read back through MPXJ, stable summary/readback fields match expected-output JSON, only allowed progress/actual fields are applied, summary-task candidates are rejected, and generated artifacts are not committed.
- Project parse handoff tests use synthetic IDs and metadata only. API tests verify request construction, pending-only parse-summary handoff, the default disconnected client, and the local-profile controller route; worker tests verify local URI resolution, the worker parse-summary endpoint, summary response mapping, and expected-output matching for the approved synthetic MSPDI fixture.
- Project export artifact handoff tests use synthetic export-preview rows and temporary paths only. API tests verify approved-only worker handoff, grouping eligible leaf-task lines, Microsoft Project task UID/ID propagation, storage-reserved artifact URI checks, generated metadata recording through the existing lifecycle path, the default disconnected client, and the local-profile controller route. Worker tests verify the export artifact endpoint and response mapping without requiring real Project files.
- Future worker queue/background-job implementation tests should follow the [Worker Handoff Queue Strategy](../architecture/worker-handoff-queue-strategy.md). They should prove retry idempotency, no duplicate import/export records, no new product enum values such as `queued`, `running`, or `completed`, API-owned workflow/audit updates, worker-owned file processing, no API-side Project parsing, and no Project write-back.
- The project worker has a worker-only MPXJ import summary spike, shared-contract parse summary handoff, and shared-contract MSPDI/XML export artifact handoff; no committed real Project files, persistence, background jobs, queue integration, production artifact storage, Project write-back, or scheduler logic exists yet.
- Migrations remain under [infra/migrations](../../infra/migrations); local migration validation remains under [scripts/db](../../scripts/db); Spring Boot `local` profiles point Flyway to `filesystem:infra/migrations`.
- The migration validation scripts apply SQL directly and do not create Flyway history; use a clean PostgreSQL volume when checking runtime Flyway migration through Spring Boot.

## CI Validation

The GitHub Actions workflow in [.github/workflows/ci.yml](../../.github/workflows/ci.yml) validates:

- The Maven backend test suite with Java 21.
- The React/Vite frontend tests and production builds with Node 22.
- SQL migrations `V001` through `V006` against a clean PostgreSQL database through Docker Compose and [scripts/db/validate-migrations.sh](../../scripts/db/validate-migrations.sh).

The workflow does not add MPXJ processing, app runtime behavior, secrets, seed data, or real Project files.

## Frontend Tests

- Run `npm test` from the repository root when Node and npm dependencies are available.
- The React/Vite console and mobile PWA scaffolds include render tests for planned navigation, status signals, and absence of schedule-authoring language.
- The shared TypeScript API client has unit tests for source-file upload multipart requests, import review paths, task lineage query parameters, export batch lifecycle, manual Project reopen/verification metadata requests, artifact handoff JSON requests, typed error handling, and the exposed review surface manifest.
- Console tests verify the shared upload and import/export review API client wiring renders without live backend calls, and separately cover opt-in live review data loading with fake import snapshot/export preview responses.
- Console live data tests verify read-only import/export review loading uses existing GET endpoints and does not create source-file uploads, import batches, parser calls, export approvals, generated artifacts, Project write-back, or execution-state writes.
- Run `npm run build` from the repository root to type-check and build both frontend scaffolds.
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
- Record the reopen and verification metadata in Shutdown Tracker without treating that as automated Project write-back.
