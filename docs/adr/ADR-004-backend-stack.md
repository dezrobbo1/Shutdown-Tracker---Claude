# ADR-004: Backend Stack

Status: Draft

## Context

The backend must support operational workflows, auditability, permissions, project imports, and export approval.

## Decision

Use Kotlin or Java Spring Boot for the backend services, PostgreSQL for relational storage, and object storage for evidence and file artifacts.

## Consequences

- Backend implementation language remains open between Kotlin and Java until the scaffold evolves.
- PostgreSQL migrations should be introduced before domain tables.
- Object-storage abstractions should be introduced before evidence and project-file uploads.
