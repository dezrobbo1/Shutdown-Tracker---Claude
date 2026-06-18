# Project Worker

Purpose: Spring Boot worker service shell for future Microsoft Project import/export processing through MPXJ and MSPDI/XML artifacts.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.projectworker`.
- The worker will later own Microsoft Project import/export processing.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- No MPXJ dependency, file parsing, background jobs, queue integration, scheduler logic, secrets, binaries, seed data, or real Project files are included.
- No domain behavior exists yet.

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
mvn -pl services/project-worker test
mvn -pl services/project-worker spring-boot:run -Dspring-boot.run.profiles=local
```
