# Shutdown Tracker

Shutdown Tracker is a shutdown, turnaround, and construction live execution tracking platform. It is intended to help control teams, planners, coordinators, supervisors, package owners, field crews, inspectors, contractors, and managers understand what is happening during execution without turning the product into a scheduling engine.

## Project Overview

The platform has two main applications:

- Master Console: a desktop-oriented operations console for imported Microsoft Project work packages, summary tasks, child tasks, problems, delays, actions, evidence, handover notes, reporting state, and export approval state.
- Mobile Field App: a mobile-first PWA for field supervisors, leading hands, contractors, inspectors, and execution crews to view assigned work, submit updates, start, pause, resume, block, and complete work, log problems, manage actions, attach evidence, and submit handover notes.

## Product Boundary

Microsoft Project remains the schedule authority. Shutdown Tracker is the live execution and reporting authority.

Shutdown Tracker imports Microsoft Project schedule snapshots and may export reviewed, approved batches back to Microsoft Project. It does not live-feed Microsoft Project and does not silently modify the schedule.

## Do Not Build Scheduler

This repository must not implement CPM, critical-path calculation, resource levelling, recovery scheduling, automatic date movement, hidden schedule recalculation, dependency-map scheduling, or schedule optimization. Critical Work Packages and Critical Watchlists are configurable reporting constructs, not calculated critical-path features.

## Current Status

Repository scaffold, product/architecture/security docs, baseline SQL migrations, local migration validation scripts, a minimal Spring Boot backend scaffold, PostgreSQL/Flyway runtime wiring, and GitHub Actions CI validation now exist.

The backend scaffold is placeholder-only:

- `services/api` contains a Spring Boot API shell with Actuator and `GET /api/version`.
- `services/api` also contains `POST /api/source-files/validate`, a validation-only multipart placeholder that stores nothing and parses nothing.
- `services/api` contains an internal source-file storage abstraction with a local filesystem implementation for upload workflows.
- `services/api` contains an internal export-artifact storage abstraction with a local filesystem implementation for worker-generated MSPDI/XML artifacts.
- `services/api` contains a local-profile source-file upload orchestration endpoint that validates, stores accepted bytes, creates `source_files` metadata, creates a pending import batch, and records an audit event.
- `services/api` contains local-profile JDBC services for synthetic review project bootstrap and source-file metadata persistence.
- `services/api` contains local-profile JDBC services for import batch creation, status updates, and parse summary persistence using the existing `import_batches` table and `import_batch_status` enum.
- `services/api` contains local-profile JDBC services for immutable project snapshot and imported Project entity persistence using the existing `project_snapshots`, `imported_tasks`, `imported_resources`, `imported_assignments`, and `imported_extended_attributes` tables; no public endpoint calls them yet.
- `services/api` contains a local-profile import review API for listing parsed snapshots, reviewing imported tasks/resources/assignments/extended attributes, and accepting or rejecting parsed snapshots with existing status values.
- `services/api` contains a local-profile task lineage review API for persisting concrete task-to-task lineage links between imported snapshots and accepting or rejecting suggested links with existing review-state values.
- `services/api` contains a local-profile export preview API for creating draft preview batches from explicit candidate lines and marking line eligibility for approved leaf-task progress/actual fields.
- `services/api` records local-profile audit events for import snapshot decisions, task lineage review decisions, and export preview creation using the existing `audit_events` table.
- `services/api` contains local-profile export batch approval/rejection endpoints and a generated-artifact metadata endpoint using existing export batch status values.
- `services/api` contains local-profile Project reopen/verification metadata endpoints that use existing export batch status values and do not automate Microsoft Project.
- `services/api` contains a local-profile worker-backed export artifact handoff endpoint for approved export batches. It prepares an export-artifact storage target, calls an explicitly configured project-worker endpoint, verifies the returned URI matches the reserved target, records returned artifact URI/hash metadata, and does not generate files in the API.
- `services/api` contains a local-profile import-batch parse-summary handoff endpoint. It can call an explicitly configured project-worker endpoint, record summary-only parser metadata on the import batch, and still does not parse Project files in the API.
- `docs/architecture/worker-handoff-queue-strategy.md` documents the future queue/background-job strategy for import/export handoffs while keeping existing product status enums and Microsoft Project boundaries intact.
- `services/api` has a `review` profile for backend smoke deployment without PostgreSQL; it is limited to health, version, and validation-only source-file checks.
- `services/project-worker` contains a Spring Boot worker shell with a local-only MPXJ import summary spike, a shared-contract parse summary handoff service and endpoint, a synthetic MSPDI/XML export artifact spike, and a shared-contract export artifact generation endpoint.
- `packages/api-client` contains a TypeScript API client for source-file upload and the current import/export review surfaces.
- `packages/project-import-contract` contains shared Java request/response records for API-to-worker parse summary handoff.
- `packages/project-export-contract` contains shared Java request/response records for API-to-worker MSPDI/XML export artifact handoff.
- `fixtures/import-export/synthetic-basic-wbs/expected-import-summary.json` now provides a structured expected worker parse summary for the approved synthetic MSPDI fixture.
- `fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json` now provides a text-only expected worker export artifact summary for synthetic MSPDI/XML generation tests.
- `docs/testing/manual-microsoft-project-round-trip-evidence.md` now defines the text-only evidence format for future manual Microsoft Project reopen checks without committing generated artifacts or screenshots.
- `docs/architecture/object-storage-provider-strategy.md` now defines production object-store provider selection and configuration guidance for source files, generated export artifacts, and future evidence files.
- `docs/testing/seeded-review-demo-data-strategy.md` now defines future local/review seeded data rules without adding seed data.
- `scripts/review/source-import-export-smoke.ps1` now provides a guarded local source/import/export smoke script using synthetic fixture input by default.
- `apps/console` contains a React/Vite scaffold wired to the shared API client surface. It renders synthetic UI state by default and can opt into read-only live import/export review data fetching with explicit Vite environment variables.
- `apps/mobile-pwa` contains a React/Vite scaffold with static synthetic UI state only.
- Both services have `local` profile PostgreSQL/Flyway wiring that points Flyway to `filesystem:infra/migrations` when run from the repository root.
- No scheduler logic, task execution endpoints, queue integration, parser execution in the API, automatic lineage matching, live execution state, automated Project verification, Project write-back, live frontend write workflows, mobile offline queue, production object-store provider implementation, secrets, binaries, actual seed data, or real Project files have been added yet.
- Database migrations remain under `infra/migrations`.
- Local migration validation remains under `scripts/db`.
- CI validates the Maven backend test suite, React/Vite frontend test/build, and SQL migrations against a clean PostgreSQL database through Docker Compose.

## Architecture Direction

- Monorepo.
- Frontend: React and Vite.
- Mobile: mobile-first PWA.
- Backend: Kotlin or Java Spring Boot.
- Database: PostgreSQL.
- Microsoft Project import/export: MPXJ.
- Export format: MSPDI/XML, not native MPP writing.
- Storage: object storage for uploaded evidence, source files, and generated export files.
- Offline mobile workflow: IndexedDB, service workers, Cache API, idempotency keys, visible sync state, and Background Sync as progressive enhancement only.

## Repo Structure

```text
docs/
  concept/
  research/
  adr/
  architecture/
  product/
  testing/
  security/
apps/
  console/
  mobile-pwa/
services/
  api/
  project-worker/
packages/
  project-import-contract/
  project-export-contract/
  shared-types/
  validation/
  api-client/
  ui/
  config/
infra/
  docker/
  migrations/
  deployment/
scripts/
  db/
  review/
fixtures/
  project-files/
  import-export/
  offline-sync/
```

## Next Steps

1. Add review/demo dataset manifest format.
2. Add provider-neutral object-storage config properties and tests after a provider decision.
3. Add local-only seeded dataset implementation after the manifest format is reviewed.
