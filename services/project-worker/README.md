# Project Worker

Purpose: Spring Boot worker service shell for future Microsoft Project import/export processing through MPXJ and MSPDI/XML artifacts.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.projectworker`.
- Worker-only MPXJ import summary spike in package `com.shutdowntracker.projectworker.importer`.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- The import spike reads one explicit local file path only when `shutdown-tracker.import-spike.path` is set.
- No persistence, upload endpoint, export generation, Project write-back, background jobs, queue integration, scheduler logic, secrets, binaries, seed data, or real Project files are included.
- No domain behavior exists yet.

## MPXJ Import Summary Spike

The worker includes `net.sf.mpxj:mpxj` version `16.4.0` for local import summary exploration. The summary service reads a local file with MPXJ and reports:

- Source filename.
- Detected file format when MPXJ exposes it.
- Project name.
- Task, summary-task, leaf-task, resource, assignment, calendar, and custom-field counts.
- Ignored read issues as notes when MPXJ exposes them.

The spike does not calculate CPM, critical path, float, resource levelling, recovery dates, or any schedule movement. It does not persist imported data or produce exports.

Local files are for local testing only. Do not commit real customer/project files, MPP/XML/MSPDI/XER/ZIP/PDF/DOCX files, screenshots, generated exports, or any file containing real work orders, contractors, vendors, people, locations, assets, costs, or commercial data.

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
mvn -pl services/project-worker spring-boot:run -Dspring-boot.run.arguments=--shutdown-tracker.import-spike.path=/absolute/path/to/local/safe-file.mpp
```

The import spike command uses the default profile so it does not require PostgreSQL. When the path property is absent, the worker starts normally and does not run the import spike. With the path property set, the current non-web worker logs the summary and exits after startup work completes.
