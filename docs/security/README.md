# Security

## Authentication and Authorization

Use OIDC for authentication. Authorization should be project-scoped and role-based, with room for explicit permissions on sensitive workflows such as export approval and evidence access.

## Project-Scoped RBAC

Permissions should be evaluated against project membership and role assignments. Global administrator access should be limited and audited.

## Audit Events

Audit events should capture important changes to task execution state, problems, actions, evidence metadata, handover, import batches, export approvals, permissions, and security-sensitive settings.

Audit events should be append-only and tied to actor, project, timestamp, action, target, and relevant request metadata.

## Evidence Access

Evidence files should be stored in object storage. Access should be mediated by application authorization and short-lived access mechanisms rather than public object URLs.

## Baseline Standards

Use OWASP ASVS and OWASP SAMM as security baselines for planning, implementation, review, and release readiness.
