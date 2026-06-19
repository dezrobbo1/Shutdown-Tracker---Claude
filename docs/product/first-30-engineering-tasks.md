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
- Review project bootstrap and source-file metadata persistence: partially addressed by local-profile API services using the existing `projects` and `source_files` tables. No public upload/storage endpoint, snapshot, or task persistence exists yet.
- Import batch persistence: partially addressed by local-profile API services using the existing `import_batches` table and `import_batch_status` enum values. No public import-batch endpoint, project snapshot, or imported task persistence exists yet.
- Import summary persistence: partially addressed by API services that record a worker parse summary response into existing `import_batches` columns and `parse_summary` JSONB, then mark the batch `parsed`. No parser execution, queue job, project snapshot, imported task/resource/assignment persistence, or public endpoint exists yet.
- Worker parse handoff: partially addressed by shared Java request/response records, an API request builder with a disconnected job client, and a worker service that maps local source file URIs to MPXJ summary output. No queue, background job, public endpoint, project snapshot, imported task persistence, or API-side parsing exists yet.
- Task 19, implement Project upload endpoint: partially addressed by a validation-only source-file upload placeholder with hardened multipart error responses. The validation endpoint validates multipart metadata and extension only; it does not store files, create import batches, persist metadata, call MPXJ, integrate with the worker, or write back to Microsoft Project.
- Task 20, implement MPXJ parse spike for MPP/MSPDI: partially addressed by a worker-only import summary spike. It reads an explicitly configured local path and returns counts only; no real fixtures, persistence, export, upload endpoint, or write-back exists.
- Import/export fixture strategy: addressed with safe fixture policy, text-only manifest schema, synthetic expected-output examples, and the first approved synthetic MSPDI import fixture.
- Approval/export batch schema: addressed as baseline SQL.
- Critical Watchlist reporting schema: addressed as baseline SQL.

This repository now has a minimal Maven/Spring Boot backend scaffold with PostgreSQL JDBC, Flyway runtime wiring, a worker-only MPXJ import summary spike, an API-only source-file validation placeholder, an internal source-file storage abstraction, local-profile services for review project bootstrap, source-file metadata persistence, import batch persistence, import summary persistence, plus a shared parse summary handoff contract. It still does not add React/Vite, frontend code, domain behavior, seed data, real Project files, Project write-back, queue integration, imported snapshot/task persistence, or a production database setup.

Migrations remain under `infra/migrations`; local migration validation remains under `scripts/db`; Spring Boot `local` profiles point Flyway to `filesystem:infra/migrations`.
