# API Service

Purpose: Spring Boot API service shell for future operational workflows, permissions, audit events, task updates, problems, actions, evidence metadata, handover, and export approvals.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.api`.
- Actuator is present for health/info exposure.
- `GET /api/version` returns a minimal service/status JSON payload.
- `POST /api/source-files/validate` validates one multipart `file` upload request and returns metadata plus an accept/reject decision.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- The `review` profile boots without PostgreSQL for backend smoke checks only.
- Source-file storage has an internal abstraction and local filesystem implementation for future upload workflows.
- Review project bootstrap and source-file metadata persistence have local-profile JDBC services.
- Import batch persistence has local-profile JDBC services using the existing `import_batch_status` enum.
- No file is stored, parsed, persisted, forwarded, or imported by the validation endpoint.
- No task execution, import batch, export, approval, evidence, domain, or scheduler endpoints exist yet.
- No Spring Security/OIDC, MPXJ, frontend, secrets, binaries, seed data, or real Project files are included.

## Source File Validation Placeholder

`POST /api/source-files/validate` is validation-only. It accepts a multipart field named `file` and returns:

- Original filename.
- Size in bytes.
- Detected extension.
- Accepted true/false.
- Rejection reason when rejected.
- Message confirming no file was stored or parsed.

Accepted extensions:

- `.mpp`
- `.xml`
- `.mspdi.xml`

Rejected examples include empty files, missing filenames, unsupported extensions, `.zip`, `.pdf`, `.doc`, `.docx`, `.xer`, screenshots/images, and files over the configured placeholder limit.

The default placeholder validation limit is 50 MB via `shutdown-tracker.source-file-validation.max-size-bytes`. Spring multipart limits are set slightly higher at 60 MB so normal oversized uploads can still receive the validation response shape. Hard multipart failures, missing `file` fields, and multipart parse errors are handled by exception type rather than selected controller, so they return the same validation-style JSON where possible.

Local testing files must stay outside Git. Do not commit real Project files, customer/site files, screenshots, generated exports, binaries, or secrets.

## Source File Storage Abstraction

The API includes a source-file storage boundary for future upload workflows:

- `SourceFileStorage` accepts a stream and returns storage metadata.
- `LocalSourceFileStorage` writes to a configured local filesystem root and returns a `file:` URI plus SHA-256 content hash.
- `shutdown-tracker.source-file-storage.local-root` defaults to `.shutdown-tracker/source-files` and can be overridden with `SHUTDOWN_TRACKER_SOURCE_FILE_STORAGE_LOCAL_ROOT`.

This is not production object storage. The local implementation exists so future source-file metadata and import-batch work can depend on a stable storage interface before S3/Azure Blob or another object store is selected.

No API endpoint calls the storage abstraction yet. `POST /api/source-files/validate` remains validation-only and still stores, parses, persists, forwards, and imports nothing.

## Review Project Bootstrap

The API includes a guarded service for creating or reusing one synthetic review project in the existing `projects` table. This is a local/review setup helper for later import workflow testing, not seed data and not production tenant provisioning.

Persistence services are disabled by default through `shutdown-tracker.persistence.enabled=false` and enabled by `application-local.yml`. To run the bootstrap path against local PostgreSQL, use the `local` profile and explicitly enable:

```text
SHUTDOWN_TRACKER_REVIEW_PROJECT_BOOTSTRAP_ENABLED=true
```

Optional bootstrap settings:

- `SHUTDOWN_TRACKER_REVIEW_PROJECT_BOOTSTRAP_PROJECT_NAME`, default `Synthetic Review Project`
- `SHUTDOWN_TRACKER_REVIEW_PROJECT_BOOTSTRAP_DESCRIPTION`
- `SHUTDOWN_TRACKER_REVIEW_PROJECT_BOOTSTRAP_TIMEZONE`, default `UTC`

The bootstrap writes metadata marking the project as synthetic and review-bootstrap-only. It does not create source files, import batches, snapshots, tasks, or demo execution records.

## Source File Metadata Persistence

The API includes a service/repository boundary for creating `source_files` metadata rows against an existing project. It records:

- `project_id`
- original filename
- file kind: `mpp`, `mspdi_xml`, `xml`, or `other`
- storage URI
- SHA-256 content hash
- file size

The service can consume `StoredSourceFile` values returned by the storage abstraction. It does not store bytes in PostgreSQL, create import batches, parse files, call MPXJ, or expose a public upload endpoint.

## Import Batch Persistence

The API includes a service/repository boundary for creating `import_batches` records linked to an existing project and source file. New batches are created with status `pending`.

Status updates use only the existing database enum values:

- `pending`
- `parsing`
- `parsed`
- `accepted`
- `failed`
- `superseded`

The repository stamps `started_at` when a batch first moves to `parsing` and stamps `completed_at` when a batch first moves to `parsed`, `accepted`, `failed`, or `superseded`.

This does not call MPXJ, request worker parsing, persist parse summaries, create project snapshots, persist imported tasks, or expose a public import-batch endpoint.

## Database Runtime Config

The `local` profile uses PostgreSQL and Flyway. Run commands from the repository root so Flyway can resolve `filesystem:infra/migrations`.

Default local values align with `infra/docker/docker-compose.postgres.yml`:

- `SHUTDOWN_TRACKER_DB_URL`, default `jdbc:postgresql://localhost:5432/shutdown_tracker`
- `SHUTDOWN_TRACKER_DB_USERNAME`, default `shutdown_tracker`
- `SHUTDOWN_TRACKER_DB_PASSWORD`, default `shutdown_tracker_dev`
- `SHUTDOWN_TRACKER_FLYWAY_LOCATIONS`, default `filesystem:infra/migrations`
- `SHUTDOWN_TRACKER_REVIEW_PROJECT_BOOTSTRAP_ENABLED`, default `false`

The test profile disables datasource and Flyway auto-configuration so context-load tests do not require PostgreSQL.

The migration validation scripts apply SQL directly and do not create Flyway history. Use a clean PostgreSQL volume when checking Spring Boot runtime migrations through this service.

## Review Smoke Profile

The `review` profile is for backend smoke deployment only. It disables datasource and Flyway auto-configuration, uses `PORT` with a default of `8080`, and keeps Actuator health/info plus source-file validation available without PostgreSQL.

See [API review smoke profile](../../docs/deployment/api-review-smoke.md) for curl checks and Docker usage.

## Local Commands

Run from the repository root when Maven and Java 21 are available:

```text
mvn -pl services/api test
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=local
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=review
```
