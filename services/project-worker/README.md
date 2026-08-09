# Project Worker

Purpose: Spring Boot worker service for bounded Microsoft Project import summaries and controlled MSPDI/XML artifact generation through MPXJ.

## Current Scope

- Spring Boot application in package `com.shutdowntracker.projectworker`.
- Worker-only MPXJ import summary spike in package `com.shutdowntracker.projectworker.importer`.
- Shared-contract parse summary handoff service and worker endpoint in package `com.shutdowntracker.projectworker.handoff`.
- Worker-only MSPDI/XML export artifact spike in package `com.shutdowntracker.projectworker.exporter`.
- Shared-contract export artifact generation handoff service and worker endpoint in package `com.shutdowntracker.projectworker.handoff`.
- Future queue/background-job wrapping for API-to-worker handoffs is documented in [Worker Handoff Queue Strategy](../../docs/architecture/worker-handoff-queue-strategy.md); no worker queue consumer exists yet.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- The import spike reads one explicit local file path only when `shutdown-tracker.import-spike.path` is set.
- The export spike writes one explicit local MSPDI/XML output path only when `shutdown-tracker.export-spike.output-path` is set.
- No persistence, upload endpoint, export approval endpoint, Project write-back, background jobs, queue integration, scheduler logic, secrets, binaries, seed data, or real Project files are included.
- Implemented worker behavior is deliberately limited to parse summaries and request-specific MSPDI/XML artifact generation; workflow authority and persistence remain API-owned.

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

`MpxjMspdiExportArtifactService` builds an MPXJ `ProjectFile` from the explicit values in the shared worker request. In the supported API handoff, the API constructs those values from revalidated authoritative candidates; the worker itself does not read or resolve candidate or approval records. It renders the project in memory, validates the generated task identity and requested values, then writes a securely parsed request-specific MSPDI/XML allowlist. The artifact retains project/task identity (`Name`, task `UID`, `ID`, and `Name`) for traceability and only the requested progress/actual elements below; MPXJ defaults such as calendars, WBS, durations, planned dates, resources, assignments, predecessors, constraints, and baselines are removed before bytes reach disk. It currently supports only:

- `percent_complete`
- `actual_start`
- `actual_finish`

The shared contract and worker reject `physical_percent_complete`, duplicate imported-task/field candidates, inconsistent repeated imported-task identity, duplicate Microsoft Project UID/ID mappings, summary-task candidates, missing or invalid task identity, unknown or numeric field aliases, unknown JSON properties, duplicate JSON properties, invalid values, and non-XML output paths. `percent_complete` equivalents such as `75`, `75.0`, and `075` canonicalize to whole-number `75`; fractional and out-of-range values are rejected. Proposed actual dates require an ISO-8601 minute- or second-precision value with an explicit offset and canonicalize to whole seconds while preserving the reviewed local wall-clock component. Omitted seconds become `:00`. A fractional component is accepted only when it contains one through six digits and every digit is zero; that zero-valued fraction canonicalizes away. Non-zero fractions, fractions outside the one-to-six-digit input range, offset-free inputs, and invalid values are rejected. The worker uses the normalized local date-time component without converting it to UTC. The API and worker use the same shared proposed-value normalizer; imported baseline timestamps do not cross this contract boundary. Physical percent complete may remain imported/internal read data, but it is not within the worker's MVP export authority. Generated files are local-only test artifacts and must not be committed.

The worker also exposes the same artifact generation through:

- `POST /worker/project-export/generate-artifact`

The endpoint accepts the shared export handoff contract, writes the requested local MSPDI/XML path, and returns artifact URI/hash plus summary counts. Direct contract use still fails closed on field, value, task-identity, and duplicate violations. The API remains responsible for candidate creation, current candidate-bound approval resolution, final export-batch validation, and generated metadata.

The spike and endpoint do not read from the database, approve export batches, mark approval records exported, update `export_batches`, generate native MPP files, call Microsoft Project, or write back to Microsoft Project. They do not calculate CPM, critical path, float, resource levelling, recovery dates, or schedule movement.

Worker tests compare a synthetic export request against `fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json`, including stable summary fields and MPXJ readback values. Generated MSPDI/XML files are temporary test output only and are not committed.

When a future queue consumer is added, it should reuse this worker-owned artifact generation boundary. The API remains responsible for export approval checks, storage target reservation, URI/hash recording, audit writes, and product lifecycle state.

Run a synthetic local generation only when explicitly needed:

```text
mvn -pl services/project-worker spring-boot:run -Dspring-boot.run.arguments=--shutdown-tracker.export-spike.output-path=/absolute/path/to/local/synthetic-export.mspdi.xml
```
