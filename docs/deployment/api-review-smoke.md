# API Review Smoke Profile

The API review profile is for narrow backend smoke review of the placeholder API service. It lets reviewers boot `services/api` without PostgreSQL so they can check health, version, and source-file validation behavior.

This is not a production deployment profile and not a product workflow review environment.

## What Can Be Reviewed

- `GET /actuator/health`
- `GET /api/version`
- `POST /api/source-files/validate`
- Validation-only accept/reject responses for synthetic local files

## What Cannot Be Reviewed

- File storage or disk writes
- MPXJ parsing in the API
- Import batch creation or persistence
- Project worker integration
- Export generation or Project write-back
- Scheduler logic, CPM, critical path, float, resource levelling, recovery scheduling, or automatic date movement
- Frontend, mobile PWA, security/OIDC, or production deployment behavior

## Required Environment

Set:

```text
SPRING_PROFILES_ACTIVE=review
```

Optional:

```text
PORT=8080
SHUTDOWN_TRACKER_SOURCE_FILE_VALIDATION_MAX_SIZE_BYTES=52428800
```

The review profile disables datasource and Flyway auto-configuration. No PostgreSQL database is required.

## Smoke Checks

Start the API with the review profile, then run:

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/version
```

For source-file validation, use a synthetic local file only:

```bash
printf "synthetic" > synthetic-basic-wbs.mspdi.xml
curl -s -F "file=@synthetic-basic-wbs.mspdi.xml;type=application/xml" \
  http://localhost:8080/api/source-files/validate
rm synthetic-basic-wbs.mspdi.xml
```

The validation endpoint does not store, parse, persist, forward, or import the file.

## Optional Docker Build

The API Dockerfile builds only the API service from the monorepo and defaults to the review profile:

```bash
docker build -f services/api/Dockerfile -t shutdown-tracker-api-review .
docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=review shutdown-tracker-api-review
```

Do not pass secrets for this review profile. It is intentionally database-free and smoke-only.
