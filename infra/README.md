# Infrastructure

This directory contains the local PostgreSQL migration-validation configuration and the versioned database schema for Shutdown Tracker. It is not a production deployment definition.

## Contents

- [`docker/docker-compose.postgres.yml`](docker/docker-compose.postgres.yml): PostgreSQL 16 Compose service for clean local migration validation.
- [`migrations`](migrations): Flyway-compatible PostgreSQL migrations `V001` through `V007`.
- [`../scripts/db`](../scripts/db): Bash and PowerShell runners that reset the validation database, apply every migration file in its own transaction, and verify the 20-table baseline.

## Local validation

Run from the repository root.

Unix-like systems:

```sh
./scripts/db/validate-migrations.sh
```

Windows PowerShell:

```powershell
.\scripts\db\validate-migrations.ps1
```

Both scripts use Docker Compose and run `psql` inside the PostgreSQL container; a host `psql` installation is not required. They remove the named validation volume before applying the migrations. A SQL error rolls back the complete migration file rather than leaving partial objects.

V007 preserves pre-policy export batches and lines as unversioned, read-only history. New current-policy previews seal their complete line membership before approval; scoped duplicate and export-field authority constraints apply only to those new lines. Legacy physical-percent and duplicate candidates remain readable without becoming newly exportable.

## Application infrastructure

The Java 21 Spring Boot API and project worker both have `local` profile PostgreSQL and Flyway configuration. Their defaults match the local Compose database, and Flyway reads `filesystem:infra/migrations` when the services are started from the repository root.

The Compose file in this directory starts PostgreSQL only. It does not start either application service. The API review-image definition is in [`../services/api/Dockerfile`](../services/api/Dockerfile); there is no API or worker service in the PostgreSQL Compose file.

The API owns request workflows and persistence orchestration. Microsoft Project parsing and MSPDI/XML artifact generation remain in the project worker. Existing API-to-worker handoffs are explicit and opt-in; no durable queue or background-job infrastructure is implemented.

## Product boundary

Microsoft Project remains the schedule authority. This infrastructure supports imported snapshots, execution review, audit, controlled export preparation, and verification metadata. It does not provide scheduling calculations, automatic date movement, native `.mpp` writing, or uncontrolled Project write-back.
