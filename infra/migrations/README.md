# Database Migrations

This directory contains the PostgreSQL schema migrations used by Shutdown Tracker. The files follow Flyway version naming and must be applied once in ascending version order.

## Current baseline

The baseline is `V001` through `V007` and creates 21 application tables:

- `V001__baseline_extensions_and_enums.sql`: enables `pgcrypto` and defines the initial enum types; creates no tables.
- `V002__projects_snapshots_and_imports.sql`: creates `projects`, `source_files`, `import_batches`, and `project_snapshots`.
- `V003__imported_project_entities.sql`: creates `imported_tasks`, `imported_resources`, `imported_assignments`, `imported_extended_attributes`, and `task_lineage_links`.
- `V004__audit_events.sql`: creates the append-only-by-application-rule `audit_events` table.
- `V005__approval_and_export_batches.sql`: creates `approval_records`, `export_batches`, and `export_batch_lines`.
- `V006__critical_watchlists_reporting.sql`: creates `critical_watchlists`, `critical_work_packages`, `critical_work_package_sources`, `reporting_policy_versions`, `reporting_periods`, `critical_updates`, and `critical_update_lines`.
- `V007__enforce_export_candidate_integrity.sql`: creates `export_candidate_records`; preserves V006 export history as unversioned, read-only records; and makes policy 1 the current export-integrity policy with immutable candidate facts, separate candidate-bound approval history, sealed preview membership, exact task/field/value authority, deterministic event ordering, authoritative Microsoft Project open identity, and database-enforced lifecycle/provenance immutability.

Critical Watchlists and Critical Work Packages are reporting constructs. They do not calculate critical path, float, or recovery schedules.

## Versioned export-integrity policy

V007 does not delete, merge, or rewrite V006 approval, batch, or line values. Existing export batches and lines retain a null policy version and remain readable, including generated, opened, verified, rejected, and superseded history, but they are frozen against further lifecycle writes. A legacy draft or approved batch requires a fresh preview under the current policy before it can progress.

New candidate-bound approval records receive a database-assigned event order; legacy approval records remain unsequenced. Tied legacy records do not acquire invented chronology and remain ambiguous and non-exportable. Progression requires a fresh authoritative candidate and a new candidate-bound ordered approval event.

Policy-1 export batches allow one same-state change only: sealing a complete draft preview without any unrelated mutation. Every status change has an explicit allowed-column delta. Earlier lifecycle facts cannot be rewritten, and rejected, failed, superseded, and verified records are terminal. Microsoft Project open actor/time use dedicated authoritative columns. Lifecycle metadata is divided into stable server-owned sections; caller values are nested under `clientMetadata`, while worker/storage facts are retained under generation `provenance`. Later transitions append their own section and cannot replace earlier approval, artifact, open, or verification facts.

Policy-1 candidates are approval-neutral when created. PostgreSQL captures the accepted snapshot, imported task, Microsoft Project UID/ID/name, leaf state, normalized old/new value, source identity/version, and a canonical source-event or payload fingerprint. Candidate creation does not imply approval. Every new approval, rejection, `correction_requested`, or supersession event is append-only and bound to exactly one candidate, and current authority is the latest ordered event for that candidate.

New preview requests select candidate IDs only. Policy-1 lines permit at most one candidate per imported task and field. Only `percent_complete`, `actual_start`, and `actual_finish` may be export eligible. `physical_percent_complete` remains readable internally and historically but is never policy-1 export eligible. A batch starts unsealed, accepts lines only while it remains a draft, and can be sealed only once; candidate and line history is append-only.

Preview materialization, batch approval, artifact generation, and generated-metadata recording revalidate the accepted snapshot, current task identity and baseline value, exact candidate payload/fingerprint, latest candidate-bound approval identity/state, field authority, and eligibility. Final generation locks the export batch, snapshot, candidates in stable ID order, imported tasks in stable ID order, then current candidate-bound approval rows in stable candidate/event order until generated metadata commits. Candidate-approval insertion uses the same batch-first ordering for active previews.

Proposed-value normalization is deterministic across the database, API, shared contract, and worker. Whole-number percent equivalents such as `75`, `75.0`, and `075` canonicalize to `75`. Proposed actual dates require ISO-8601 minute- or second-precision values with an explicit offset and canonicalize to whole seconds while retaining the reviewed Microsoft Project local wall-clock component. Omitted seconds become `:00`. A fractional component is accepted only when it contains one through six digits and every digit is zero; that zero-valued fraction canonicalizes away. Non-zero fractions, fractions outside the one-to-six-digit input range, offset-free values, and invalid values are rejected. The worker uses the normalized local component without converting it to UTC. Imported actual baselines use a separate canonicalizer that retains available microsecond precision for exact drift comparison and never enters the worker request as a proposed value.

## Runtime use

The API and project worker `local` profiles enable Flyway with:

```text
filesystem:infra/migrations
```

Start those services from the repository root so the relative location resolves correctly. The local datasource defaults match [`../docker/docker-compose.postgres.yml`](../docker/docker-compose.postgres.yml).

The validation scripts do not run Flyway or create Flyway schema-history records. They apply each SQL file directly with containerized `psql` in its own PostgreSQL transaction against a clean database, fail on SQL errors, roll back the complete failing file, and verify all 21 expected tables. The same commands then run a populated V006 upgrade, policy-1 candidate/approval assertions, synchronized PostgreSQL concurrency checks, and an intentionally failed V007 transaction check. The CI wrapper removes its validation container and volume on every exit.

## Validate locally

Prerequisite: Docker with Docker Compose support. A host `psql` installation is not required.

Unix-like systems, from the repository root:

```sh
./scripts/db/validate-migrations.sh
```

Windows PowerShell, from the repository root:

```powershell
.\scripts\db\validate-migrations.ps1
```

Both scripts reset the named validation volume before applying `infra/migrations/V*.sql` in sorted order. They then create isolated temporary databases for synthetic populated-upgrade, current-policy, concurrency, and atomicity validation, remove those databases when the suite exits, and tear down the Compose container and volume even when a check fails.

## Migration rules

- Add the next monotonically increasing `V###__description.sql` file; never rename or rewrite an applied migration.
- Keep migrations reviewable and use explicit keys, constraints, indexes, and comments.
- Do not place seed data, secrets, real Project files, generated export artifacts, or other operational data in schema migrations.
- Treat imported Microsoft Project rows as immutable snapshot facts. Microsoft Project remains the schedule authority.
- Preserve reviewed, approved, batch-oriented export handling; do not introduce scheduler ownership or uncontrolled Project write-back.
