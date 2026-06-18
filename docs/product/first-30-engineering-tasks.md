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
- Task 7, scaffold Project worker: addressed as a placeholder service shell only. The worker has no MPXJ dependency, file parsing, background jobs, queue integration, export generation, or scheduler logic.
- Task 8, configure PostgreSQL locally: partially addressed by the local Docker migration validation setup. This is not full runtime configuration and not a production database setup.
- Task 9, add migrations: addressed by PR #3 with baseline SQL migrations in `infra/migrations`.
- Task 10, create source-file, import-batch, snapshot, task, and audit tables: addressed by PR #3 as baseline SQL.
- Approval/export batch schema: addressed as baseline SQL.
- Critical Watchlist reporting schema: addressed as baseline SQL.

This repository now has a minimal Maven/Spring Boot backend scaffold. It still does not add React/Vite, frontend code, domain behavior, seed data, real Project files, PostgreSQL runtime wiring, Flyway runtime dependencies, or a production database setup.

Migrations remain under `infra/migrations`; local migration validation remains under `scripts/db`.
