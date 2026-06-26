# Product

## Product Modules

- Project import/export
- Project snapshots
- Imported WBS, tasks, resources, and assignments
- Task Progress Review and Export Approval
- Supervisor Review Queue
- Planner Progress Review Queue
- Critical Watchlists and Critical Work Packages
- Configurable Reporting Policies
- Critical Updates
- Problems
- Actions
- Evidence and files
- Handover
- Approval and export batches
- Entity-linked Discussion / Communications Layer
- Needs Response / Mentions
- Announcements
- Frontend Visual Review Scope
- UX Anti-Slop Rules
- Design Language and Status Semantics
- Audit events
- Users, roles, and permissions
- Offline sync queue

## Control Model

The product control model is documented before implementation. Backend tables, API endpoints, and frontend workflows should align to these documents before scaffolding begins:

- [Roles and Capabilities](roles-and-capabilities.md)
- [Permission Matrix](permission-matrix.md)
- [Approval and Export State Model](approval-export-state-model.md)
- [Task Progress Review and Export Approval](task-progress-review-export-approval.md)
- [Communications Layer](communications-layer.md)
- [Correction and Supersession Rules](correction-and-supersession-rules.md)
- [Offline Audit and Sync Rules](offline-audit-sync-rules.md)
- [Critical Watchlist Permissions](critical-watchlist-permissions.md)
- [Frontend Visual Review Scope](frontend-visual-review-scope.md)
- [UX Anti-Slop Rules](ux-anti-slop-rules.md)
- [Design Language and Status Semantics](design-language-and-status-semantics.md)

## User Roles

Initial role language should remain generic:

- Shutdown control
- Planner
- Coordinator
- Supervisor
- Package owner
- Manager
- Field supervisor
- Leading hand
- Contractor
- Inspector
- Execution crew member
- System administrator

## Core Workflows

- Import a Microsoft Project schedule snapshot.
- Review imported tasks, resources, assignments, and warnings.
- Reconcile task lineage after re-import.
- Track task execution state and task events.
- Submit structured task progress.
- Review progress as supervisor.
- Review exportable progress as planner.
- Approve only eligible leaf-task progress/actual fields for export preview.
- Log problems and delays.
- Assign, receive, and close actions.
- Attach evidence and photos.
- Submit and review handover notes.
- Submit Critical Updates under configurable reporting policies.
- Review and approve export batches.
- Generate MSPDI/XML export artifacts.
- Record manual Microsoft Project open/check/verification metadata.
- Use entity-linked Discussion only where structured records are not enough.

## Task Progress Review and Export Approval

Task Progress Review and Export Approval is the next core product capability.

The intended workflow is:

```text
field progress update
-> supervisor review
-> planner review
-> export eligibility
-> export preview
-> MSPDI/XML artifact generated
-> planner manually opens/checks in Microsoft Project
-> planner controls whether master .mpp is saved
-> Shutdown Tracker records verification metadata and audit
```

Only reviewed, planner-approved leaf-task progress/actual fields may become export candidates. The MVP whitelist is:

- percent complete;
- actual start;
- actual finish.

Summary-task actuals, planned dates, dependencies, constraints, calendars, baselines, WBS/outline structure, resources, resource levelling, assignment actuals, and schedule logic remain outside Shutdown Tracker write authority.

## Communications Layer

The communications layer must not start as generic chat.

The product direction is entity-linked operational Discussion later, attached to structured objects such as tasks, problems, actions, evidence, handover, export preview lines, export batches, and Project verification steps.

Rules:

- A comment is not task progress.
- A comment is not a blocker unless promoted.
- A comment is not an action unless promoted.
- A comment is not handover unless flagged/promoted.
- Evidence is not a chat attachment.
- Export review comments do not update Microsoft Project.
- Project verification notes do not save or update the master `.mpp`.

Do not create a top-level Chat area for MVP.

## Critical Watchlist and Critical Work Package Reporting

A Critical Watchlist is a named operational reporting list for one shutdown, area, or purpose. A Critical Work Package is a reporting object, not a scheduling object.

Default Critical Work Package source is a selected Microsoft Project summary task plus all descendants. The MVP should also support one Critical WP sourced from multiple summary tasks where a reporting group crosses summary boundaries. Fully manual/arbitrary leaf-task grouping can be deferred.

Reporting policy types should include none, ad hoc, fixed interval, fixed times, shift-based, event-triggered, and custom.

## Problem, Action, and Evidence Model

Problems describe execution issues, delays, blockers, holds, permits, or risks. Actions assign follow-up ownership. Evidence stores file/photo metadata and object-storage references. These records may link to tasks, Critical Work Packages, handover entries, users, communications, and audit events.

Problems/actions/evidence/handover records are structured operational records. They should not be replaced by free-form comments.

## MVP Screen List

Master Console:

- Today
- Tasks
- Problems
- Evidence
- Exports

Mobile Field App:

- My Work
- Today
- Problems
- Evidence
- Sync

Do not add top-level console zones such as Chat, Supervisor Review, Planner Review, Verification, Dashboard, Reports, or Gantt without a product decision and ADR/source-doc update. Supervisor Review belongs under Today/Tasks, Planner Review and Project Verification belong under Exports, and Needs Response belongs in Today/top chrome rather than a generic inbox.

## Visual Review Boundary

Current frontend Task Progress Review surfaces are a static/synthetic visual shell, not production IA and not API contracts.

Before further feature UI work:

- restore the console top-level IA to Today, Tasks, Problems, Evidence, Exports;
- reduce card/chip density;
- replace visible synthetic labels with sanitized realistic examples;
- keep write-like controls disabled until APIs exist;
- keep Project-boundary warnings visible;
- do not add more panels to the single console overview page.
