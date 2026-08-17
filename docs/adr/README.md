# Architecture Decision Records

ADR status values: Draft, Accepted, Superseded, Rejected.

## Index

- [ADR-001: Microsoft Project Integration](ADR-001-microsoft-project-integration.md) — **Accepted**
- [ADR-002: Application Architecture](ADR-002-application-architecture.md)
- [ADR-003: Frontend and Mobile](ADR-003-frontend-and-mobile.md)
- [ADR-004: Backend Stack](ADR-004-backend-stack.md)
- [ADR-005: Offline Sync](ADR-005-offline-sync.md)
- [ADR-006: Audit and Approval](ADR-006-audit-and-approval.md)
- [ADR-007: Data Ownership and Schedule Authority](ADR-007-data-ownership-and-schedule-authority.md) — **Accepted**
- [ADR-008: MVP Scope Boundary](ADR-008-mvp-scope-boundary.md) — **Accepted**
- [ADR-009: UX/UI Architecture](ADR-009-ux-ui-architecture.md)
- [ADR-010: Critical Work Package Reporting](ADR-010-critical-work-package-reporting.md)
- [ADR-011: Project Operational Mapping](ADR-011-project-operational-mapping.md)

## Controlling Project-handoff decisions

ADR-001 defines the Project interchange and candidate-artifact direction.

ADR-007 defines the three-part authority model:

1. Shutdown Tracker approves execution inputs.
2. Microsoft Project calculates the disposable candidate.
3. The planner decides whether to adopt the candidate.

ADR-008 defines the MVP boundary and explicitly allows read-only candidate-impact review while continuing to prohibit a Shutdown Tracker scheduling engine or silent master-file update.

The detailed product contract is [Project Candidate Schedule Handoff](../product/project-candidate-schedule-handoff.md).

## Other implementation guidance

- ADR-003 controls application experience/delivery-channel direction.
- ADR-006 controls audit and approval.
- ADR-009 controls UX/UI architecture.
- ADR-010 controls Critical Work Package reporting.
- ADR-011 controls Project-derived operational classification and mapping boundaries.

Product sources:

- [Project Candidate Schedule Handoff](../product/project-candidate-schedule-handoff.md)
- [Project Operational Mapping](../product/project-operational-mapping.md)
- [Task Progress Review and Export Approval](../product/task-progress-review-export-approval.md)
- [Approval and Export State Model](../product/approval-export-state-model.md)
- [Communications Layer](../product/communications-layer.md)
- [Offline Audit and Sync Rules](../product/offline-audit-sync-rules.md)

## Boundary reminders

- Microsoft Project remains schedule calculation authority.
- Shutdown Tracker may prepare approved execution inputs but does not independently calculate their schedule consequences.
- Project-calculated changes inside a disposable candidate are expected and must be reviewed, not automatically treated as prohibited writes.
- The accepted source/master remains unchanged until explicit planner adoption.
- Imported Project source values remain immutable.
- Project-derived classification is not application authorisation.
- Generic chat, editable scheduler views, and silent Project write-back require explicit future decisions.
