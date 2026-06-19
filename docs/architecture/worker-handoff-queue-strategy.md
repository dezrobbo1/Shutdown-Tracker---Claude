# Worker Handoff Queue Strategy

Shutdown Tracker uses the API service for workflow ownership and the project worker for Microsoft Project file processing. The queue/background-job layer should make existing handoffs reliable without turning the API into a parser or turning the worker into the product workflow authority.

## Current Baseline

The repository already has two synchronous, opt-in local handoff paths:

- Import parse summary: `POST /api/projects/{projectId}/import-batches/{importBatchId}/request-parse-summary` calls the worker endpoint `POST /worker/project-import/parse-summary`.
- Export artifact generation: `POST /api/projects/{projectId}/export-preview/{exportBatchId}/generate-artifact` calls the worker endpoint `POST /worker/project-export/generate-artifact`.

The default API worker clients are disconnected. Local HTTP clients are enabled only through explicit configuration. No queue, background jobs, broker, scheduler, worker persistence, or production object-store provider exists yet.

## Scope

This strategy covers future asynchronous handoff for:

- Worker-owned MPXJ import parsing from an already stored source file.
- Worker-owned MSPDI/XML export artifact generation from an already approved export batch.

It does not introduce scheduler logic, CPM, critical path, float, resource levelling, recovery scheduling, automatic date movement, live Microsoft Project feeds, native MPP writing, or uncontrolled Project write-back.

## Ownership Rules

The API service owns product workflow state:

- Project-scoped access checks.
- Source-file metadata and import batch ownership.
- Import batch state transitions using existing `import_batch_status` values.
- Project snapshot review state using existing `project_snapshot_status` values.
- Export batch lifecycle using existing export batch states.
- Audit event recording for user-visible workflow decisions.
- Idempotency and correlation for accepted requests.

The project worker owns file processing:

- Reading an explicit stored source-file URI for import.
- Running MPXJ parsing and returning parse output or summary data.
- Creating MSPDI/XML export artifacts from explicit approved leaf-task candidates.
- Returning generated artifact URI/hash and summary metadata.

The worker should not approve imports, approve exports, create live execution records, calculate schedules, move dates, or write back to Microsoft Project.

## Status Model

Do not add `queued`, `running`, or `completed` values to product enums.

Use the existing visible workflow states:

| Area | Existing visible states |
| --- | --- |
| Import batches | `pending`, `parsing`, `parsed`, `accepted`, `failed`, `superseded` |
| Export batches | `draft_preview`, `awaiting_approval`, `approved`, `rejected`, `generated`, `opened_in_microsoft_project`, `verified`, `superseded`, `failed` |

If a later implementation needs durable job tracking, add a separate job-run concept rather than overloading product state. Job-run state may use internal values such as `queued`, `running`, `succeeded`, `failed`, and `cancelled`, but those values must not replace import batch or export batch lifecycle states.

For import parsing, an accepted background request may move the import batch from `pending` to `parsing`. It reaches `parsed` only after worker output is recorded, or `failed` after a terminal worker failure is recorded.

For export artifact generation, the export batch should remain `approved` while a background worker job is queued or running. It reaches `generated` only after the API records the returned artifact URI/hash, or `failed` after terminal generation failure.

## Candidate Job Types

| Job type | Trigger | API precondition | Worker action | API completion |
| --- | --- | --- | --- | --- |
| `project_import_parse` | Stored source file has a pending import batch and parsing is requested. | Import batch is `pending`; source file belongs to same project. | Read stored Project file, parse with MPXJ, return parse summary and later parsed entities when that contract exists. | Record parse metadata, warnings/errors, and later immutable snapshot rows; mark batch `parsed` or `failed`. |
| `project_export_artifact_generation` | Approved export batch artifact generation is requested. | Export batch is `approved` and has eligible leaf-task lines. | Generate MSPDI/XML artifact at the API-reserved storage target and return URI/hash/summary. | Verify returned URI matches reserved target, record artifact URI/hash, mark batch `generated` or `failed`. |

## Request Payload Expectations

Queue messages should be small references, not embedded source files or generated artifacts.

Import jobs should reference:

- `project_id`
- `import_batch_id`
- `source_file_id`
- stored source-file URI
- original filename
- request idempotency/correlation key

Export artifact jobs should reference:

- `project_id`
- `export_batch_id`
- `project_snapshot_id`
- approved leaf-task update candidates
- original Microsoft Project task UID/ID values needed for MSPDI/XML output
- API-reserved artifact output URI/path
- request idempotency/correlation key

The existing shared Java contracts should remain the starting point for the HTTP and future queued payloads. If queue envelopes are added, they should wrap those contracts rather than creating a parallel shape.

## Failure and Retry Rules

Retries must be idempotent and bounded.

- Retrying an import parse job must not create duplicate source files, import batches, project snapshots, imported tasks, resources, assignments, or extended attributes.
- Retrying export generation must not create multiple visible export batches or record a generated artifact against the wrong storage target.
- Transient worker, network, or storage failures may retry under the same idempotency key.
- Terminal validation failures should move the owning import batch or export batch to `failed` with a clear failure reason where the current schema supports it.
- Superseded batches should not continue processing. A worker completion for a superseded product record must be ignored or recorded as stale failure metadata, not applied as current state.

## Audit Expectations

Queue implementation should preserve auditability without making low-level retry noise look like user workflow.

Audit events should record user-visible decisions and accepted workflow requests, such as a parse request accepted by the API, an export generation request accepted by the API, generated artifact metadata recorded, manual Microsoft Project open, and manual verification.

Internal retry attempts, poll loops, and provider delivery events should be job-run telemetry unless a failure changes product state.

## Provider Selection Criteria

A future queue provider should be selected only after the handoff contract is stable enough to justify implementation. Evaluate:

- Local development support without secrets.
- Durable delivery with bounded retries and dead-letter handling.
- Idempotency-key support or a straightforward way to enforce idempotency in the API.
- Ability to carry small JSON payloads and correlate logs/audit events.
- Operational simplicity for the first review/staging deployment.
- Clear migration path from the current synchronous HTTP handoff.

Do not select a provider in this strategy. Candidate provider selection and runtime configuration should be a later PR.

## Implementation Sequence

1. Keep current synchronous HTTP handoffs as the local/review baseline.
2. Add durable job-run metadata only if needed for retries, observability, or provider acknowledgement.
3. Add an enqueue boundary in the API that validates product state and writes an idempotent job request.
4. Add a worker consumer that reuses the existing import/export handoff services.
5. Add completion handling that updates existing import/export workflow records through the API-owned services.
6. Add dead-letter/failure handling that moves product records to existing `failed` states only when failure is terminal.

Each implementation PR should include tests proving no API-side Project parsing, no duplicate product records on retry, no new product enum values, no Project write-back, and no schedule calculation.
