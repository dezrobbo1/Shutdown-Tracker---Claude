# Product

## Product Modules

- Project import/export
- Project snapshots
- Imported WBS, tasks, resources, and assignments
- Critical Watchlists and Critical Work Packages
- Configurable Reporting Policies
- Critical Updates
- Problems
- Actions
- Evidence and files
- Handover
- Approval and export batches
- Audit events
- Users, roles, and permissions
- Offline sync queue

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
- Track task execution state and task events.
- Log problems and delays.
- Assign, receive, and close actions.
- Attach evidence and photos.
- Submit and review handover notes.
- Submit Critical Updates under configurable reporting policies.
- Review and approve export batches.
- Generate MSPDI/XML export artifacts.

## Critical Watchlist and Critical Work Package Reporting

A Critical Watchlist is a named operational reporting list for one shutdown, area, or purpose. A Critical Work Package is a reporting object, not a scheduling object.

Default Critical Work Package source is a selected Microsoft Project summary task plus all descendants. Future options may include multi-summary grouping and manual grouping.

Reporting policy types should include none, ad hoc, fixed interval, fixed times, shift-based, event-triggered, and custom.

## Problem, Action, and Evidence Model

Problems describe execution issues, delays, blockers, or risks. Actions assign follow-up ownership. Evidence stores file/photo metadata and object-storage references. These records may link to tasks, Critical Work Packages, handover entries, users, and audit events.

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
