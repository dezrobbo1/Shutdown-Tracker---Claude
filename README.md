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
- `services/api` contains local-profile JDBC services for import batch creation and status updates using the existing `import_batch_status` enum; no public endpoint calls them yet.
- `services/api` has a `review` profile for backend smoke deployment without PostgreSQL; it is limited to health, version, and validation-only source-file checks.
- `services/project-worker` contains a Spring Boot worker shell with a local-only MPXJ import summary spike.
- Both services have `local` profile PostgreSQL/Flyway wiring that points Flyway to `filesystem:infra/migrations` when run from the repository root.
- No domain logic, scheduler logic, task execution endpoints, upload/storage endpoint, parser handoff, import summary persistence, export generation, Project write-back, React app, mobile PWA, secrets, binaries, seed data, or real Project files have been added yet.
- Database migrations remain under `infra/migrations`.
- Local migration validation remains under `scripts/db`.
- CI validates the Maven backend test suite and SQL migrations against a clean PostgreSQL database through Docker Compose.

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

1. Add worker parse handoff while keeping MPXJ parsing in `services/project-worker`.
2. Persist import summary.
3. Add parser expected-output expansion.
4. Add export preview model.
5. Scaffold the console and mobile PWA in a follow-up PR.
