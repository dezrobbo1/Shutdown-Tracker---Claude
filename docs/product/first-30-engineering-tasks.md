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
- Task 7, scaffold Project worker: addressed as a service shell with a worker-only MPXJ import summary spike. The worker has no persistence, upload endpoint, background jobs, queue integration, export generation, Project write-back, or scheduler logic.
- Task 8, configure PostgreSQL locally: addressed for local validation and Spring Boot `local` profile runtime wiring. This is not production database setup.
- Task 9, add migrations: addressed by PR #3 with baseline SQL migrations in `infra/migrations`.
- Task 10, create source-file, import-batch, snapshot, task, and audit tables: addressed by PR #3 as baseline SQL.
- Task 11, add object-storage abstraction: partially addressed for source files by an internal API storage abstraction with a local filesystem implementation. This is not production object storage and is not yet called by an upload endpoint.
- Review project bootstrap and source-file metadata persistence: partially addressed by local-profile API services using the existing `projects` and `source_files` tables. No public upload/storage endpoint exists yet.
- Import batch persistence: partially addressed by local-profile API services using the existing `import_batches` table and `import_batch_status` enum values. No public import-batch endpoint exists yet.
- Import summary persistence: partially addressed by API services that record a worker parse summary response into existing `import_batches` columns and `parse_summary` JSONB, then mark the batch `parsed`. No parser execution, queue job, or public endpoint exists yet.
- Project snapshot and imported entity persistence: partially addressed by transactional API services that create immutable parsed snapshots and imported task/resource/assignment/extended-attribute rows in existing baseline tables.
- Import review API: partially addressed by local-profile endpoints for listing parsed snapshots, reviewing imported tasks/resources/assignments/extended attributes, and accepting or rejecting parsed snapshots with existing status values.
- Task lineage review persistence: partially addressed by local-profile endpoints and JDBC services for creating concrete task-to-task lineage links between imported snapshots and accepting or rejecting suggested links with existing review-state values. No automatic lineage matching, live execution state, parser execution, upload orchestration, export generation, or Project write-back exists yet.
- Export preview model: partially addressed by local-profile endpoints and JDBC services that create `draft_preview` export batches and preview line eligibility for explicit approved-source candidates. Export batch approval orchestration is partially addressed by local-profile approve/reject endpoints and a generated-artifact metadata endpoint using existing export batch status values. No worker-backed MSPDI/XML artifact generation, frontend/UI, live task update source tables, Project verification workflow, or Project write-back exists yet.
- Audit event writes for first review/export mutations: partially addressed by local-profile API wiring that records audit rows for import snapshot accept/reject decisions, task lineage link create/accept/reject decisions, export preview creation, export batch approval/rejection, and generated artifact metadata recording using the existing `audit_events` table. No public audit query API, task execution audit events, permission audit events, or offline sync audit events exist yet.
- Task 18, add API client package: partially addressed by a shared TypeScript client for current import review, task lineage review, export preview, and export batch lifecycle endpoints. It does not implement auth, live data fetching, offline sync, upload orchestration, worker calls, export artifact generation, or Project write-back.
- Tasks 14 and 15, scaffold React/Vite console and mobile PWA: addressed as frontend shells. The console imports the shared API client surface but still renders synthetic scaffold data by default; the apps have no task execution writes, offline queue, storage, parser execution, export generation, or Project write-back.
- Worker parse handoff: partially addressed by shared Java request/response records, an API request builder with a disconnected job client, and a worker service that maps local source file URIs to MPXJ summary output. No queue, background job, public endpoint, or API-side parsing exists yet.
- Task 19, implement Project upload endpoint: partially addressed by a validation-only source-file upload placeholder with hardened multipart error responses. The validation endpoint validates multipart metadata and extension only; it does not store files, create import batches, persist metadata, call MPXJ, integrate with the worker, or write back to Microsoft Project.
- Task 20, implement MPXJ parse spike for MPP/MSPDI: partially addressed by a worker-only import summary spike. It reads an explicitly configured local path and returns counts only; no real fixtures, persistence, export, upload endpoint, or write-back exists.
- Task 29, generate first MSPDI export artifact: partially addressed by a worker-only synthetic MSPDI/XML artifact generation service and local runner. It accepts explicit leaf-task progress/actual candidates, writes only to an explicit local path, and does not persist generated artifact metadata, approve export batches, call Microsoft Project, or write back to Microsoft Project.
- Import/export fixture strategy and parser expected-output expansion: addressed with safe fixture policy, text-only manifest schema, synthetic expected-output examples, the first approved synthetic MSPDI import fixture, and structural worker-response expected-output tests.
- Approval/export batch schema: addressed as baseline SQL.
- Critical Watchlist reporting schema: addressed as baseline SQL.

This repository now has a minimal Maven/Spring Boot backend scaffold with PostgreSQL JDBC, Flyway runtime wiring, a worker-only MPXJ import summary spike, a worker-only synthetic MSPDI/XML export artifact spike, an API-only source-file validation placeholder, an internal source-file storage abstraction, local-profile services for review project bootstrap, source-file metadata persistence, import batch persistence, import summary persistence, project snapshot/imported entity persistence, import review API endpoints, task lineage review persistence, export preview model endpoints, export batch approval/generated-metadata orchestration, audit event writes for the first review/export mutations, a shared TypeScript API client for import/export review surfaces, a shared parse summary handoff contract, structural expected-output checks for the approved synthetic MSPDI fixture, and React/Vite console/mobile PWA shells. It still does not add live frontend data fetching, seed data, real Project files, Project write-back, queue integration, automatic lineage matching, live execution state, mobile offline queues, worker-backed export generation, Project verification workflow, public audit query APIs, or a production database setup.

Migrations remain under `infra/migrations`; local migration validation remains under `scripts/db`; Spring Boot `local` profiles point Flyway to `filesystem:infra/migrations`.
