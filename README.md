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
- `services/api` contains an internal source-file storage abstraction with a local filesystem implementation for future upload workflows; no endpoint calls it yet.
- `services/api` contains local-profile JDBC services for synthetic review project bootstrap and source-file metadata persistence; no public endpoint calls them yet.
- `services/api` contains local-profile JDBC services for import batch creation, status updates, and parse summary persistence using the existing `import_batches` table and `import_batch_status` enum; no public endpoint calls them yet.
- `services/api` contains local-profile JDBC services for immutable project snapshot and imported Project entity persistence using the existing `project_snapshots`, `imported_tasks`, `imported_resources`, `imported_assignments`, and `imported_extended_attributes` tables; no public endpoint calls them yet.
- `services/api` contains a local-profile import review API for listing parsed snapshots, reviewing imported tasks/resources/assignments/extended attributes, and accepting or rejecting parsed snapshots with existing status values.
- `services/api` contains a local-profile task lineage review API for persisting concrete task-to-task lineage links between imported snapshots and accepting or rejecting suggested links with existing review-state values.
- `services/api` contains a local-profile export preview API for creating draft preview batches from explicit candidate lines and marking line eligibility for approved leaf-task progress/actual fields.
- `services/api` records local-profile audit events for import snapshot decisions, task lineage review decisions, and export preview creation using the existing `audit_events` table.
- `services/api` contains local-profile export batch approval/rejection endpoints and a generated-artifact metadata endpoint using existing export batch status values.
- `services/api` contains a disconnected Project parse handoff client and request builder for future worker integration; no public endpoint calls it yet and the API still does not parse Project files.
- `services/api` has a `review` profile for backend smoke deployment without PostgreSQL; it is limited to health, version, and validation-only source-file checks.
- `services/project-worker` contains a Spring Boot worker shell with a local-only MPXJ import summary spike, a shared-contract parse summary handoff service, and a synthetic MSPDI/XML export artifact spike.
- `packages/api-client` contains a TypeScript API client for the current import/export review surfaces.
- `packages/project-import-contract` contains shared Java request/response records for API-to-worker parse summary handoff.
- `fixtures/import-export/synthetic-basic-wbs/expected-import-summary.json` now provides a structured expected worker parse summary for the approved synthetic MSPDI fixture.
- `apps/console` contains a React/Vite scaffold wired to the shared API client surface while still rendering synthetic UI state by default.
- `apps/mobile-pwa` contains a React/Vite scaffold with static synthetic UI state only.
- Both services have `local` profile PostgreSQL/Flyway wiring that points Flyway to `filesystem:infra/migrations` when run from the repository root.
- No scheduler logic, task execution endpoints, upload/storage endpoint, queue integration, parser execution in the API, automatic lineage matching, live execution state, Project write-back, live frontend data fetching, mobile offline queue, secrets, binaries, seed data, or real Project files have been added yet.
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
  shared-types/
  validation/
  api-client/
  ui/
  config/
infra/
  docker/
  migrations/
  deployment/
fixtures/
  project-files/
  import-export/
  offline-sync/
```

## Next Steps

1. Add source-file upload orchestration from validation through storage metadata and import batch creation.
2. Add worker-backed export artifact generation handoff.
3. Add live console data fetching for import/export review once a review environment is seeded.
