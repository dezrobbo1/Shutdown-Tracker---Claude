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
- immutable Project source/snapshot and imported-entity persistence foundations;
- MPXJ import-summary and MSPDI/XML export-artifact worker boundaries;
- import review, task-lineage review, export-preview, approval, artifact handoff, and verification-metadata foundations;
- append-only audit foundations;
- approval-record capture that gates export eligibility, and terminal failure recording for import and export batches;
- React/Vite Master Console and Mobile Field App visual shells;
- TypeScript API client and shared Java import/export handoff contracts;
- synthetic MSPDI regression fixtures and expected-output tests;
- local migration and import/export smoke tooling.

Not production-complete yet:

- live task-execution and progress-write workflows;
- imported-entity persistence from a parsed Project file, which waits on the worker parsed-entities contract described in [docs/architecture/worker-handoff-queue-strategy.md](docs/architecture/worker-handoff-queue-strategy.md);
- supervisor/planner production review workflows;
- production authentication and authorization enforcement;
- mobile offline execution queue;
- production object storage;
- durable background-job/queue integration;
- full human Microsoft Project round-trip evidence;
- communications implementation;
- Project Operational Mapping and configurable operational scope.

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
