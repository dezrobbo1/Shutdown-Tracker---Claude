# Architecture

Shutdown Tracker is a live shutdown execution-control system. Microsoft Project remains the schedule authority and final master-file control point; Shutdown Tracker owns execution truth, review, evidence, handover, reporting, controlled export preparation, verification metadata, and audit.

This document describes durable architecture boundaries. Exact endpoint/test inventories belong in source code and service/app READMEs rather than here.

## System shape

The repository is a monorepo with:

- `apps/console`: Master Console frontend.
- `apps/mobile-pwa`: current Mobile Field App PWA implementation.
- `services/api`: Spring Boot API and persistence/orchestration boundary.
- `services/project-worker`: Spring Boot worker for Microsoft Project file processing through MPXJ.
- `packages/api-client`: shared TypeScript API client.
- `packages/project-import-contract`: API-to-worker import handoff contract.
- `packages/project-export-contract`: API-to-worker export handoff contract.
- `infra/migrations`: PostgreSQL schema migrations.
- `fixtures/import-export`: approved synthetic import/export regression assets.

The backend follows a modular-monolith-first approach. Explicit module and worker boundaries should remain clear enough to extract later only if operational scale requires it.

## Core domain boundaries

The architecture is expected to support:

- identity and project-scoped authorization;
- projects, source files, import batches, and immutable Project snapshots;
- imported tasks, resources, assignments, and extended attributes;
- task lineage across snapshots;
- execution state and structured task progress;
- supervisor and planner review;
- problems and actions;
- evidence metadata;
- handover;
- Critical Watchlists, Critical Work Packages, reporting policies, and Critical Updates;
- entity-linked communications/discussion;
- approval and export batches;
- manual Microsoft Project verification metadata;
- audit events;
- mobile offline/sync workflows.

## API and worker ownership

The API owns application workflows, authorization decisions, operational state, persistence orchestration, review/approval decisions, audit, and user-facing API contracts.

The project worker owns Microsoft Project file processing. MPXJ parsing and MSPDI/XML artifact generation belong in the worker rather than the API.

Current API-to-worker handoffs are explicit and opt-in. The future asynchronous direction is documented in [Worker Handoff Queue Strategy](worker-handoff-queue-strategy.md). Product workflow statuses must not be replaced with transport/job statuses merely because a queue is introduced.

## Data and storage

PostgreSQL is the relational system of record for application state. Imported Microsoft Project data is stored as immutable snapshot facts; operational records and audit history are app-owned.

The v1 model uses relational domain records plus append-only audit events rather than full event sourcing.

Versioned migrations live in [`infra/migrations`](../../infra/migrations). See the migration README and validation scripts under [`scripts/db`](../../scripts/db).

Source files, generated export artifacts, and evidence should use storage abstractions with metadata held in PostgreSQL. Local filesystem implementations are development/review facilities only. Production provider guidance is in [Object Storage Provider Strategy](object-storage-provider-strategy.md).

## Import and export lifecycle

The durable handoff is:

1. Accept and store an immutable Microsoft Project source file.
2. Create an import batch.
3. Process the source file in the project worker with MPXJ.
4. Persist parse metadata and immutable snapshot entities.
5. Review/accept the imported snapshot and resolve lineage where required.
6. Track execution in Shutdown Tracker.
7. Capture structured task progress.
8. Supervisor reviews operational validity.
9. Planner reviews export eligibility.
10. Materialise an export preview from approved leaf-task candidates.
11. Approve the export batch.
12. Generate an MSPDI/XML artifact through the worker.
13. Planner manually opens/checks the artifact in Microsoft Project.
14. Planner controls whether the master `.mpp` is saved.
15. Shutdown Tracker records verification metadata and audit.

No step authorises hidden Project write-back or native `.mpp` generation.

## Audit

Audit events are append-only application records for material workflow and authority changes. See [Audit Event Schema](audit-event-schema.md) for the baseline schema and event requirements.

Product documents may define additional event families as capabilities are implemented; those product rules do not imply that the corresponding backend functionality already exists.

## Mobile and offline model

The field application must make queued/submitted/synced state explicit. Offline direction includes IndexedDB, service workers, Cache API, idempotency keys, replay-safe mutations, and recoverable conflict states. Background Sync is progressive enhancement only.

See [Offline Audit and Sync Rules](../product/offline-audit-sync-rules.md). Key rule: queued is not submitted.

## Communications

Communications are entity-linked operational context, not a generic chat system of record. Discussion may attach to tasks, problems, actions, evidence, handover, export review, verification, or Critical Watch objects.

A comment is not progress, a blocker, an action, evidence, or handover unless it is promoted or linked into the corresponding structured record. See [Communications Layer](../product/communications-layer.md).

## Frontend and UX guardrails

- Master Console top-level zones remain Today, Tasks, Problems, Evidence, Exports unless current product/ADR sources explicitly change them.
- Mobile Field App top-level zones remain My Work, Today, Problems, Evidence, Sync unless current product/ADR sources explicitly change them.
- Do not introduce scheduler/Gantt/critical-path ownership through the frontend.
- Review, verification, and communications should remain scoped operational surfaces rather than uncontrolled top-level navigation growth.
- Use [Frontend Visual Review Scope](../product/frontend-visual-review-scope.md), [UX Anti-Slop Rules](../product/ux-anti-slop-rules.md), and [Design Language and Status Semantics](../product/design-language-and-status-semantics.md) for current UI guidance.

## Non-negotiable Microsoft Project boundary

Shutdown Tracker must not calculate CPM, critical path, or float; resource-level; optimise schedules; automatically move dates; silently alter dependencies, constraints, calendars, or baselines; or imply that an internal approval updates the master Project file.

Critical Watch is an execution-reporting construct, not a critical-path calculation.
