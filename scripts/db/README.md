# Database Validation Scripts

These scripts validate the SQL migrations in `infra/migrations` against a clean local PostgreSQL database.

They are for local validation only. They are not a production deployment, application runtime, or database administration system.

## Prerequisites

- Docker Desktop or another Docker environment with Docker Compose support.
- No local `psql` installation is required. The scripts run `psql` inside the PostgreSQL container.

## PowerShell

From the repository root:

```powershell
.\scripts\db\validate-migrations.ps1
```

The PowerShell script checks `PATH` first and then falls back to common Docker Desktop install locations, including per-user installs under `%LOCALAPPDATA%\Programs\DockerDesktop`.

Both platform wrappers invoke the same read-only-mounted POSIX integrity runner inside the PostgreSQL container, so the SQL fixtures, assertions, and concurrency behavior do not diverge by host shell.

## POSIX Shell

From the repository root:

```sh
./scripts/db/validate-migrations.sh
```

## What The Scripts Validate

- Start a local PostgreSQL container using `infra/docker/docker-compose.postgres.yml`.
- Reset the validation database volume before applying migrations.
- Wait for PostgreSQL readiness.
- Apply each `infra/migrations/V*.sql` file in sorted version order as its own PostgreSQL transaction.
- Fail fast on SQL errors and roll back the complete failing migration file so it cannot leave partial database objects.
- Verify the expected 21 baseline tables exist after migration.
- Upgrade populated synthetic V006 history through V007 and V008 without changing historical business values, duplicates, physical-percent lines, lifecycle records, or null legacy markers.
- Preserve and freeze synthetic V007 policy-1 records when V008 is applied.
- Exercise policy-2 authoritative-candidate bindings, reciprocal foreign keys, exact line identity/value matching, field and leaf authority, duplicates, sealing, immutable history, approval ordering, and baseline freshness.
- Prove line-versus-seal, concurrent duplicate, generation-versus-approval, and failed-generation rollback behavior with separately synchronized PostgreSQL sessions.
- Intentionally fail V007 and V008 at the end of their transaction and verify that neither migration leaves partial objects.

The populated data is fixed, synthetic validation data created only in temporary databases inside the local validation container. The suite does not run application code, create Project artifacts, or use operational data. Concurrency synchronization uses PostgreSQL locks and `pg_blocking_pids`, rather than assuming that a timed delay proves blocking.

## Reset

To manually reset the local validation container and volume:

```sh
docker compose -f infra/docker/docker-compose.postgres.yml down -v
```

The validation scripts already run this reset before applying migrations.

## Boundary

This setup exists only to validate the local migration foundation. It does not imply production database configuration, cloud services, scheduler ownership, live Microsoft Project integration, or uncontrolled Project write-back.
