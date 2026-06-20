# Review Smoke Scripts

This folder contains local reviewer scripts for narrow source/import/export smoke checks.

## Source/Import/Export Smoke Script

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\review\source-import-export-smoke.ps1
```

Default behavior is read/validation-only:

- `GET /actuator/health`
- `GET /api/version`
- `POST /api/source-files/validate` using the approved synthetic MSPDI fixture

The default run does not require PostgreSQL, store a file, create an import batch, call the worker, create an export preview, generate an artifact, automate Microsoft Project, or write back to Microsoft Project.

## Optional Local Writes

Write steps require `-AllowWrites` plus explicit switches. They are intended only for local or review environments with synthetic data:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\review\source-import-export-smoke.ps1 `
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
- `-CreateExportPreview`: creates a draft export preview from supplied `-SnapshotId`, `-ImportedTaskId`, and optional source entity arguments.
- `-ReadExportPreview`: reads an export preview by `-ExportBatchId`.
- `-ApproveExportBatch`: approves a draft export batch when it already has at least one eligible line.
- `-GenerateExportArtifact`: calls the worker-backed artifact generation endpoint for an approved batch.

`-ApproveExportBatch` and `-GenerateExportArtifact` require already prepared eligible synthetic review data and local worker/API configuration. They do not perform manual Microsoft Project verification.

## Safety Rules

- Use only synthetic or approved sanitized inputs.
- Do not use real Project files, real schedules, screenshots, generated exports from real schedules, secrets, or customer data.
- Keep generated MSPDI/XML artifacts outside Git or under ignored local folders.
- Do not treat API artifact generation as Microsoft Project verification.
- Do not use this script to create production seed data.
