# ADR-002: Application Architecture

Status: Draft

## Context

The platform needs a clear first architecture without creating unnecessary distributed-system complexity.

## Decision

Use a monorepo and modular monolith first. Separate the primary API service from a project import/export worker.

## Consequences

- Shared packages can hold types, validation, API client code, UI foundations, and configuration.
- The worker boundary keeps heavy project-file processing away from request/response paths.
- Service extraction can be revisited later through ADRs.
