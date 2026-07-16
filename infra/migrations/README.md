# Database Migrations

This directory contains the PostgreSQL schema migrations used by Shutdown Tracker. The files follow Flyway version naming and must be applied once in ascending version order.

## Current baseline

The baseline is `V001` through `V006` and creates 20 application tables:

- `V001__baseline_extensions_and_enums.sql`: enables `pgcrypto` and defines the initial enum types; creates no tables.
- `V002__projects_snapshots_and_imports.sql`: creates `projects`, `source_files`, `import_batches`, and `project_snapshots`.
- `V003__imported_project_entities.sql`: creates `imported_tasks`, `imported_resources`, `imported_assignments`, `imported_extended_attributes`, and `task_lineage_links`.
- `V004__audit_events.sql`: creates the append-only-by-application-rule `audit_events` table.
- `V005__approval_and_export_batches.sql`: creates `approval_records`, `export_batches`, and `export_batch_lines`.
- `V006__critical_watchlists_reporting.sql`: creates `critical_watchlists`, `critical_work_packages`, `critical_work_package_sources`, `reporting_policy_versions`, `reporting_periods`, `critical_updates`, and `critical_update_lines`.

Critical Watchlists and Critical Work Packages are reporting constructs. They do not calculate critical path, float, or recovery schedules.

## Runtime use

The API and project worker `local` profiles enable Flyway with:

```text
filesystem:infra/migrations
```

Start those services from the repository root so the relative location resolves correctly. The local datasource defaults match [`../docker/docker-compose.postgres.yml`](../docker/docker-compose.postgres.yml).

The validation scripts do not run Flyway or create Flyway schema-history records. They apply the SQL files directly with containerized `psql` against a clean database, fail on SQL errors, and verify all 20 expected tables.

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

Both scripts reset the named validation volume before applying `infra/migrations/V*.sql` in sorted order.

## Migration rules

- Add the next monotonically increasing `V###__description.sql` file; never rename or rewrite an applied migration.
- Keep migrations reviewable and use explicit keys, constraints, indexes, and comments.
- Do not place seed data, secrets, real Project files, generated export artifacts, or other operational data in schema migrations.
- Treat imported Microsoft Project rows as immutable snapshot facts. Microsoft Project remains the schedule authority.
- Preserve reviewed, approved, batch-oriented export handling; do not introduce scheduler ownership or uncontrolled Project write-back.
