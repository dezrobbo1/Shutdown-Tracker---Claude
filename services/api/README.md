# API Service

Purpose: Spring Boot API service shell for future operational workflows, permissions, audit events, task updates, problems, actions, evidence metadata, handover, and export approvals.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.api`.
- Actuator is present for health/info exposure.
- `GET /api/version` returns a minimal service/status JSON payload.
- `POST /api/source-files/validate` validates one multipart `file` upload request and returns metadata plus an accept/reject decision.
- `POST /api/projects/{projectId}/source-files` stores an accepted source file, creates `source_files` metadata, and creates a pending import batch when persistence is enabled.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- The `review` profile boots without PostgreSQL for backend smoke checks only.
- Source-file storage has an internal abstraction and local filesystem implementation for future upload workflows.
- Export artifact storage has an internal abstraction and local filesystem implementation for worker-generated MSPDI/XML artifacts.
- Production object-store provider selection and configuration guidance is documented in [Object Storage Provider Strategy](../../docs/architecture/object-storage-provider-strategy.md); no production provider implementation exists yet.
- Future seeded review/demo data guidance is documented in [Seeded Review and Demo Data Strategy](../../docs/testing/seeded-review-demo-data-strategy.md); no seeded dataset implementation exists yet.
- Local source/import/export smoke checks can use [source-import-export-smoke.ps1](../../scripts/review/source-import-export-smoke.ps1); write steps are guarded and opt-in.
- Review project bootstrap and source-file metadata persistence have local-profile JDBC services.
- Import batch persistence has local-profile JDBC services using the existing `import_batches` table and `import_batch_status` enum.
- Imported project snapshot persistence has local-profile JDBC services using the existing `project_snapshots` and imported Project entity tables.
- Import review has local-profile API endpoints for reviewing parsed snapshots and accepting or rejecting them with existing status values.
- Task lineage review has local-profile API endpoints for creating concrete task-to-task lineage links between imported snapshots and accepting or rejecting suggested links with existing review-state values.
- Export preview has local-profile API endpoints for creating `draft_preview` batches and previewing eligibility for approved leaf-task progress/actual fields.
- Audit event writes are wired for import snapshot review decisions, task lineage review decisions, export preview creation, and export batch lifecycle decisions using the existing `audit_events` table.
- Export batch approval orchestration has local-profile endpoints for approving or rejecting draft preview batches and recording manual Microsoft Project reopen/verification metadata. Generated artifact metadata is recorded only inside the worker-backed generation handoff.
- Project parse handoff has a shared request builder, local-profile API trigger endpoint, default disconnected client, and opt-in HTTP worker client.
- Project export artifact handoff has a shared request builder, local-profile API trigger endpoint, default disconnected client, and opt-in HTTP worker client.
- JSON request bodies are fail-closed API-wide: unknown or duplicate properties and numeric enum aliases are rejected instead of being ignored or coerced.
- Future queue/background-job wrapping for those handoffs is documented in [Worker Handoff Queue Strategy](../../docs/architecture/worker-handoff-queue-strategy.md); no queue implementation exists yet.
- No file is stored, parsed, persisted, forwarded, or imported by the validation endpoint.
- No task execution, evidence, scheduler, parser execution, automatic lineage matching, automated Project verification, or write-back endpoints exist yet.
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

This is not production object storage. The local implementation exists so source-file metadata and import-batch work can depend on a stable storage interface before S3/Azure Blob or another object store is selected. Provider selection and configuration guidance lives in [Object Storage Provider Strategy](../../docs/architecture/object-storage-provider-strategy.md).

`POST /api/source-files/validate` remains validation-only and still stores, parses, persists, forwards, and imports nothing. When persistence is enabled, `POST /api/projects/{projectId}/source-files` is the first endpoint that calls the storage abstraction.

## Export Artifact Storage Abstraction

The API includes an export-artifact storage boundary for worker-generated MSPDI/XML artifacts:

- `ExportArtifactStorage` prepares a storage-owned output location for an export batch.
- `LocalExportArtifactStorage` reserves a path under a configured local filesystem root and returns a `file:` URI for the generated artifact.
- `shutdown-tracker.export-artifact-storage.local-root` defaults to `.shutdown-tracker/export-artifacts` and can be overridden with `SHUTDOWN_TRACKER_EXPORT_ARTIFACT_STORAGE_LOCAL_ROOT`.

This is not production object storage. The local implementation exists so export-artifact handoff code depends on a stable storage interface before S3/Azure Blob or another object store is selected. Provider selection and configuration guidance lives in [Object Storage Provider Strategy](../../docs/architecture/object-storage-provider-strategy.md).

The storage abstraction prepares the target path only. It does not generate MSPDI/XML, store artifact bytes in PostgreSQL, parse artifacts, open Microsoft Project, verify artifact contents, or write back to Microsoft Project.

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

Future local/review seeded data should follow [Seeded Review and Demo Data Strategy](../../docs/testing/seeded-review-demo-data-strategy.md). Seeded data must remain disabled by default, synthetic-only, dataset-scoped, and separate from migrations.

## Source File Metadata Persistence

The API includes a service/repository boundary for creating `source_files` metadata rows against an existing project. It records:

- `project_id`
- original filename
- file kind: `mpp`, `mspdi_xml`, `xml`, or `other`
- storage URI
- SHA-256 content hash
- file size

The service consumes `StoredSourceFile` values returned by the storage abstraction. It does not store bytes in PostgreSQL, parse files, call MPXJ, or enqueue worker jobs.

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

The API can also request and record a worker parse summary response against an existing pending import batch. This updates:

- `status` to `parsed`
- `parser_name`
- `parser_version`
- `warning_count`
- `error_count`
- `parse_summary` JSONB with source filename, detected format, project name, summary-only flag, count metadata, and notes

This calls MPXJ only through the configured project-worker endpoint. It does not parse in the API, persist imported tasks/resources/assignments, create a project snapshot, run a queue, or create live execution records.

## Source File Upload Orchestration

When persistence is enabled, the API exposes the first project-scoped upload orchestration endpoint:

- `POST /api/projects/{projectId}/source-files`

The endpoint accepts a multipart field named `file`, reuses the same source-file validation rules, stores accepted bytes through `SourceFileStorage`, creates a `source_files` metadata row, creates a pending `import_batches` row, and records a `source_file_uploaded` audit event.

Rejected uploads stop before storage and do not create metadata, import batches, or audit rows. Accepted uploads still do not call MPXJ, parse the file, create a worker job, persist imported tasks/resources/assignments, create a project snapshot, generate exports, or write back to Microsoft Project. The `review` profile still boots without PostgreSQL and does not expose this persistence-backed endpoint.

## Imported Project Snapshot Persistence

The API includes a transactional service/repository boundary for persisting one parsed immutable Microsoft Project snapshot and its imported entity rows into existing baseline tables:

- `project_snapshots`
- `imported_tasks`
- `imported_resources`
- `imported_assignments`
- `imported_extended_attributes`

New snapshots are created with the existing `project_snapshot_status` value `parsed`. Snapshot versions are assigned per project by the repository. Imported task rows store schedule values as imported snapshot facts only; they are not live execution state and are not recalculated by Shutdown Tracker.

This does not call MPXJ, parse files, create worker jobs, create task lineage links, mutate imported schedule rows, calculate CPM/critical path/float, move dates, perform resource levelling, or write back to Microsoft Project.

## Import Review API

When persistence is enabled, the API exposes a project-scoped review surface for already-persisted imported snapshots:

- `GET /api/projects/{projectId}/import-review/snapshots`
- `GET /api/projects/{projectId}/import-review/snapshots/{snapshotId}`
- `POST /api/projects/{projectId}/import-review/snapshots/{snapshotId}/accept`
- `POST /api/projects/{projectId}/import-review/snapshots/{snapshotId}/reject`

The review responses include snapshot metadata, parser/warning counts, imported task/resource/assignment/extended-attribute rows, and summary/leaf task counts. Accept and reject use only the existing `project_snapshot_status` values `accepted` and `rejected`; accepting also marks the related import batch `accepted`.

The snapshot review endpoints do not upload or store files, parse files, call MPXJ, create worker jobs, create live execution records, generate exports, calculate schedule fields, or write back to Microsoft Project. The `review` profile still boots without PostgreSQL and does not expose these persistence-backed endpoints.

Accepting or rejecting a snapshot records an audit event after the status update succeeds:

- `import_snapshot_accepted`
- `import_snapshot_rejected`

The audit row targets the project snapshot, includes the previous and new snapshot status, references the imported project snapshot, and records metadata confirming no Project write-back occurred.

## Task Lineage Review Persistence

When persistence is enabled, the API exposes project-scoped lineage review endpoints under the import review surface:

- `GET /api/projects/{projectId}/import-review/lineage-links?previousSnapshotId={previousSnapshotId}&currentSnapshotId={currentSnapshotId}`
- `POST /api/projects/{projectId}/import-review/lineage-links`
- `POST /api/projects/{projectId}/import-review/lineage-links/{lineageLinkId}/accept`
- `POST /api/projects/{projectId}/import-review/lineage-links/{lineageLinkId}/reject`

Creating a link persists one concrete previous-task to current-task relationship in the existing `task_lineage_links` table with review state `suggested`. The request requires both snapshot IDs, both imported task IDs, a match method such as `external_uid`, `wbs`, `outline_number`, `name`, or `manual_review`, optional confidence, and optional metadata.

Accept and reject use only the existing `review_state` values `accepted` and `rejected`, and only suggested links can be reviewed. This is persistence and review plumbing only; it does not run automatic matching, create unmatched-task review records, calculate dependencies, mutate imported tasks, create live execution records, generate exports, or write back to Microsoft Project.

Creating, accepting, or rejecting a lineage link records an audit event after the link write succeeds:

- `reimport_lineage_link_created`
- `reimport_lineage_link_accepted`
- `reimport_lineage_link_rejected`

The audit row targets the lineage link, references the current imported project snapshot, and records metadata confirming no schedule calculation or Project write-back occurred.

## Authoritative Export Candidates

When persistence is enabled, the API exposes separate candidate creation and approval-event surfaces:

- `POST /api/projects/{projectId}/export-candidates`
- `POST /api/projects/{projectId}/export-candidates/{candidateId}/approval-events`

Candidate creation accepts `projectSnapshotId`, `importedTaskId`, `fieldName`, `proposedValue`, `sourceEntityType`, `sourceEntityId`, and a required `sourceVersion`, plus optional source actor/timestamp, reason, and metadata. The server requires an accepted snapshot and matching imported task, captures Microsoft Project task UID/ID/name/leaf state and the current baseline value, canonicalizes the proposed value, and computes the immutable source-event or payload fingerprint. Callers cannot provide the captured baseline, task identity, normalized value, fingerprint, or approval state.

The returned candidate is approval-neutral. Candidate creation does not approve it or make it export eligible. A later approval-event request appends `approvalState` and optional review metadata against the exact candidate ID. Before an `approved_for_export` event is appended, PostgreSQL locks and revalidates the accepted snapshot, exact imported task UID/ID/name/leaf identity, captured baseline, canonical proposed value, source fingerprint, and candidate identity. Non-authorizing rejection, correction, and supersession history remains appendable after drift. New candidate approval history is append-only and database ordered; current authority is the latest event for that candidate. An approval event for candidate A cannot authorize candidate B even if both share a generic source identity.

Proposed-value normalization is shared with the worker boundary. `percent_complete` accepts whole-number equivalents such as `75`, `75.0`, and `075` and stores canonical `75`. Proposed `actual_start` and `actual_finish` values require an ISO-8601 minute- or second-precision value with an explicit offset and canonicalize to whole seconds while preserving the reviewed local wall-clock component; omitted seconds become `:00`, all-zero fractions canonicalize away, and non-zero fractions, offset-free values, and invalid values are rejected. The worker uses the normalized local component without converting it to UTC. Captured imported actual baselines use a separate canonicalizer that preserves available microsecond precision for freshness comparison and are not sent to the worker as proposed values. `physical_percent_complete` may be recorded as internal review context but never becomes export eligible.

## Export Preview Model

The preview surface is:

- `POST /api/projects/{projectId}/export-preview`
- `GET /api/projects/{projectId}/export-preview/{exportBatchId}`

Creating a preview accepts only `projectSnapshotId`, `candidateIds`, and optional metadata. It writes one current policy-1 `export_batches` row with status `draft_preview`, materializes exact candidate-bound `export_batch_lines`, and seals the complete line set before returning. The server derives every task, field, baseline, proposed value, source, fingerprint, and captured approval identity from the immutable candidates and their exact latest approval events.

Preview lines can represent these imported/internal values:

- `percent_complete`
- `physical_percent_complete`
- `actual_start`
- `actual_finish`

The MVP export whitelist is limited to `percent_complete`, `actual_start`, and `actual_finish`. `physical_percent_complete` remains readable for imported, candidate, and historical preview compatibility but is outside export authority and cannot reach a generated artifact. A line is eligible only when its exact latest candidate-bound event is `approved_for_export`, the captured task remains a leaf task in the accepted snapshot, and the field is on the whitelist. Summary-task and physical-percent candidates may appear as ineligible review context. A request cannot contain more than one candidate for the same imported task and field.

The preview creation endpoint does not approve export batches, generate MSPDI/XML, write export files, mark approval records exported, mutate imported task rows, calculate schedule fields, or write back to Microsoft Project. The `review` profile still boots without PostgreSQL and does not expose these persistence-backed endpoints.

Creating a draft preview records `export_preview_created` after the preview batch and lines are stored. The audit row targets the export batch, references both `project_snapshot_id` and `export_batch_id`, and records line counts plus metadata confirming no artifact generation or Project write-back occurred.

## Export Batch Approval Orchestration

When persistence is enabled, the API exposes additive lifecycle endpoints on the preview surface:

- `POST /api/projects/{projectId}/export-preview/{exportBatchId}/approve`
- `POST /api/projects/{projectId}/export-preview/{exportBatchId}/reject`
- `POST /api/projects/{projectId}/export-preview/{exportBatchId}/mark-opened-in-microsoft-project`
- `POST /api/projects/{projectId}/export-preview/{exportBatchId}/verify`

Lifecycle writes operate only on current policy-1 batches created after V007. Unversioned V006 batches and lines remain readable, frozen history, including generated, opened, verified, rejected, and superseded records. Their draft or approved batches cannot progress and require a fresh policy-1 preview.

Approve and reject operate only on current policy-1 `draft_preview` batches. Approval requires the exact snapshot to remain accepted and revalidates every line's candidate ID, exact latest approval identity/state, source identity/version/fingerprint, task UID/ID/name, leaf state, old value, normalized new value, field authority, eligibility, and uniqueness. Any difference blocks the whole batch, including a source change that leaves an ineligible physical-percent or summary-task line ineligible. Approval requires at least one eligible line and moves the batch to `approved`, stamping `approved_at` and optional `approved_by_user_id`. Rejection moves the batch to `rejected`; rejection details are carried in request/audit metadata because the baseline schema has no dedicated rejected timestamp column.

`mark-opened-in-microsoft-project` operates only on `generated` batches and records that a generated artifact has been manually opened in Microsoft Project for review. `verify` operates only after that manual-open step and records manual artifact verification with `verified_at` and `verified_by_user_id`. These endpoints record lifecycle metadata only. They do not automate Microsoft Project, parse artifacts, mutate imported task rows, generate exports, or write back to Microsoft Project.

Lifecycle writes record audit events:

- `export_batch_approved`
- `export_batch_rejected`
- `export_file_generated`
- `export_file_opened_in_microsoft_project`
- `export_file_verified`

The audit rows target the export batch, include previous/new batch status, reference both `project_snapshot_id` and `export_batch_id`, and preserve metadata confirming no Project write-back occurred.

## Controlled Project Handoff Lifecycle

1. Candidate created — master `.mpp` not updated.
2. Candidate approved — master `.mpp` not updated.
3. Export preview created — master `.mpp` not updated.
4. Export batch approved — master `.mpp` not updated.
5. MSPDI/XML artifact generated — master `.mpp` not updated.
6. Artifact opened in Microsoft Project — master `.mpp` not updated.
7. Artifact verified in Microsoft Project — master `.mpp` not updated.
8. Planner manually updates or saves the master `.mpp` — outside Shutdown Tracker automation.

The API records steps one through seven as domain and audit metadata where implemented. It never performs step eight. The first human Microsoft Project round-trip remains pending.

## Export Artifact Handoff Boundary

When persistence is enabled, the API exposes an opt-in worker handoff endpoint for already-approved export batches:

- `POST /api/projects/{projectId}/export-preview/{exportBatchId}/generate-artifact`

The endpoint repeats the complete current policy-1 snapshot, candidate, task, value, source, approval, field, eligibility, and uniqueness validation immediately before worker handoff. Any stale or ambiguous line blocks the complete batch rather than being exported or silently dropped. Final generation acquires locks in this order: export batch; project snapshot; candidates in stable ID order; imported tasks in stable ID order; then current candidate-bound approval rows in stable candidate/event order. Candidate approval takes the project advisory lock, active export batches in stable order, accepted snapshot, candidate, then imported task before inserting an authorizing event. These locks remain held across worker generation and generated-metadata recording, so snapshot, task, candidate, approval authority, and preview membership cannot change between validation and output. The API groups eligible lines by imported task, uses only candidate-derived canonical values and candidate-captured Microsoft Project task UID/ID/name, prepares an export-artifact storage target, and sends a shared `ProjectExportArtifactGenerationRequest` to the project worker. The worker returns a generated artifact URI/hash and summary. The API verifies the worker URI matches the storage-reserved URI, revalidates once more while recording generated metadata, then stores `export_file_uri`, `export_file_hash`, generated metadata, and the `export_file_generated` audit event. No standalone route accepts caller-authored generated artifact metadata.

The default `ProjectExportArtifactJobClient` is intentionally disconnected and throws if called. Set these variables to enable local HTTP handoff:

- `SHUTDOWN_TRACKER_PROJECT_EXPORT_WORKER_ENABLED=true`
- `SHUTDOWN_TRACKER_PROJECT_EXPORT_WORKER_BASE_URL`, default `http://localhost:8081`
- `SHUTDOWN_TRACKER_PROJECT_EXPORT_WORKER_GENERATE_ARTIFACT_PATH`, default `/worker/project-export/generate-artifact`
- `SHUTDOWN_TRACKER_EXPORT_ARTIFACT_STORAGE_LOCAL_ROOT`, default `.shutdown-tracker/export-artifacts`

The API does not generate MSPDI/XML itself, parse Project files, create queue jobs, store artifact bytes in PostgreSQL, automate Microsoft Project reopen, verify artifact contents automatically, mutate imported task rows, calculate schedules, or write back to Microsoft Project. Future asynchronous wrapping should follow the [Worker Handoff Queue Strategy](../../docs/architecture/worker-handoff-queue-strategy.md), keeping the export batch in existing product states while any internal job-run state is tracked separately.

## Audit Event Writes

When `shutdown-tracker.persistence.enabled=true`, the API writes audit rows through `JdbcAuditEventRecorder` into the existing `audit_events` table. The default profile still uses a no-op audit recorder so context-load tests and review smoke deployments can boot without PostgreSQL.

The current audit writer is infrastructure for the first review and export lifecycle mutations only. It does not add task execution events, permission events, offline sync events, authentication, OIDC, or public audit-query endpoints.

## Project Parse Handoff Boundary

The API includes a local-profile handoff boundary for worker-owned parsing:

- `ProjectParseHandoffService` builds a shared `ProjectParseSummaryRequest` from an existing import batch and source-file metadata record.
- The request includes import batch, project, source file, storage URI, and original filename.
- `POST /api/projects/{projectId}/import-batches/{importBatchId}/request-parse-summary` resolves a pending import batch, moves it to `parsing`, requests a worker summary, and records that summary on the import batch as `parsed`.
- The default `ProjectParseJobClient` is intentionally disconnected and throws if called.
- Set `SHUTDOWN_TRACKER_PROJECT_PARSE_WORKER_ENABLED=true` and `SHUTDOWN_TRACKER_PROJECT_PARSE_WORKER_BASE_URL` to enable the HTTP worker client. The default worker base URL is `http://localhost:8081`, and the default path is `/worker/project-import/parse-summary`.

This keeps MPXJ parsing in `services/project-worker`. The API does not parse Project files, create imported snapshots, persist imported task/resource/assignment rows, create live execution records, generate exports, calculate schedule fields, or write back to Microsoft Project.

Future asynchronous wrapping should follow the [Worker Handoff Queue Strategy](../../docs/architecture/worker-handoff-queue-strategy.md): the API validates state and idempotency, the worker owns file processing, and visible import batch status continues to use `pending`, `parsing`, `parsed`, `accepted`, `failed`, and `superseded`.

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

For local source/import/export smoke checks against an already running API, see [Review Smoke Scripts](../../scripts/review/README.md). The default smoke script mode checks health, version, and validation-only source-file handling without storing files or requiring PostgreSQL.

## Local Commands

Run from the repository root when Maven and Java 21 are available:

```text
mvn -pl services/api test
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=local
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=review
```
