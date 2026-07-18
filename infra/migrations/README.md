# Database Migrations

This directory contains the PostgreSQL schema migrations used by Shutdown Tracker. The files follow Flyway version naming and must be applied once in ascending version order.

## Current baseline

The baseline is `V001` through `V007` and creates 20 application tables:

- `V001__baseline_extensions_and_enums.sql`: enables `pgcrypto` and defines the initial enum types; creates no tables.
- `V002__projects_snapshots_and_imports.sql`: creates `projects`, `source_files`, `import_batches`, and `project_snapshots`.
- `V003__imported_project_entities.sql`: creates `imported_tasks`, `imported_resources`, `imported_assignments`, `imported_extended_attributes`, and `task_lineage_links`.
- `V004__audit_events.sql`: creates the append-only-by-application-rule `audit_events` table.
- `V005__approval_and_export_batches.sql`: creates `approval_records`, `export_batches`, and `export_batch_lines`.
- `V006__critical_watchlists_reporting.sql`: creates `critical_watchlists`, `critical_work_packages`, `critical_work_package_sources`, `reporting_policy_versions`, `reporting_periods`, `critical_updates`, and `critical_update_lines`.
- `V007__enforce_export_candidate_integrity.sql`: preserves existing export history as unversioned, read-only records; adds sealed current-policy line sets, ordered approval events, and captured approval identity/state; and enforces current-policy candidate uniqueness and field authority; creates no tables.

Critical Watchlists and Critical Work Packages are reporting constructs. They do not calculate critical path, float, or recovery schedules.

## V007 export-integrity policy

V007 does not delete, merge, or rewrite V006 approval, batch, or line values. Existing export batches and lines retain a null policy version and remain readable, including generated, opened, verified, rejected, and superseded history, but they are frozen against further lifecycle writes. A legacy draft or approved batch requires a fresh preview under the current policy before it can progress.

New approval records receive a database-assigned event order; legacy approval records remain unsequenced. If the most recent legacy timestamp is tied, approval authority is ambiguous until a new ordered approval event is recorded. Each new preview line stores the exact approval-record identity and state used to materialize it. A current-policy batch starts unsealed, accepts lines only while it remains an unsealed draft, and can be sealed only once; sealed membership and all preview lines are immutable. Approval-event writes lock active sealed batches that captured the same source, serializing authority changes with approval and artifact generation. Current-policy lines permit at most one candidate per imported task and field, and only `percent_complete`, `actual_start`, and `actual_finish` may be export eligible. `physical_percent_complete` remains readable internally and historically but is never current-policy export eligible.

## Runtime use

The API and project worker `local` profiles enable Flyway with:

```text
filesystem:infra/migrations
```

Start those services from the repository root so the relative location resolves correctly. The local datasource defaults match [`../docker/docker-compose.postgres.yml`](../docker/docker-compose.postgres.yml).

The validation scripts do not run Flyway or create Flyway schema-history records. They apply each SQL file directly with containerized `psql` in its own PostgreSQL transaction against a clean database, fail on SQL errors, roll back the complete failing file, and verify all 20 expected tables.

## Validate locally

Prerequisite: Docker with Docker Compose support. A host `psql` installation is not required.

Unix-like systems, from the repository root:

```sh
./scripts/db/validate-migrations.sh
```

Windows PowerShell, from the repository root:

```powershell
.\scripts\db\validate-migrations.ps1
```

Both scripts reset the named validation volume before applying `infra/migrations/V*.sql` in sorted order. They validate a clean installation; populated-upgrade checks require separate synthetic validation.

## Migration rules

- Add the next monotonically increasing `V###__description.sql` file; never rename or rewrite an applied migration.
- Keep migrations reviewable and use explicit keys, constraints, indexes, and comments.
- Do not place seed data, secrets, real Project files, generated export artifacts, or other operational data in schema migrations.
- Treat imported Microsoft Project rows as immutable snapshot facts. Microsoft Project remains the schedule authority.
- Preserve reviewed, approved, batch-oriented export handling; do not introduce scheduler ownership or uncontrolled Project write-back.
