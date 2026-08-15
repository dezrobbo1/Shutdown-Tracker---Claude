# Authorization Model

## Direction

Shutdown Tracker should use OIDC for authentication and project-scoped RBAC for authorization. Authentication identifies the user. Application roles decide what the user can do.

## OIDC Authentication

- Use an OIDC provider for sign-in.
- Store only the application identity and provider subject required to map a user to project membership.
- Do not commit secrets, client secrets, `.env` files, keys, tokens, or provider configuration containing credentials.

## Project-Scoped RBAC

- Roles are assigned per project.
- Permissions are evaluated against project, role, and scope.
- Scope may include project, area, package, contract, watchlist, task assignment, or inspection responsibility.
- A user may have different roles in different projects.

## Role Inheritance

No implicit role inheritance is assumed in the baseline. If inheritance is added later, it must be explicit, documented, auditable, and testable.

## Least Privilege

- Start every role with the minimum permission needed for its work.
- Grant export approval only to Planner by default.
- Grant security administration only to Admin by default.
- Keep read-only Viewer / Management access separate from operational write access.

## Contractor Isolation

- Contractors see only assigned work, own evidence, own updates, and scoped handover or reporting obligations.
- Contractors cannot see other contractors' work unless explicitly granted.
- Contractors cannot approve export batches or manage project configuration.

## Viewer / Read-Only Model

Viewer / Management users are read-only by default. They may view reports, project status, export history, and scoped evidence where granted, but they do not create or approve operational records.

## Admin Responsibilities

Admins manage users, roles, permissions, security-sensitive settings, retention policy, and access investigations. Admin actions are highly sensitive and must be audited.

## Planner Export Authority

Planners own Microsoft Project export approval and generation by default. Export authority includes preview review, approval/rejection, MSPDI/XML generation, manual Microsoft Project verification, and export supersession.

## Supervisor Review Authority

Supervisors may review field updates, approve task completion, request corrections, and validate scoped evidence depending on project policy. Supervisor approval is not export batch approval.

## Field-User Limited Write Model

Field users can update assigned work, submit field updates, log problems, upload/link evidence, and submit handover or Critical Updates where assigned. They cannot approve exports, change reporting policies, or manage permissions.

## Evidence Access Rules

- Evidence metadata is governed by project and scope.
- Original evidence downloads should be separately permissioned.
- Evidence access should use application authorization and short-lived access mechanisms rather than public object URLs.
- Evidence unlinking and supersession must be audited.
- Deleting evidence metadata is admin-only by default and should be rare.

## Audit-Log Access Rules

- Admins can view/export audit logs by default.
- Planner and Shutdown Control may view scoped operational audit history.
- Viewer / Management may view read-only audit summaries where granted.
- Security events and permission-change history are protected.

## Future Tenant Boundary Notes

The baseline assumes project-scoped permissions. If multi-tenant operation is introduced, tenant boundaries must be explicit, audited, and covered by ADR updates before implementation.
