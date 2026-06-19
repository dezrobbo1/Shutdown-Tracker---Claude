# Architecture

## High-Level System

Shutdown Tracker is planned as a monorepo with two frontend applications, backend services, shared packages, infrastructure definitions, and test fixtures.

## Modular Monolith First

The first backend should be a modular monolith rather than a distributed service mesh. Module boundaries should be explicit enough to support future extraction if the product grows.

## API Service

The API service will own request/response workflows, authentication, authorization, task events, problems, actions, evidence metadata, handover, audit events, reporting policies, and export approvals.

The repository now includes a minimal Spring Boot API scaffold in [services/api](../../services/api). Actuator, `GET /api/version`, validation-only source-file checks, source-file storage abstraction, export-artifact storage abstraction, source-file upload orchestration, review project bootstrap services, source-file metadata persistence services, import batch persistence services, worker parse-summary handoff, parse summary persistence, immutable project snapshot/imported entity persistence, a local-profile import review API, local-profile task lineage review persistence, a local-profile export preview model, export batch approval/generation/reopen/verification metadata orchestration, worker-backed export artifact handoff, and audit event writes for the first review and export lifecycle mutations exist. No task execution endpoints, scheduler logic, parser execution in the API, automatic lineage matching, automated Project verification workflow, or authorization behavior exists yet.

The repository also includes React/Vite scaffolds in [apps/console](../../apps/console) and [apps/mobile-pwa](../../apps/mobile-pwa). The console imports the shared TypeScript API client for current upload and import/export review operation wiring while still rendering synthetic scaffold data by default. The mobile PWA remains a static UI shell. The apps do not write execution state, implement offline queues, store files, parse Project files, generate exports, or write back to Microsoft Project.

## Project Worker

The project worker will process stored Microsoft Project source files for import batches, run MPXJ parsing, capture warnings, help persist snapshots, and later generate MSPDI/XML export artifacts.

The repository now includes a minimal Spring Boot worker scaffold in [services/project-worker](../../services/project-worker). The worker has a local-only MPXJ import summary spike, a shared-contract parse summary handoff service and endpoint, a synthetic MSPDI/XML export artifact spike, a shared-contract export artifact generation endpoint, and the `local` profile wires PostgreSQL and Flyway. It reads explicit local paths only when configured or handed one through a local contract, reports summary counts for imports, can write a local MSPDI/XML export artifact from explicit leaf-task candidates, and does not persist, run background jobs, integrate queues, calculate schedules, or write back to Microsoft Project.

## PostgreSQL

PostgreSQL is the system of record for relational operational data including users, roles, projects, imported snapshots, tasks, assignments, task events, problems, actions, handover, export batches, and audit events.

## Database Migrations

The migration foundation now lives in [infra/migrations](../../infra/migrations). It establishes SQL conventions and baseline PostgreSQL tables for projects, source files, import batches, immutable snapshots, imported Project entities, audit events, approval/export batches, and Critical Watchlist reporting.

The Spring Boot services include PostgreSQL JDBC and Flyway runtime dependencies. Their `local` profiles point Flyway to `filesystem:infra/migrations`, which is intended to be run from the repository root. The test profiles disable datasource and Flyway auto-configuration so simple context-load tests do not require PostgreSQL.

## Audit Event Schema

Audit events are immutable and must be designed before domain tables are implemented. See [Audit Event Schema](audit-event-schema.md) for the baseline event identity, actor, target, correlation, idempotency, offline, evidence, snapshot, and export-batch fields. The API now records local-profile audit rows for import snapshot accept/reject decisions, task lineage link review decisions, export preview creation, export batch approval/rejection, and generated artifact metadata recording using the existing `audit_events` table.

## Object Storage

Object storage should hold uploaded source files, evidence files, and generated export files. The database should store metadata, ownership, access, and lifecycle state.

The API now has internal source-file and export-artifact storage abstractions with local filesystem implementations for development and review wiring. They are not production object storage. The local-profile upload orchestration endpoint writes accepted source files through source-file storage, then stores the returned storage URI and content hash in metadata rather than raw file bytes. The export-artifact handoff endpoint prepares a storage-owned output target before calling the worker and verifies the worker response uses that reserved URI before recording artifact metadata.

The API also has local-profile JDBC services for synthetic review project bootstrap, `source_files` metadata persistence, `import_batches` creation/status updates, worker parse-summary handoff, parse summary persistence, immutable `project_snapshots`, imported Project entity rows, import review endpoints over already-persisted snapshots, task lineage review persistence over `task_lineage_links`, draft export preview persistence over `export_batches` and `export_batch_lines`, export batch lifecycle status transitions including manual Project reopen/verification metadata, worker-backed export artifact handoff, export artifact storage target preparation, and audit event writes for those upload/review/export mutations. The API can call explicitly configured worker endpoints for summary-only parsing and approved export artifact generation, but the default API clients are disconnected. These services use existing baseline tables and do not parse Project files in the API, enqueue worker jobs, automatically match task lineage, create live execution records, automate Microsoft Project, write directly to Microsoft Project, or create demo execution data.

## PWA and Offline Model

The Mobile Field App should eventually use IndexedDB for queued local state, service workers and Cache API for offline-capable resources, idempotency keys for replay-safe operations, and visible sync states for user trust. Background Sync is progressive enhancement only.

## Import/Export Flow

1. Upload Microsoft Project source file.
2. Store the immutable source file.
3. Ensure a project exists for the source file.
4. Persist source-file metadata.
5. Create an import batch.
6. Hand off the stored source file to the project worker.
7. Parse with MPXJ in the worker and capture warnings.
8. Persist parse summary metadata on the import batch.
9. Persist snapshot data for tasks, resources, and assignments.
10. Review parsed snapshot data and accept or reject the imported snapshot.
11. Review task lineage links between imported snapshots where a re-import needs continuity.
12. Track live execution state in Shutdown Tracker.
13. Preview export-eligible approved updates.
14. Approve export batch.
15. Hand approved export batch to the worker to generate an MSPDI/XML artifact.
16. Manually reopen and verify in Microsoft Project.
17. Record reopen/verification metadata in Shutdown Tracker without write-back.
