# Review Smoke Scripts

This folder contains local reviewer scripts for narrow source/import/export smoke checks.

## Source/Import/Export Smoke Script

Run from the repository root:

```powershell
powershell -NoProfile -File .\scripts\review\source-import-export-smoke.ps1
```

Default behavior is read/validation-only:

- `GET /actuator/health`
- `GET /api/version`
- `POST /api/source-files/validate` using the approved synthetic MSPDI fixture

The default run does not require PostgreSQL, store a file, create an import batch, create an export candidate or approval event, call the worker, create an export preview, generate an artifact, automate Microsoft Project, or write back to Microsoft Project.

## Optional Local Writes

Write steps require `-AllowWrites` plus explicit switches. They are intended only for local or review environments with synthetic data:

```powershell
powershell -NoProfile -File .\scripts\review\source-import-export-smoke.ps1 `
  -ApiBaseUrl http://localhost:8080 `
  -AllowWrites `
  -ProjectId <project-id> `
  -RunProjectUpload
```

Optional switches:

- `-RunProjectUpload`: stores the approved synthetic MSPDI fixture through the source-file storage abstraction and creates a pending import batch.
- `-RunParseHandoff`: requests worker parse-summary handoff for an existing or just-created import batch.
- `-ListImportSnapshots`: reads import-review snapshot summaries for a project.
- `-ReadImportSnapshot`: reads one import-review snapshot by `-SnapshotId`.
- `-CreateExportCandidate`: creates an approval-neutral candidate for `-SnapshotId`, `-ImportedTaskId`, `-CandidateFieldName`, and `-CandidateProposedValue`. The field is limited to `percent_complete`, `actual_start`, or `actual_finish`. The server derives the baseline, Project task identity, normalized value, fingerprint, and leaf-task state. The script generates a synthetic source identity unless `-CandidateSourceEntityId` is supplied.
- `-ApproveExportCandidate`: appends a separate `APPROVED_FOR_EXPORT` planner event bound to `-AuthoritativeExportCandidateId`, which may be returned by `-CreateExportCandidate` earlier in the same run or supplied explicitly for existing synthetic review data.
- `-CreateExportPreview`: creates a draft export preview from `-SnapshotId` and `-AuthoritativeExportCandidateId`; the request contains candidate IDs only and never caller-authored preview lines.
- `-ReadExportPreview`: reads an export preview by `-ExportBatchId`.
- `-ApproveExportBatch`: approves a draft export batch when it already has at least one eligible line.
- `-GenerateExportArtifact`: calls the worker-backed artifact generation endpoint for an approved batch.

The guarded synthetic candidate-to-preview sequence is:

```powershell
powershell -NoProfile -File .\scripts\review\source-import-export-smoke.ps1 `
  -ApiBaseUrl http://localhost:8080 `
  -AllowWrites `
  -ProjectId <project-id> `
  -SnapshotId <accepted-snapshot-id> `
  -ImportedTaskId <leaf-imported-task-id> `
  -CreateExportCandidate `
  -CandidateFieldName percent_complete `
  -CandidateProposedValue 75 `
  -ApproveExportCandidate `
  -CreateExportPreview
```

Supplying `-AuthoritativeExportCandidateId` without `-CreateExportCandidate` is supported only for an existing current-policy candidate backed by synthetic or approved sanitized review data. Add `-ApproveExportCandidate` only when appending a new planner decision is intentional. `-ApproveExportBatch` and `-GenerateExportArtifact` require eligible synthetic review data and local worker/API configuration. They do not perform manual Microsoft Project verification.

## Safety Rules

- Use only synthetic or approved sanitized inputs.
- Do not use real Project files, real schedules, screenshots, generated exports from real schedules, secrets, or customer data.
- Keep generated MSPDI/XML artifacts outside Git or under ignored local folders.
- Do not treat API artifact generation as Microsoft Project verification.
- Do not use this script to create production seed data.
