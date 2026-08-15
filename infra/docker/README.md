# Docker

[`docker-compose.postgres.yml`](docker-compose.postgres.yml) provides a local PostgreSQL database for migration validation. It is not a production deployment or a complete application runtime.

## Compose configuration

The existing Compose project defines:

- project name `shutdown-tracker-migration-validation`;
- one `postgres` service using `postgres:16-alpine`;
- database and user `shutdown_tracker`, with the local-only password `shutdown_tracker_dev`;
- host port `5432` mapped to PostgreSQL port `5432`;
- named volume `shutdown_tracker_pgdata` for database data;
- read-only mount of `infra/migrations` at `/migrations`;
- read-only mount of `scripts/db` at `/validation` for the shared PostgreSQL integrity suite;
- a `pg_isready` health check for the validation database.

The Compose file does not start the API or project worker.

## Commands

Run from the repository root.

Unix-like systems:

```sh
docker compose -f infra/docker/docker-compose.postgres.yml up -d
docker compose -f infra/docker/docker-compose.postgres.yml ps
docker compose -f infra/docker/docker-compose.postgres.yml logs postgres
docker compose -f infra/docker/docker-compose.postgres.yml down -v
```

Windows PowerShell:

```powershell
docker compose -f .\infra\docker\docker-compose.postgres.yml up -d
docker compose -f .\infra\docker\docker-compose.postgres.yml ps
docker compose -f .\infra\docker\docker-compose.postgres.yml logs postgres
docker compose -f .\infra\docker\docker-compose.postgres.yml down -v
```

Use the repository validation scripts for the full reset, ordered migration application, readiness wait, 21-table check, populated upgrades, integrity assertions, and deterministic concurrency checks:

```sh
./scripts/db/validate-migrations.sh
```

```powershell
.\scripts\db\validate-migrations.ps1
```

`down -v` removes the named local validation volume. The validation scripts perform that reset before applying migrations and always run the same cleanup when they exit, including after validation failure.
