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
- Verify the expected baseline tables exist after migration.

The scripts validate a clean install only. They do not insert seed data, exercise populated upgrades, or run application code.

## Reset

To manually reset the local validation container and volume:

```sh
docker compose -f infra/docker/docker-compose.postgres.yml down -v
```

The validation scripts already run this reset before applying migrations.

## Boundary

This setup exists only to validate the local migration foundation. It does not imply production database configuration, cloud services, scheduler ownership, live Microsoft Project integration, or uncontrolled Project write-back.
