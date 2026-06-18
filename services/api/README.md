# API Service

Purpose: Spring Boot API service shell for future operational workflows, permissions, audit events, task updates, problems, actions, evidence metadata, handover, and export approvals.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.api`.
- Actuator is present for health/info exposure.
- `GET /api/version` returns a minimal service/status JSON payload.
- `POST /api/source-files/validate` validates one multipart `file` upload request and returns metadata plus an accept/reject decision.
- The `local` profile configures PostgreSQL and Flyway runtime wiring.
- The `review` profile boots without PostgreSQL for backend smoke checks only.
- No file is stored, parsed, persisted, forwarded, or imported by the validation endpoint.
- No task execution, import batch, export, approval, evidence, domain, or scheduler endpoints exist yet.
- No Spring Security/OIDC, MPXJ, frontend, secrets, binaries, seed data, or real Project files are included.

## Source File Validation Placeholder

`POST /api/source-files/validate` is validation-only. It accepts a multipart field named `file` and returns:

- Original filename.
- Size in bytes.
- Detected extension.
- Accepted true/false.
- Rejection reason when rejected.
- Message confirming no file was stored or parsed.

Accepted extensions:

- `.mpp`
- `.xml`
- `.mspdi.xml`

Rejected examples include empty files, missing filenames, unsupported extensions, `.zip`, `.pdf`, `.doc`, `.docx`, `.xer`, screenshots/images, and files over the configured placeholder limit.

The default placeholder validation limit is 50 MB via `shutdown-tracker.source-file-validation.max-size-bytes`. Spring multipart limits are set slightly higher at 60 MB so normal oversized uploads can still receive the validation response shape. Hard multipart failures, missing `file` fields, and multipart parse errors are handled by exception type rather than selected controller, so they return the same validation-style JSON where possible.

Local testing files must stay outside Git. Do not commit real Project files, customer/site files, screenshots, generated exports, binaries, or secrets.

## Database Runtime Config

The `local` profile uses PostgreSQL and Flyway. Run commands from the repository root so Flyway can resolve `filesystem:infra/migrations`.

Default local values align with `infra/docker/docker-compose.postgres.yml`:

- `SHUTDOWN_TRACKER_DB_URL`, default `jdbc:postgresql://localhost:5432/shutdown_tracker`
- `SHUTDOWN_TRACKER_DB_USERNAME`, default `shutdown_tracker`
- `SHUTDOWN_TRACKER_DB_PASSWORD`, default `shutdown_tracker_dev`
- `SHUTDOWN_TRACKER_FLYWAY_LOCATIONS`, default `filesystem:infra/migrations`

The test profile disables datasource and Flyway auto-configuration so context-load tests do not require PostgreSQL.

The migration validation scripts apply SQL directly and do not create Flyway history. Use a clean PostgreSQL volume when checking Spring Boot runtime migrations through this service.

## Review Smoke Profile

The `review` profile is for backend smoke deployment only. It disables datasource and Flyway auto-configuration, uses `PORT` with a default of `8080`, and keeps Actuator health/info plus source-file validation available without PostgreSQL.

See [API review smoke profile](../../docs/deployment/api-review-smoke.md) for curl checks and Docker usage.

## Local Commands

Run from the repository root when Maven and Java 21 are available:

```text
mvn -pl services/api test
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=local
mvn -pl services/api spring-boot:run -Dspring-boot.run.profiles=review
```
