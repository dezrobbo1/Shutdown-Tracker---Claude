# Security

## Authentication and Authorization

Use OIDC for authentication. Authorization should be project-scoped and role-based, with room for explicit permissions on sensitive workflows such as export approval and evidence access.

See [Authorization Model](authorization-model.md) for the baseline authorization rules.

## Project-Scoped RBAC

Permissions should be evaluated against project membership and role assignments. Global administrator access should be limited and audited.

Authentication identifies the user, but project-scoped roles decide what the user can do. Export authority, evidence access, and audit-log access should be explicit rather than implied by login alone.

## Audit Events

Audit events should capture important changes to task execution state, problems, actions, evidence metadata, handover, import batches, export approvals, permissions, and security-sensitive settings.

Audit events should be append-only and tied to actor, project, timestamp, action, target, and relevant request metadata.

Audit-log access is protected. Admins can view/export audit logs by default, while Planner, Shutdown Control, and other roles receive scoped access only where their work requires it.

## Evidence Access

Evidence files should be stored in object storage. Access should be mediated by application authorization and short-lived access mechanisms rather than public object URLs.

Evidence metadata, original file download, unlinking, supersession, and audit history should be separately permissioned. Contractors should be isolated to their own contract scope unless explicitly granted broader access.

## Baseline Standards

Use OWASP ASVS and OWASP SAMM as security baselines for planning, implementation, review, and release readiness.
