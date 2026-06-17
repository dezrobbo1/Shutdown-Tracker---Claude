# Database Migrations

This folder contains PostgreSQL SQL migrations for Shutdown Tracker.

The migration foundation establishes the first system-of-record schema for project imports, immutable snapshots, imported Microsoft Project entities, audit events, controlled approvals/exports, and Critical Watchlist reporting.

## Target

- Database: PostgreSQL.
- Migration style: SQL files compatible with a future Flyway-style runner.
- Application stack: not scaffolded in this PR.
- Seed data: not allowed in baseline migrations.

## Naming Convention

Use Flyway-compatible versioned migration names:

```text
V001__short_description.sql
V002__next_description.sql
```

Version numbers must be monotonically increasing. Do not rename or rewrite applied migrations after they have been shared.

## Review Rules

- Keep migrations small, clear, and reviewable.
- Prefer explicit primary keys, foreign keys, indexes, and comments.
- Avoid destructive changes unless there is explicit review and approval.
- Do not commit secrets, `.env` files, real Microsoft Project files, generated exports, local database files, PDFs, DOCX files, ZIPs, screenshots, or other binary artifacts.
- Migrations are not expected to be idempotent when run manually; migration tooling should apply each version exactly once.

## Baseline Migration List

- `V001__baseline_extensions_and_enums.sql`: UUID extension and conservative enum types.
- `V002__projects_snapshots_and_imports.sql`: projects, immutable source files, import batches, and project snapshots.
- `V003__imported_project_entities.sql`: imported tasks, resources, assignments, extended attributes, and task lineage links.
- `V004__audit_events.sql`: append-only audit event table by application rule.
- `V005__approval_and_export_batches.sql`: approval records, export batches, and export batch lines.
- `V006__critical_watchlists_reporting.sql`: Critical Watchlists, Critical Work Packages, reporting policies, reporting periods, and Critical Updates.

## Product Boundary

Microsoft Project remains the schedule authority. Shutdown Tracker is the live execution and reporting authority.

The schema must not introduce scheduler ownership. Do not add critical-path, CPM, float calculation, resource-levelling, recovery-scheduling, automatic-date-movement, hidden-recalculation, live-feed, or uncontrolled Microsoft Project write-back structures.

Project exports must remain controlled, reviewed, approved, and batch-oriented. Only approved leaf-task progress/actual fields may be eligible for Microsoft Project export.
