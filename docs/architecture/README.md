# Architecture

## High-Level System

Shutdown Tracker is planned as a monorepo with two frontend applications, backend services, shared packages, infrastructure definitions, and test fixtures.

The product is a live shutdown execution tracker, not a scheduler. Microsoft Project remains the schedule authority and final master-file control point. Shutdown Tracker owns execution truth, review, evidence, handover, export preparation, verification metadata, and audit.

## Modular Monolith First

The first backend should be a modular monolith rather than a distributed service mesh. Module boundaries should be explicit enough to support future extraction if the product grows.

The core backend module boundaries are expected to include:

- identity and access;
- projects and imported snapshots;
- imported WBS, tasks, resources, and assignments;
- task execution and task progress review;
- problems and actions;
- evidence metadata;
- handover;
- Critical Watchlists and reporting policies;
- communications / entity-linked Discussion;
- approval and export batches;
- manual Microsoft Project verification metadata;
- audit events;
- offline sync queue.

## API Service

The API service will own request/response workflows, authentication, authorization, task events, task progress submissions, supervisor review, planner review candidates, problems, actions, evidence metadata, handover, communications records, audit events, reporting policies, and export approvals.

The repository now includes a minimal Spring Boot API scaffold in [services/api](../../services/api). Actuator, `GET /api/version`, validation-only source-file checks, source-file storage abstraction, export-artifact storage abstraction, source-file upload orchestration, review project bootstrap services, source-file metadata persistence services, import batch persistence services, worker parse-summary handoff, parse summary persistence, immutable project snapshot/imported entity persistence, a local-profile import review API, local-profile task lineage review persistence, a local-profile export preview model, export batch approval/generation/reopen/verification metadata orchestration, worker-backed export artifact handoff, and audit event writes for the first review and export lifecycle mutations exist. No task execution endpoints, task progress write APIs, supervisor review APIs, planner progress review APIs, communications APIs, production offline sync, scheduler logic, parser execution in the API, automatic lineage matching, automated Project verification workflow, or authorization behavior exists yet.

The repository also includes React/Vite scaffolds in [apps/console](../../apps/console) and [apps/mobile-pwa](../../apps/mobile-pwa). The console imports the shared TypeScript API client for current upload and import/export review operation wiring, renders synthetic scaffold data by default, and can opt into read-only live import/export review data fetching when an API base URL and project id are explicitly configured. The mobile PWA remains a static UI shell. The apps do not write execution state, implement offline queues, store files, parse Project files, generate exports, or write back to Microsoft Project.

The current Task Progress Review frontend surfaces are static/synthetic visual review surfaces only. They are not final IA, backend API contracts, or production route structure. See [Frontend Visual Review Scope](../product/frontend-visual-review-scope.md), [UX Anti-Slop Rules](../product/ux-anti-slop-rules.md), and [Task Progress Review and Export Approval](../product/task-progress-review-export-approval.md).

Seeded local/review data guidance is documented in [Seeded Review and Demo Data Strategy](../testing/seeded-review-demo-data-strategy.md). No seeded dataset implementation, migration-driven seed data, production tenant data, or real Project data has been added.

## Project Worker

The project worker will process stored Microsoft Project source files for import batches, run MPXJ parsing, capture warnings, help persist snapshots, and later generate MSPDI/XML export artifacts.

The repository now includes a minimal Spring Boot worker scaffold in [services/project-worker](../../services/project-worker). The worker has a local-only MPXJ import summary spike, a shared-contract parse summary handoff service and endpoint, a synthetic MSPDI/XML export artifact spike, a shared-contract export artifact generation endpoint, and the `local` profile wires PostgreSQL and Flyway. It reads explicit local paths only when configured or handed one through a local contract, reports summary counts for imports, can write a local MSPDI/XML export artifact from explicit leaf-task candidates, and does not persist, run background jobs, integrate queues, calculate schedules, or write back to Microsoft Project.

## PostgreSQL

PostgreSQL is the system of record for relational operational data including users, roles, projects, imported snapshots, tasks, assignments, task events, task progress submissions, progress review decisions, export candidates, problems, actions, handover, communications records, export batches, and audit events.

The v1 model should use relational domain records plus append-only audit events. Full event sourcing remains later/experimental.

## Database Migrations

The migration foundation now lives in [infra/migrations](../../infra/migrations). It establishes SQL conventions and baseline PostgreSQL tables for projects, source files, import batches, immutable snapshots, imported Project entities, audit events, approval-neutral authoritative export candidates, candidate-bound approval/export batches, and Critical Watchlist reporting.

The Spring Boot services include PostgreSQL JDBC and Flyway runtime dependencies. Their `local` profiles point Flyway to `filesystem:infra/migrations`, which is intended to be run from the repository root. The test profiles disable datasource and Flyway auto-configuration so simple context-load tests do not require PostgreSQL.

## Audit Event Schema

Audit events are immutable and must be designed before domain tables are implemented. See [Audit Event Schema](audit-event-schema.md) for the baseline event identity, actor, target, correlation, idempotency, offline, evidence, communications, task progress, snapshot, and export-batch fields. The API now records local-profile audit rows for import snapshot accept/reject decisions, task lineage link review decisions, authoritative candidate creation, candidate approval events, export preview creation, export batch approval/rejection, and generated artifact metadata recording using the existing `audit_events` table.

Task Progress Review and Communications Layer product docs define future event families that are not implemented yet.

## Object Storage

Object storage should hold uploaded source files, evidence files, and generated export files. The database should store metadata, ownership, access, lifecycle state, and audit linkage.

Production provider selection and configuration guidance is documented in [Object Storage Provider Strategy](object-storage-provider-strategy.md). No production object-store provider, SDK dependency, bucket/container, credential, or deployment secret has been added yet.

The API now has internal source-file and export-artifact storage abstractions with local filesystem implementations for development and review wiring. They are not production object storage. The local-profile upload orchestration endpoint writes accepted source files through source-file storage, then stores the returned storage URI and content hash in metadata rather than raw file bytes. The export-artifact handoff endpoint prepares a storage-owned output target before calling the worker and verifies the worker response uses that reserved URI before recording artifact metadata.

The API also has local-profile JDBC services for synthetic review project bootstrap, `source_files` metadata persistence, `import_batches` creation/status updates, worker parse-summary handoff, parse summary persistence, immutable `project_snapshots`, imported Project entity rows, import review endpoints over already-persisted snapshots, task lineage review persistence over `task_lineage_links`, approval-neutral authoritative candidate creation, separate candidate-bound approval events, candidate-ID-only draft export previews over `export_batches` and `export_batch_lines`, export batch lifecycle status transitions including manual Project reopen/verification metadata, worker-backed export artifact handoff, export artifact storage target preparation, and audit event writes for those upload/review/export mutations. The API can call explicitly configured worker endpoints for summary-only parsing and approved export artifact generation, but the default API clients are disconnected. The future async handoff direction is documented in [Worker Handoff Queue Strategy](worker-handoff-queue-strategy.md). These services use existing baseline tables and do not parse Project files in the API, enqueue worker jobs, automatically match task lineage, create live execution records, automate Microsoft Project, write directly to Microsoft Project, or create seeded demo execution data.

## PWA and Offline Model

The Mobile Field App should eventually use IndexedDB for queued local state, service workers and Cache API for offline-capable resources, idempotency keys for replay-safe operations, and visible sync states for user trust. Background Sync is progressive enhancement only.

Offline copy and state rules are defined in [Offline Audit and Sync Rules](../product/offline-audit-sync-rules.md). Key rule: queued is not submitted.

## Communications Model

The communications layer is not generic chat. Future communication records should be entity-linked Discussion attached to tasks, problems, actions, evidence, handover, export preview lines, export batches, Project verification steps, or Critical Watch reporting objects.

A comment is not task progress, a blocker, an action, evidence, or handover unless it is promoted or linked into that structured object. See [Communications Layer](../product/communications-layer.md).

## Import/Export Flow

1. Upload Microsoft Project source file.
2. Store the immutable source file.
3. Ensure a project exists for the source file.
4. Persist source-file metadata.
5. Create an import batch.
6. Hand off the stored source file to the project worker through the current explicit HTTP boundary, later wrapped by the queue strategy.
7. Parse with MPXJ in the worker and capture warnings.
8. Persist parse summary metadata on the import batch.
9. Persist snapshot data for tasks, resources, and assignments.
10. Review parsed snapshot data and accept or reject the imported snapshot.
11. Review task lineage links between imported snapshots where a re-import needs continuity.
12. Track live execution state in Shutdown Tracker.
13. Capture structured task progress.
14. Supervisor reviews progress for operational validity.
15. Planner reviews supervisor-accepted leaf-task progress/actual fields for export eligibility.
16. Preview export-eligible approved updates.
17. Approve export batch.
18. Hand approved export batch to the worker through the current explicit HTTP boundary, later wrapped by the queue strategy, to generate an MSPDI/XML artifact.
19. Manually reopen and verify in Microsoft Project.
20. Record reopen/verification metadata in Shutdown Tracker without write-back.

## Frontend and UX Guardrails

- Master Console top-level zones remain Today, Tasks, Problems, Evidence, Exports.
- Mobile Field App top-level zones remain My Work, Today, Problems, Evidence, Sync.
- Do not add top-level Chat, Supervisor Review, Planner Review, Verification, Dashboard, Reports, or Gantt without product and ADR/source-doc approval.
- Use saved views, drill-down pages, detail drawers, and scoped sections rather than a dashboard/card wall.
- Keep visual-only surfaces clearly labelled and disabled until APIs exist.
- Use [Design Language and Status Semantics](../product/design-language-and-status-semantics.md) for status meaning and copy.
