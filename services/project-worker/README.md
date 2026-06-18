# Project Worker

Purpose: Spring Boot worker service shell for future Microsoft Project import/export processing through MPXJ and MSPDI/XML artifacts.

## Current Scope

- Placeholder Spring Boot application in package `com.shutdowntracker.projectworker`.
- The worker will later own Microsoft Project import/export processing.
- No MPXJ dependency, file parsing, background jobs, queue integration, database wiring, scheduler logic, secrets, binaries, seed data, or real Project files are included.
- No domain behavior exists yet.

## Local Commands

Run from the repository root when Maven and Java 21 are available:

```text
mvn -pl services/project-worker test
mvn -pl services/project-worker spring-boot:run
```
