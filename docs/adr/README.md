# Architecture Decision Records

ADR status values: Draft, Accepted, Superseded, Rejected.

## Index

- [ADR-001: Microsoft Project Integration](ADR-001-microsoft-project-integration.md)
- [ADR-002: Application Architecture](ADR-002-application-architecture.md)
- [ADR-003: Frontend and Mobile](ADR-003-frontend-and-mobile.md)
- [ADR-004: Backend Stack](ADR-004-backend-stack.md)
- [ADR-005: Offline Sync](ADR-005-offline-sync.md)
- [ADR-006: Audit and Approval](ADR-006-audit-and-approval.md)
- [ADR-007: Data Ownership and Schedule Authority](ADR-007-data-ownership-and-schedule-authority.md)
- [ADR-008: MVP Scope Boundary](ADR-008-mvp-scope-boundary.md)
- [ADR-009: UX/UI Architecture](ADR-009-ux-ui-architecture.md)
- [ADR-010: Critical Work Package Reporting](ADR-010-critical-work-package-reporting.md)

## Proposed future ADRs

These decisions are currently captured in product/source documents and should become ADRs when implementation moves from visual review into production architecture.

- ADR-011: Task Progress Review and Export Approval.
- ADR-012: Entity-linked Communications Layer.
- ADR-013: Frontend Visual Review and UX Anti-Slop Guardrails.
- ADR-014: Progress Review Backend/API Model.
- ADR-015: Evidence Upload and Chain-of-Custody Model.
- ADR-016: Mobile Offline Queue Implementation.

## Implementation Guidance

ADR-006 controls audit and approval.

ADR-007 controls schedule authority.

ADR-009 controls UX/UI architecture.

ADR-010 controls Critical Work Package reporting.

The following product docs elaborate the current implementation direction without creating new ADRs yet:

- [Task Progress Review and Export Approval](../product/task-progress-review-export-approval.md)
- [Communications Layer](../product/communications-layer.md)
- [Frontend Visual Review Scope](../product/frontend-visual-review-scope.md)
- [UX Anti-Slop Rules](../product/ux-anti-slop-rules.md)
- [Design Language and Status Semantics](../product/design-language-and-status-semantics.md)

The permission, audit, authorization, approval/export, correction/supersession, offline sync, Critical Watchlist, task-progress review, and communications documents provide implementation guidance under those ADRs. This elaborates existing decisions rather than adding production behavior.

## Boundary reminders

- Microsoft Project remains the schedule authority.
- Shutdown Tracker remains the execution, review, evidence, handover, export-preparation, verification-metadata, and audit system.
- Planner approval does not update the master `.mpp`.
- MSPDI/XML artifact generation does not update the master `.mpp`.
- Project verification metadata does not save the master `.mpp`.
- Generic chat, scheduler views, direct Project automation, and dashboard bloat require explicit future ADRs before implementation.
