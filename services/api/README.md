# API Service

Purpose: Spring Boot API service shell for future operational workflows, permissions, audit events, task updates, problems, actions, evidence metadata, handover, and export approvals.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.api`.
- Actuator is present for health/info exposure.
- `GET /api/version` returns a minimal service/status JSON payload.
- No task, import, export, approval, evidence, domain, or scheduler endpoints exist yet.
- No database, Flyway runtime wiring, Spring Security/OIDC, MPXJ, frontend, secrets, binaries, seed data, or real Project files are included.

## Local Commands

Run from the repository root when Maven and Java 21 are available:

```text
mvn -pl services/api test
mvn -pl services/api spring-boot:run
```
