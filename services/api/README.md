# API Service

Purpose: Spring Boot API service shell for future operational workflows, permissions, audit events, task updates, problems, actions, evidence metadata, handover, and export approvals.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.api`.
- Actuator is present for health/info exposure.
- `GET /api/version` returns a minimal service/status JSON payload.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- No task, import, export, approval, evidence, domain, or scheduler endpoints exist yet.
- No Spring Security/OIDC, MPXJ, frontend, secrets, binaries, seed data, or real Project files are included.

## Database Runtime Config

The `local` profile uses PostgreSQL and Flyway. Run commands from the repository root so Flyway can resolve `filesystem:infra/migrations`.

Default local values align with `infra/docker/docker-compose.postgres.yml`:

- `SHUTDOWN_TRACKER_DB_URL`, default `jdbc:postgresql://localhost:5432/shutdown_tracker`
- `SHUTDOWN_TRACKER_DB_USERNAME`, default `shutdown_tracker`
- `SHUTDOWN_TRACKER_DB_PASSWORD`, default `shutdown_tracker_dev`
- `SHUTDOWN_TRACKER_FLYWAY_LOCATIONS`, default `filesystem:infra/migrations`

The test profile disables datasource and Flyway auto-configuration so context-load tests do not require PostgreSQL.

The migration validation scripts apply SQL directly and do not create Flyway history. Use a clean PostgreSQL volume when checking Spring Boot runtime migrations through this service.

## Local Commands

Run from the repository root when Maven and Java 21 are available:

```text
mvn -pl services/api test
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=local
```
