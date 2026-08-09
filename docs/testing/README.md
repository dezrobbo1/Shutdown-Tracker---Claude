# Testing

Testing should protect the Microsoft Project authority boundary, immutable imported snapshots, execution/review integrity, auditability, permissions, offline safety, and controlled export correctness.

This document describes durable test policy. Exact test-class inventories belong in the source tree and CI results rather than being copied here.

## Core validation layers

### Backend

Run from the repository root when Java 21 and Maven are available:

```text
mvn test
```

Backend tests should cover service/domain validation, persistence boundaries, API contracts, audit events, import/export handoffs, review/approval state transitions, and rejection of scheduler-like or uncontrolled write-back behavior.

Persistence and transaction claims that depend on PostgreSQL must use real PostgreSQL, the real Spring transaction proxy, and the JDBC implementation rather than H2 or fake repositories. The export-integrity integration suite uses a uniquely named repository-local Docker container, applies V001-V007 through Flyway, exercises controlled local HTTP worker failure and rollback, and must clean up its exact container and temporary artifact storage on exit. Fake-repository tests remain useful unit evidence but are not PostgreSQL or transaction-boundary evidence.

### Frontend

Run:

```text
npm test
npm run build
```

Frontend tests should cover application rendering, critical workflow state/copy, API client contracts, validation logic, and explicit absence of unintended schedule-authoring behavior.

### Database migrations

Versioned migrations live under [`infra/migrations`](../../infra/migrations).

Validate them against a clean PostgreSQL database using:

```text
./scripts/db/validate-migrations.sh
```

or Windows PowerShell:

```text
.\scripts\db\validate-migrations.ps1
```

Migration changes should verify ordering, constraints, indexes, upgrade safety where relevant, and compatibility with the product boundary. Applied migrations must not be rewritten merely to simplify history.

## Import/export fixture policy

Use [Import/Export Fixture Strategy](import-export-fixture-strategy.md).

- Do not commit real customer/site schedules or operational data.
- Prefer synthetic text/XML fixtures and text expected-output files.
- Keep generated export artifacts temporary and uncommitted.
- Validate MPXJ parsing, stable task/resource/assignment identity fields, relevant extended attributes, warnings, and export allowlisting.
- The approved synthetic MSPDI fixture lives under `fixtures/import-export/synthetic-basic-wbs/`.

## Microsoft Project round-trip validation

Automated MSPDI/XML generation tests do not replace human Microsoft Project verification.

Use [Manual Microsoft Project Round-Trip Evidence](manual-microsoft-project-round-trip-evidence.md) for text-only evidence of representative reopen checks. The planner remains responsible for deciding whether a verified artifact is applied/saved into the master `.mpp`.

Never commit real Project files, generated artifacts, screenshots, or confidential schedule data as round-trip evidence.

## Import and snapshot tests

Tests should verify:

- source validation and storage boundaries;
- immutable source/snapshot semantics;
- imported task/resource/assignment/extended-attribute persistence;
- parse warning/count handling;
- snapshot review and lineage rules;
- no API-side Project parsing when the worker owns that responsibility;
- no automatic uncertain lineage remapping.

## Progress/review/export tests

As the corresponding features are implemented, tests should verify:

- field progress does not bypass supervisor/planner review;
- export eligibility remains limited to explicitly approved candidates;
- summary-task and unsupported-field export attempts are rejected;
- candidate/task/source/approval identities cannot be substituted or become stale before artifact generation;
- export generation remains request-specific and allowlisted;
- artifact metadata and verification state are auditable;
- policy-1 lifecycle facts and structured provenance cannot be rewritten or replaced by caller metadata;
- Microsoft Project open and verification actor/time identity remains authoritative through terminal verification;
- no endpoint or worker operation silently updates Microsoft Project.

## Permission and audit tests

Verify project-scoped authorization, least-privilege behavior, review/export authority, evidence access, delegation boundaries, and immutable audit records for material actions.

Imported category/classification membership must never become an implicit permission grant.

## Offline and sync tests

Offline-capable field workflows should verify:

- queued is visibly distinct from submitted/synced;
- operations are idempotent/replay-safe;
- retries are visible and recoverable;
- Background Sync is not required for correctness;
- conflicts do not silently discard field updates;
- server acknowledgements are required before local work is represented as synced.

## Worker handoff tests

Use [Worker Handoff Queue Strategy](../architecture/worker-handoff-queue-strategy.md) when asynchronous handoff is introduced.

Tests should preserve API ownership of workflow/audit state, worker ownership of file processing, retry idempotency, and the existing product status model. Transport/job states must not leak into product semantics without an explicit design change.

## Object-storage tests

Use [Object Storage Provider Strategy](../architecture/object-storage-provider-strategy.md). Provider tests must use synthetic bytes/IDs and isolated test storage or emulators. Never commit provider credentials, real Project files, evidence, or generated operational artifacts.

## Seeded review/demo data

Use [Seeded Review and Demo Data Strategy](seeded-review-demo-data-strategy.md). Seed data must be synthetic, disabled by default, reset-safe, and kept out of production migrations.

## CI

The GitHub Actions workflow under [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) is the repository CI entry point. It currently covers the Maven backend suite, frontend tests/builds, and clean-database migration validation.

CI results and source code are the authority for the exact current test inventory.

## Manual/E2E expansion

As workflows become production-capable, add end-to-end coverage for representative import, execution, problem/action, evidence, handover, review, export, verification, and offline-sync paths. Keep schedule calculation, automatic Project movement, and uncontrolled write-back out of those workflows.
