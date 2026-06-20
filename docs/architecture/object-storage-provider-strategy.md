# Object Storage Provider Strategy

## Purpose

Shutdown Tracker needs production object storage for immutable Microsoft Project source files, generated MSPDI/XML export artifacts, and future evidence files. This note defines provider selection and configuration guidance only. It does not choose a provider, add an SDK, add migrations, create buckets or containers, or change runtime behavior.

Microsoft Project remains the schedule authority. Object storage supports reviewed import/export workflows; it must not become a live Project feed, scheduler, or uncontrolled write-back path.

## Current Baseline

The API currently has provider-neutral boundaries backed by local filesystem implementations:

- `SourceFileStorage` stores accepted source-file bytes and returns a URI, SHA-256 hash, and size for `source_files` metadata.
- `ExportArtifactStorage` prepares a storage-owned output location for worker-generated MSPDI/XML export artifacts.
- `shutdown-tracker.source-file-storage.local-root` defaults to `.shutdown-tracker/source-files`.
- `shutdown-tracker.export-artifact-storage.local-root` defaults to `.shutdown-tracker/export-artifacts`.

The local implementations are for development and review wiring only. They are not production object storage and should not be used as a production durability or access-control model.

## Storage Classes

| Class | Examples | Required behavior |
| --- | --- | --- |
| Source files | Imported `.mpp`, `.xml`, `.mspdi.xml` files | Immutable after upload, private by default, hash recorded, linked to project/source-file metadata, never parsed in the API. |
| Export artifacts | Worker-generated MSPDI/XML files for approved export batches | Immutable after generation, linked to the export batch, hash recorded, opened and verified manually in Microsoft Project. |
| Evidence files | Future photos, documents, or field attachments | Private by default, project-scoped access, metadata and audit trail in PostgreSQL, binary bytes outside PostgreSQL. |

PostgreSQL should store metadata, ownership, lifecycle state, content hashes, and storage URIs. Object storage should hold the bytes.

## Provider Selection Criteria

A production provider should be selected using these criteria:

- Private buckets or containers with no public listing.
- Encryption at rest by default, with customer-managed keys considered later if pilot requirements demand it.
- Workload identity, managed identity, or OIDC-based credentials preferred over long-lived access keys.
- Short-lived signed URL support for controlled upload/download paths where direct object-store access is needed.
- Object versioning, retention, or immutability controls for source files and export artifacts where practical.
- Lifecycle policy support for retention, archive, and deletion rules.
- Regional data residency that matches customer and project requirements.
- Access logging or audit integration.
- Reasonable local development strategy, such as keeping the existing local filesystem implementation or using a local emulator only in integration tests.
- Operational simplicity for the first production pilot.

Candidate providers can include Azure Blob Storage, Amazon S3, or an S3-compatible object store. This repository should not add provider-specific dependencies until the provider decision is made.

## Configuration Shape

Future provider wiring should keep local storage as the default for development and introduce explicit production configuration. Suggested environment variables:

```text
SHUTDOWN_TRACKER_OBJECT_STORAGE_PROVIDER=local|azure_blob|s3
SHUTDOWN_TRACKER_OBJECT_STORAGE_ENDPOINT=
SHUTDOWN_TRACKER_OBJECT_STORAGE_REGION=
SHUTDOWN_TRACKER_OBJECT_STORAGE_KMS_KEY_ID=
SHUTDOWN_TRACKER_SOURCE_FILE_STORAGE_CONTAINER=
SHUTDOWN_TRACKER_EXPORT_ARTIFACT_STORAGE_CONTAINER=
SHUTDOWN_TRACKER_EVIDENCE_STORAGE_CONTAINER=
```

Use `BUCKET` names instead of `CONTAINER` names if the selected provider is S3-compatible:

```text
SHUTDOWN_TRACKER_SOURCE_FILE_STORAGE_BUCKET=
SHUTDOWN_TRACKER_EXPORT_ARTIFACT_STORAGE_BUCKET=
SHUTDOWN_TRACKER_EVIDENCE_STORAGE_BUCKET=
```

Secrets, access keys, connection strings, service principals, and tokens must stay outside Git. Prefer platform-managed credentials in deployed environments and local developer credentials outside the repository.

## Object Identity

Object keys should avoid real customer names, site names, asset names, work order numbers, and commercial data. Use generated identifiers and sanitized filenames only when a filename is needed for user clarity.

Suggested key patterns:

```text
projects/{projectId}/source-files/{sourceFileId}/{safeFilename}
projects/{projectId}/export-artifacts/{exportBatchId}/{exportBatchId}.mspdi.xml
projects/{projectId}/evidence/{evidenceId}/{safeFilename}
```

Storage URIs should be treated as internal references. User-facing access should go through the API or short-lived signed URLs issued after project/role checks.

## Access Model

Production object storage should follow these rules:

- Buckets or containers are private.
- The API mediates access decisions using project membership, role permissions, and audit policy.
- Workers receive only the object references required for their specific import/export job.
- Signed URLs, when used, are short lived and scoped to one object operation.
- Source-file and export-artifact bytes are never stored in PostgreSQL.
- Generated export artifacts are not committed to Git and are not treated as automated Project verification.

## Implementation Sequence

1. Keep local filesystem storage as the development default.
2. Add provider-neutral configuration properties and tests while preserving existing local behavior.
3. Add one provider implementation behind the existing storage interfaces after the provider decision is made.
4. Add integration tests with an emulator or isolated test bucket/container.
5. Document deployment environment variables and credential setup.
6. Add migrations only if new metadata fields are required; do not store object bytes in PostgreSQL.

## Test Expectations

Future object-store tests should use synthetic byte arrays and generated identifiers only. They should verify:

- Provider selection and local default behavior.
- Object-key normalization and rejection of path traversal.
- Content hash recording.
- Source-file immutability expectations.
- Export-artifact URI/hash recording.
- Private access assumptions and signed URL expiry where supported.

Tests must not commit real Project files, generated export artifacts, customer files, screenshots, secrets, or provider credentials.

## Non-Goals

This strategy does not add:

- Object-store SDK dependencies.
- Cloud resources, buckets, containers, or deployment secrets.
- File parsing, MPXJ execution in the API, or worker queue processing.
- Scheduler logic, CPM, critical path, float, resource levelling, recovery scheduling, or automatic date movement.
- Live Microsoft Project feed or uncontrolled Project write-back.
- Generated artifacts, real Project files, screenshots, or seed data.
