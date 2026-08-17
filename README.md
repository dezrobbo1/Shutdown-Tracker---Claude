# Shutdown Tracker

Shutdown Tracker is a shutdown, turnaround, outage, and major-overhaul execution-control platform. It is intended to help planners, coordinators, supervisors, field teams, inspectors, contractors, and managers understand and control live execution without turning the product into a scheduling engine.

## Product boundary

Microsoft Project remains the schedule authority. Shutdown Tracker is the live execution, review, evidence, handover, reporting, and controlled export system.

The controlled progress path is:

```text
field progress update
-> supervisor review
-> planner review
-> export eligibility
-> export preview
-> MSPDI/XML artifact generation
-> planner manually opens/checks in Microsoft Project
-> planner controls whether the master .mpp is saved
-> Shutdown Tracker records verification metadata and audit
```

Shutdown Tracker must not calculate CPM, critical path, or float; resource-level; optimise the schedule; automatically move dates; silently recalculate schedule logic; or silently write back to Microsoft Project. Critical Work Packages and Critical Watchlists are configurable execution-reporting constructs, not calculated critical-path features.

## Applications

- **Master Console** — desktop-oriented operations workspace for imported Project work, execution status, problems, actions, evidence, handover, review, Critical Watch, and controlled exports.
- **Mobile Field App** — field-oriented application for assigned work, progress updates, problems, actions, evidence, handover, and visible sync state. The current repository implementation is a React/Vite PWA scaffold.

## Current maturity

Implemented foundations include:

- Java 21 Spring Boot API and project-worker services;
- PostgreSQL and Flyway-compatible migrations;
- immutable Project source/snapshot persistence, including imported tasks, resources,
  assignments, and aliased custom fields stored from a parsed Project file;
- MPXJ import-summary and MSPDI/XML export-artifact worker boundaries;
- import review, task-lineage review, export-preview, approval, artifact handoff, and verification-metadata foundations;
- append-only audit foundations, with import, snapshot-acceptance and lineage decisions
  attributed to the acting user rather than recorded as system events;
- approval-record capture that gates export eligibility, and terminal failure recording for import and export batches;
- users, project-scoped roles, and enforced authorization resolved from stored membership;
- task execution state and the field-to-export progress review chain, with supervisor and
  planner review as separate decisions;
- problems, actions, evidence, and handover records;
- versioned Import Profiles and Operational Categories resolved from task fields, summary
  ancestry, and assigned-resource Groups, with mapping health on re-import;
- Critical Watchlists, Critical Work Packages, and Critical Update reporting, reachable over
  HTTP and surfaced in the console, with composing a package and reporting on one held as
  separate capabilities so a planner may build a package without filing reports on it;
- a Master Console on the baseline zones — Today, Tasks, Problems, Evidence, Exports — with
  import review, operational mapping, planner review and the export lifecycle gathered under
  Exports as addressable sections, each reading and writing through the API;
- a Today attention surface that reports what is awaiting review, what is blocking work, and
  what is waiting to be acknowledged, and links to the zone that owns each decision;
- a Mobile Field App on the baseline zones — My Work, Today, Problems, Evidence, Sync — with an
  offline execution queue: progress is stored on the device before it is sent, carries a
  device-generated idempotency key so a retry cannot double-report, shows sync state rather
  than implying delivery, and marks an unsent report on the work card itself;
- role-aware controls in both apps, checked against a capability map that a test compares
  against the server's own enum so the two cannot silently diverge;
- TypeScript API client and shared Java import/export handoff contracts;
- synthetic MSPDI regression fixtures and expected-output tests;
- repository tests that run the migrations and every SQL statement against a real
  PostgreSQL server;
- local migration and import/export smoke tooling.

Not production-complete yet:

- production authentication; authorization is enforced, but the actor still arrives
  through a gateway-trusted header rather than a validated token;
- Critical Update reporting in the field app; the console carries it, but a field user or
  contractor cannot yet file one from a device, and the offline queue covers task progress only;
- evidence binary upload in either app; evidence records and their per-task read exist and are
  wired in both, but nothing uploads a file, so a record without a storage location means the
  evidence itself is still outstanding;
- a project-wide evidence list; evidence is readable per task only;
- offline problem raising; raising a problem needs a connection, because problem creation has
  no server-side idempotency key and a queued retry could raise the same problem twice;
- assignment-scoped work lists; the field app currently lists the snapshot's leaf tasks
  rather than only the work assigned to the signed-in user;
- Saved Operational Views and global operational Scope;
- reporting policy cadences and generated reporting periods;
- production object storage;
- durable background-job/queue integration;
- full human Microsoft Project round-trip evidence;
- communications implementation.

Current implementation details belong in the app/service READMEs and source code rather than this root overview.

## Architecture

- Monorepo.
- Frontend: React and Vite.
- Current mobile implementation: mobile-first PWA scaffold.
- Backend: Java Spring Boot.
- Database: PostgreSQL.
- Microsoft Project parsing/export: MPXJ.
- Controlled export format: MSPDI/XML, not native `.mpp` writing.
- File/evidence architecture: provider-neutral storage abstractions, local filesystem implementations for development/review, production object storage later.
- Offline field direction: IndexedDB, service workers, Cache API, idempotency keys, and explicit sync states.
- Communications direction: entity-linked Discussion around structured records, not generic chat as the source of truth.

See [docs/architecture](docs/architecture/README.md) for durable architecture rules and focused strategy documents.

## Repository structure

```text
apps/
  console/
  mobile-pwa/
services/
  api/
  project-worker/
packages/
  api-client/
  project-import-contract/
  project-export-contract/
infra/
  docker/
  migrations/
scripts/
  db/
  review/
fixtures/
  import-export/
docs/
  concept/
  product/
  architecture/
  adr/
  research/
  testing/
  security/
  deployment/
```

## Documentation authority

Use documentation by purpose:

- [docs/concept](docs/concept/README.md) — high-level product definition and MVP boundary.
- [docs/product](docs/product/README.md) — current product behavior, roles, permissions, workflows, and UX rules.
- [docs/architecture](docs/architecture/README.md) — durable system structure and technical boundaries.
- [docs/adr](docs/adr/README.md) — architecture decision history.
- [docs/research](docs/research/README.md) — research evidence, source quality, and provenance.
- [docs/testing](docs/testing/README.md) — durable test policy and verification procedures.
- GitHub pull requests and commit history — implementation chronology.

`AGENTS.md` contains repository-specific implementation guidance for coding agents.

## Development and validation

From the repository root:

```text
mvn test
npm test
npm run build
```

`mvn test` includes repository tests that start a real PostgreSQL server and apply
`infra/migrations` to it. Docker is not required: the server binary is unpacked from a
Maven artifact, so the same tests run locally and in CI.

Static migration linting (naming and content checks only):

```text
./scripts/db/validate-migrations.sh
```

or on Windows PowerShell:

```text
.\scripts\db\validate-migrations.ps1
```

The repository must not contain real customer Project files, generated exports, evidence uploads, secrets, local databases, or other operational artifacts unless an explicit fixture policy permits a fully synthetic test asset.
