# Project Worker

Purpose: Spring Boot worker service shell for future Microsoft Project import/export processing through MPXJ and MSPDI/XML artifacts.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.projectworker`.
- Worker-only MPXJ import summary spike in package `com.shutdowntracker.projectworker.importer`.
- Shared-contract parse summary handoff service and worker endpoint in package `com.shutdowntracker.projectworker.handoff`.
- Worker-only MSPDI/XML export artifact spike in package `com.shutdowntracker.projectworker.exporter`.
- Shared-contract export artifact generation handoff service and worker endpoint in package `com.shutdowntracker.projectworker.handoff`.
- Future queue/background-job wrapping for API-to-worker handoffs is documented in [Worker Handoff Queue Strategy](../../docs/architecture/worker-handoff-queue-strategy.md); no worker queue consumer exists yet.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- The import spike reads one explicit local file path only when `shutdown-tracker.import-spike.path` is set.
- The export spike writes one explicit local MSPDI/XML output path only when `shutdown-tracker.export-spike.output-path` is set.
- No persistence, upload endpoint, export approval endpoint, Project write-back, background jobs, queue integration, scheduler logic, secrets, binaries, seed data, or real Project files are included.
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

## Project Parse Handoff Boundary

`WorkerProjectParseHandoffService` accepts the shared `ProjectParseSummaryRequest`, resolves an explicit local file URI/path, calls the existing MPXJ summary service, and returns a shared `ProjectParseSummaryResponse`.

The worker exposes the same contract through:

- `POST /worker/project-import/parse-summary`

The response is summary-only: parser name/version, source filename, detected format, project name, task/resource/assignment/calendar/custom-field counts, warning/error counts, and notes. It does not persist import output, create snapshots, create imported tasks, run jobs, integrate a queue, generate exports, write back to Microsoft Project, or calculate schedules.

Only local file storage URIs are accepted for this early handoff. Non-local object-storage URIs should wait for the future storage/queue contract.

When a future queue consumer is added, it should reuse this worker-owned parsing boundary rather than moving MPXJ into the API. Product workflow state and audit writes should remain API-owned.

Worker tests compare the approved `synthetic-basic-wbs` MSPDI fixture against its structured expected import summary JSON, including the stable `worker_response` fields. This remains synthetic and summary-only.

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

The worker HTTP endpoint defaults to port `8081`, or `PORT` when set. The import spike command uses the default profile so it does not require PostgreSQL. When the path property is absent, the worker starts normally and does not run the import spike. With the path property set, the worker logs the summary during startup and continues serving until stopped.

## MSPDI/XML Export Artifact Spike

`MpxjMspdiExportArtifactService` builds a minimal MPXJ `ProjectFile` from explicit leaf-task export candidates and writes an MSPDI/XML artifact with MPXJ. It currently supports only:

- `percent_complete`
- `physical_percent_complete`
- `actual_start`
- `actual_finish`

The service rejects summary-task candidates, non-numeric Microsoft Project task identity, invalid percentage values, and non-XML output paths. Generated files are local-only test artifacts and must not be committed.

The worker also exposes the same artifact generation through:

- `POST /worker/project-export/generate-artifact`

The endpoint accepts the shared export handoff contract, writes the requested local MSPDI/XML path, and returns artifact URI/hash plus summary counts. The API remains responsible for checking export-batch approval and recording generated metadata.

The spike and endpoint do not read from the database, approve export batches, mark approval records exported, update `export_batches`, generate native MPP files, call Microsoft Project, or write back to Microsoft Project. They do not calculate CPM, critical path, float, resource levelling, recovery dates, or schedule movement.

Worker tests compare a synthetic export request against `fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json`, including stable summary fields and MPXJ readback values. Generated MSPDI/XML files are temporary test output only and are not committed.

When a future queue consumer is added, it should reuse this worker-owned artifact generation boundary. The API remains responsible for export approval checks, storage target reservation, URI/hash recording, audit writes, and product lifecycle state.

Run a synthetic local generation only when explicitly needed:

```text
mvn -pl services/project-worker spring-boot:run -Dspring-boot.run.arguments=--shutdown-tracker.export-spike.output-path=/absolute/path/to/local/synthetic-export.mspdi.xml
```

## Container Image

Build from the repository root so the Docker context includes the shared contract modules the worker depends on:

```text
docker build -f services/project-worker/Dockerfile -t shutdown-tracker-project-worker .
```

The build stage copies every module pom because the root pom declares all modules and the Maven reactor resolves the full module graph before `-pl`/`-am` selects a subset. CI builds this image on every pull request.

Handoff authentication fails closed, so supply the shared secret at run time and never bake it into the image:

```text
docker run -e SHUTDOWN_TRACKER_WORKER_AUTH_SHARED_SECRET=... -p 8081:8081 shutdown-tracker-project-worker
```
