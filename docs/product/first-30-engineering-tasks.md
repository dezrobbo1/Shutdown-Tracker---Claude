# First 30 Engineering Tasks

1. Create repo scaffold.
2. Add concept, ADR, and security docs.
3. Write boundary statement.
4. Draft permission matrix.
5. Draft audit-event schema.
6. Scaffold Spring Boot API.
7. Scaffold Project worker.
8. Configure PostgreSQL locally.
9. Add migrations.
10. Create source-file, import-batch, snapshot, task, and audit tables.
11. Add object-storage abstraction.
12. Add OIDC stub.
13. Add role and project-permission model.
14. Scaffold React and Vite console.
15. Scaffold React and Vite mobile PWA.
16. Add shared TypeScript types.
17. Add shared validation package.
18. Add API client package.
19. Implement Project upload endpoint.
20. Implement MPXJ parse spike for MPP/MSPDI.
21. Store immutable source file and parse warnings.
22. Persist first task/resource/assignment snapshot.
23. Build console task-list screen.
24. Build mobile My Work screen.
25. Implement task event APIs.
26. Implement IndexedDB queue.
27. Implement evidence metadata/upload queue.
28. Build export preview endpoint/UI.
29. Generate first MSPDI export artifact.
30. Run manual Project reopen test.

## Status Notes

- Task 6, scaffold Spring Boot API: addressed as a placeholder service shell only. The API currently exposes Actuator plus `GET /api/version`; no domain, task, import, export, authorization, or scheduler logic exists.
- API review smoke profile: addressed for backend smoke deployment only. The `review` profile boots without PostgreSQL and supports health, version, and validation-only source-file checks; it is not production deployment or product workflow review.
- Task 7, scaffold Project worker: addressed as a service shell with a worker-only MPXJ import summary spike and worker-only MSPDI/XML export artifact handoff. The worker has no persistence, upload endpoint, background jobs, queue integration, Project write-back, or scheduler logic.
- Task 8, configure PostgreSQL locally: addressed for local validation and Spring Boot `local` profile runtime wiring. This is not production database setup.
- Task 9, add migrations: addressed by PR #3 with baseline SQL migrations in `infra/migrations`.
- Task 10, create source-file, import-batch, snapshot, task, and audit tables: addressed by PR #3 as baseline SQL.
- Task 11, add object-storage abstraction: partially addressed for source files by an internal API storage abstraction with a local filesystem implementation. This is not production object storage.
- Review project bootstrap and source-file metadata persistence: partially addressed by local-profile API services using the existing `projects` and `source_files` tables. The upload orchestration endpoint now creates source-file metadata for accepted uploads.
- Import batch persistence: partially addressed by local-profile API services using the existing `import_batches` table and `import_batch_status` enum values. The upload orchestration endpoint now creates pending import batches for accepted source files.
- Import summary persistence: partially addressed by a local-profile API endpoint that can request a worker parse summary for a pending import batch, record the response into existing `import_batches` columns and `parse_summary` JSONB, then mark the batch `parsed`. No API-side parser execution, queue job, imported snapshot creation, or imported task/resource/assignment persistence exists yet.
- Project snapshot and imported entity persistence: partially addressed by transactional API services that create immutable parsed snapshots and imported task/resource/assignment/extended-attribute rows in existing baseline tables.
- Import review API: partially addressed by local-profile endpoints for listing parsed snapshots, reviewing imported tasks/resources/assignments/extended attributes, and accepting or rejecting parsed snapshots with existing status values.
- Task lineage review persistence: partially addressed by local-profile endpoints and JDBC services for creating concrete task-to-task lineage links between imported snapshots and accepting or rejecting suggested links with existing review-state values. No automatic lineage matching, live execution state, parser execution, export generation, or Project write-back exists yet.
- Export preview model: partially addressed by local-profile endpoints and JDBC services that create `draft_preview` export batches and preview line eligibility for explicit approved-source candidates. Export batch approval orchestration is partially addressed by local-profile approve/reject endpoints, a generated-artifact metadata endpoint, and a worker-backed artifact generation handoff endpoint using existing export batch status values. No frontend/UI, live task update source tables, Project verification workflow, or Project write-back exists yet.
- Audit event writes for first review/export mutations: partially addressed by local-profile API wiring that records audit rows for import snapshot accept/reject decisions, task lineage link create/accept/reject decisions, export preview creation, export batch approval/rejection, and generated artifact metadata recording using the existing `audit_events` table. No public audit query API, task execution audit events, permission audit events, or offline sync audit events exist yet.
- Task 18, add API client package: partially addressed by a shared TypeScript client for source-file upload orchestration, current import review, task lineage review, export preview, export batch lifecycle endpoints, and the API export-artifact generation handoff endpoint. It does not implement auth, live data fetching, offline sync, direct worker calls, or Project write-back.
- Tasks 14 and 15, scaffold React/Vite console and mobile PWA: addressed as frontend shells. The console imports the shared API client surface but still renders synthetic scaffold data by default; the apps have no task execution writes, offline queue, storage, parser execution, export generation, or Project write-back.
- Worker parse handoff: partially addressed by shared Java request/response records, a local-profile API trigger for pending import batches, a default disconnected API client, an opt-in HTTP API client, and a worker endpoint that maps local source file URIs to MPXJ summary output. No queue, background job, imported snapshot creation, imported task/resource/assignment persistence, or API-side parsing exists yet.
- Task 19, implement Project upload endpoint: partially addressed by a validation-only source-file upload placeholder plus local-profile upload orchestration. The project-scoped upload endpoint validates, stores accepted bytes through the storage abstraction, creates `source_files` metadata, creates a pending import batch, and records `source_file_uploaded`. It does not call MPXJ, parse files, integrate with the worker, create snapshots, import tasks, generate exports, or write back to Microsoft Project.
- Task 20, implement MPXJ parse spike for MPP/MSPDI: partially addressed by a worker-only import summary spike. It reads an explicitly configured local path and returns counts only; no real fixtures, persistence, export, upload endpoint, or write-back exists.
- Task 29, generate first MSPDI export artifact: partially addressed by a worker-only synthetic MSPDI/XML artifact generation service, local runner, worker HTTP endpoint, shared Java export handoff contract, and API handoff endpoint for approved export batches. The API records returned artifact URI/hash through the existing generated lifecycle path. This still does not call Microsoft Project, verify reopen results, create production object storage, or write back to Microsoft Project.
- Import/export fixture strategy and parser expected-output expansion: addressed with safe fixture policy, text-only manifest schema, synthetic expected-output examples, the first approved synthetic MSPDI import fixture, and structural worker-response expected-output tests.
- Approval/export batch schema: addressed as baseline SQL.
- Critical Watchlist reporting schema: addressed as baseline SQL.

This repository now has a minimal Maven/Spring Boot backend scaffold with PostgreSQL JDBC, Flyway runtime wiring, a worker-only MPXJ import summary spike, a worker-only synthetic MSPDI/XML export artifact spike, an API-only source-file validation placeholder, an internal source-file storage abstraction, local-profile source-file upload orchestration, local-profile services for review project bootstrap, source-file metadata persistence, import batch persistence, worker parse-summary handoff, import summary persistence, project snapshot/imported entity persistence, import review API endpoints, task lineage review persistence, export preview model endpoints, export batch approval/generated-metadata orchestration, worker-backed export artifact handoff, audit event writes for upload/review/export mutations, a shared TypeScript API client for upload and import/export review surfaces, shared Java parse/export handoff contracts, structural expected-output checks for the approved synthetic MSPDI fixture, and React/Vite console/mobile PWA shells. It still does not add live frontend data fetching, seed data, real Project files, Project write-back, queue integration, parser execution in the API, automatic lineage matching, live execution state, mobile offline queues, Project verification workflow, public audit query APIs, or a production database setup.

Migrations remain under `infra/migrations`; local migration validation remains under `scripts/db`; Spring Boot `local` profiles point Flyway to `filesystem:infra/migrations`.
