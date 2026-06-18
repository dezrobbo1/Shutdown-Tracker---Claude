# Architecture

## High-Level System

Shutdown Tracker is planned as a monorepo with two frontend applications, backend services, shared packages, infrastructure definitions, and test fixtures.

## Modular Monolith First

The first backend should be a modular monolith rather than a distributed service mesh. Module boundaries should be explicit enough to support future extraction if the product grows.

## API Service

The API service will own request/response workflows, authentication, authorization, task events, problems, actions, evidence metadata, handover, audit events, reporting policies, and export approvals.

The repository now includes a minimal Spring Boot API scaffold in [services/api](../../services/api). It is placeholder-only: Actuator and `GET /api/version` exist, but no domain endpoints, import/export endpoints, database wiring, scheduler logic, or authorization behavior exists yet.

## Project Worker

The project worker will process uploaded Microsoft Project source files, run MPXJ parsing, capture warnings, create import batches, persist snapshots, and generate MSPDI/XML export artifacts.

The repository now includes a minimal Spring Boot worker scaffold in [services/project-worker](../../services/project-worker). It is placeholder-only: no MPXJ dependency, file parsing, background jobs, queue integration, database wiring, scheduler logic, or export generation exists yet.

## PostgreSQL

PostgreSQL is the system of record for relational operational data including users, roles, projects, imported snapshots, tasks, assignments, task events, problems, actions, handover, export batches, and audit events.

## Database Migrations

The migration foundation now lives in [infra/migrations](../../infra/migrations). It establishes SQL conventions and baseline PostgreSQL tables for projects, source files, import batches, immutable snapshots, imported Project entities, audit events, approval/export batches, and Critical Watchlist reporting.

The migrations prepare for a future Flyway-style runner. The repository includes local Docker-based validation scripts under [scripts/db](../../scripts/db) and a Maven/Spring Boot backend scaffold, but still does not include Flyway runtime dependencies, PostgreSQL runtime configuration, or an application migration runner.

## Audit Event Schema

Audit events are immutable and must be designed before domain tables are implemented. See [Audit Event Schema](audit-event-schema.md) for the baseline event identity, actor, target, correlation, idempotency, offline, evidence, snapshot, and export-batch fields.

## Object Storage

Object storage should hold uploaded source files, evidence files, and generated export files. The database should store metadata, ownership, access, and lifecycle state.

## PWA and Offline Model

The Mobile Field App should eventually use IndexedDB for queued local state, service workers and Cache API for offline-capable resources, idempotency keys for replay-safe operations, and visible sync states for user trust. Background Sync is progressive enhancement only.

## Import/Export Flow

1. Upload Microsoft Project source file.
2. Store the immutable source file.
3. Create an import batch.
4. Parse with MPXJ and capture warnings.
5. Persist snapshot data for tasks, resources, and assignments.
6. Track live execution state in Shutdown Tracker.
7. Preview export-eligible approved updates.
8. Approve export batch.
9. Generate MSPDI/XML artifact.
10. Manually reopen and verify in Microsoft Project.
