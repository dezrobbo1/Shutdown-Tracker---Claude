# Product

## Product Modules

- Project import/export
- Project snapshots
- Imported WBS, tasks, resources, assignments, and custom-field metadata
- Project Operational Mapping
- Source Catalogue
- Versioned Import Profiles
- Operational Categories and category value configuration
- Global operational Scope
- Saved Operational Views
- Mapping health and execution-readiness checks
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
- Users, roles, permissions, responsibility scopes, and delegation
- Offline sync queue

## Application Experiences and Delivery

Shutdown Tracker has two application experiences, not four separate products:

- **Master Console** — desktop-optimised, available through a desktop browser and capable of installable desktop delivery.
- **Field App** — mobile-optimised, available through a mobile browser/PWA and capable of installable iOS/Android delivery.

Both experiences use the same platform data, project state, identity, permissions, audit, and workflow authority. Installed and browser delivery channels must not fork the product model. Device-specific capabilities may differ where required for offline storage, camera/evidence capture, notifications, or background sync.

The Master Console remains desktop-first even when browser-delivered. The Field App remains field/mobile-first and does not reproduce the complete control-room workspace.

## Control Model

The product control model is documented before implementation. Backend tables, API endpoints, and frontend workflows should align to these documents before scaffolding begins:

- [Project Operational Mapping](project-operational-mapping.md)
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

- Admin
- Planner
- Shutdown Control
- Coordinator
- Supervisor
- Field User
- Contractor
- Inspector
- Viewer / Management

Project-derived categories may assist visibility, relevance, and Tracker-owned responsibility configuration, but category membership never grants application authority by itself.

## Core Workflows

- Import a Microsoft Project schedule snapshot.
- Inspect available Project source fields, hierarchy, custom-field metadata, resources, assignments, and Resource Groups.
- Select/validate a versioned Import Profile.
- Create or maintain Operational Categories and preview their source coverage/values.
- Resolve task category membership from direct task fields, hierarchy/summary ancestry, and assigned-resource `Group`.
- Review mapping health, new/unmapped values, and execution-readiness warnings.
- Reconcile task lineage and operational mappings after re-import without uncertain silent remapping.
- Apply global operational Scope and Saved Operational Views.
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

## Project Operational Mapping

Project Operational Mapping is the planner-configurable layer between immutable Project snapshots and Shutdown Tracker execution views.

Microsoft Project owns source facts. Shutdown Tracker owns the operational interpretation configured over those facts.

The MVP source modes are:

- direct imported task fields/custom fields;
- WBS/hierarchy/selected summary ancestry;
- task assignments resolved through Resource `Group`.

Operational Categories may be single- or multi-valued and may feed filtering, grouping, global Scope, Saved Views, Critical Watch selection/scoping, Problems, Actions, Evidence, Handover, reporting, and appropriate mobile relevance.

Original Project values are never overwritten. Optional Tracker display aliases and higher-level roll-ups are separate configuration.

Mappings live in versioned Import Profiles and must be revalidated against every immutable Project snapshot. Missing/changed fields, new values, hierarchy changes, and probable field moves must be surfaced to the planner. Uncertain mappings must never be silently remapped.

Classification is not authorisation. Visibility/relevance, operational responsibility, task-update authority, review authority, and export authority remain separate.

See [Project Operational Mapping](project-operational-mapping.md) for the detailed product contract.

## Task Progress Review and Export Approval

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

Default Critical Work Package source is a selected Microsoft Project summary task plus all descendants. The MVP should also support one Critical WP sourced from multiple summary tasks where a reporting group crosses summary boundaries. Configured hierarchy/category mappings may assist selection and operational scope.

Microsoft Project `Critical`, Total Slack, Free Slack, or other schedule-calculated values do not automatically define Critical Watch membership.

Reporting policy types should include none, ad hoc, fixed interval, fixed times, shift-based, event-triggered, and custom.

## Problem, Action, Evidence, and Handover Model

Problems describe execution issues, delays, blockers, holds, permits, or risks. Actions assign follow-up ownership. Evidence stores file/photo metadata and object-storage references. These records may link to tasks, Critical Work Packages, handover entries, users, communications, and audit events.

Linked operational records should inherit enough mapped category context for filtering/reporting and retain historical context/provenance across later Project re-imports.

Problems/actions/evidence/handover records are structured operational records. They should not be replaced by free-form comments.

## MVP Screen List

Master Console:

- Today
- Tasks
- Problems
- Evidence
- Exports

Field App:

- My Work
- Today
- Problems
- Evidence
- Sync

Project Operational Mapping is project/planner setup functionality and does not require a new permanent top-level operational zone. It may be exposed through import/project setup and dedicated mapping configuration surfaces.

Do not add top-level console zones such as Chat, Supervisor Review, Planner Review, Verification, Dashboard, Reports, or Gantt without a product decision and ADR/source-doc update. Supervisor Review belongs under Today/Tasks, Planner Review and Project Verification belong under Exports, and Needs Response belongs in Today/top chrome rather than a generic inbox.

## MVP Boundary

MVP includes the operational-mapping foundation described above: source discovery, versioned Import Profiles, categories from direct task fields/hierarchy/Resource Group, single/multi-value classification, aliases/roll-ups, Scope, Saved Views, mapping health/re-import validation, readiness checks, provenance, and audit.

Deferred mapping capabilities include complex expression/rules engines, advanced assignment custom-field derivation, automatic responsibility assignment, milestone event watch, baseline analysis, and advanced Project-context analysis.

The following remain explicitly outside the product scheduling boundary: Gantt/CPM scheduling UI, critical-path/float calculation, dependency-map scheduling UI, recovery/scheduler engine, resource levelling, automatic schedule movement, Project formula evaluation, AI schedule optimisation, Project Critical/slack-driven Critical Watch logic, hidden Project write-back, native `.mpp` writing, automatic permissions from category membership, and generic chat-clone behavior.
