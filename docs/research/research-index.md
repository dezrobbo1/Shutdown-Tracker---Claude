# Research Index

This index groups the project research material into decision packets and states how each packet should be used.

This file is a consolidation aid. `docs/research/README.md` and product-source documents now carry the current product direction.

## Research packets

| Packet | Status | Primary use | Strength | Notes |
| --- | --- | --- | --- | --- |
| Packet 01: Microsoft Project Import/Export Architecture | Accepted baseline | Microsoft Project boundary, MPXJ/MSPDI path, import/export policy | Strong | Supports MSPDI/XML export and no native `.mpp` write with MPXJ |
| Packet 02: End-to-End Application Architecture and Lifecycle | Accepted baseline | Monorepo, modular monolith, API/worker separation, PostgreSQL, object storage, frontend split | Strong | Aligns with current repo scaffold |
| Packet 03: UX/UI and Operational Interaction Design | Accepted baseline | Console/mobile IA, operational UX, sync visibility, evidence capture, import/export UX | Strong | Keep Today/Tasks/Problems/Evidence/Exports and My Work/Today/Problems/Evidence/Sync |
| Packet 04: Configurable Critical Work Package Reporting | Accepted baseline | Critical Watchlists, Critical WPs, reporting policy, Critical Updates, no-critical-path boundary | Strong | Reporting layer, not scheduling feature |
| Packet 05: Built-in Communications and Messaging Layer | Product direction accepted, implementation deferred | Communications model, entity-linked Discussion, no generic chat first | Medium/strong | Captured in product doc; backend deferred |
| Packet 06: Communications Layer Visual Review Brief | Visual design input | Components, states, copy, visual review scope for entity-linked Discussion | Medium | Do not treat as production messaging backend spec |
| Packet 07: Shutdown Tracker Functionality, Possibilities, and Next Product Direction | Current direction source | Task Progress Review & Export Approval, advanced ideas gating, next roadmap | Strong | Current best source for next major capability |
| Packet 08: Task Progress Review & Export Approval | Product brief created; integrity slice implemented | Field progress, supervisor review, planner review, export candidates | Strong | Authoritative candidate/approval/export integrity exists; live field-progress and supervisor-review workflow remains unimplemented |
| Packet 09: Source Quality Register and Source Map | Current consolidation | Source tiering and decision-to-source map | Strong as governance | Added to prevent weak-source drift |
| Packet 10: UX Anti-Slop and Frontend Visual Review Scope | Product guardrail created | Visual shell boundaries, approved IA, status semantics, anti-dashboard rules | Strong | Added after current visual shell showed card/chip/dashboard drift |
| NotebookLM: Technical Implementation Blueprint | Working synthesis note | Architecture framing and source discovery | Useful but not authoritative | Correct over-strong claims before use |
| NotebookLM: Technical Development Report | Working synthesis note | Architecture synthesis and roadmap comparison | Useful but not authoritative | Contains weak source classes; use register/map to filter |
| Historical chat/PDF summaries | Historical context | Project chronology only | Weak for current decisions | Do not cite for current architecture/product policy |

## Current final decisions

### Core product decision

Shutdown Tracker is a live shutdown execution tracker. It supports field execution, structured progress review, blockers, actions, evidence, handover, reporting, export preview, and audit. It is not a scheduling engine.

### Microsoft Project decision

Microsoft Project remains the schedule authority and final master-file control point.

Shutdown Tracker must not:

- calculate CPM;
- calculate float;
- calculate critical path;
- resource-level;
- optimise the schedule;
- automatically move dates;
- edit dependencies, constraints, calendars, baselines, resources, or planned dates;
- perform hidden write-back;
- imply that internal approval updates the master `.mpp`.

### Import/export decision

Use the existing controlled handoff path:

1. Import Microsoft Project snapshot.
2. Review parsed snapshot and lineage.
3. Capture execution truth in Shutdown Tracker.
4. Review progress through supervisor and planner gates.
5. Export only approved leaf-task progress/actual fields.
6. Generate MSPDI/XML artifact.
7. Planner manually opens/checks in Microsoft Project.
8. Planner controls whether the master `.mpp` is updated/saved.
9. Shutdown Tracker records verification metadata and audit.

### Task Progress Review decision

The next major product capability is Task Progress Review and Export Approval.

- Field progress does not go straight to Project.
- Supervisor review confirms operational validity, not export approval.
- Planner review controls export eligibility.
- MVP export whitelist is percent complete, actual start, and actual finish on leaf tasks only.
- Summary-task actuals, planned dates, dependencies, constraints, calendars, baselines, resources, and schedule logic remain outside Shutdown Tracker write authority.

Product source:

- [Task Progress Review and Export Approval](../product/task-progress-review-export-approval.md)

### Communications decision

Do not build generic chat first. Build structured records first, then entity-linked Discussion around tasks, problems, actions, evidence, handover, export review, and Project verification.

Product source:

- [Communications Layer](../product/communications-layer.md)

### UX anti-slop decision

The current frontend visual shell is useful for review but should not become final IA. Future UI work must avoid dashboard/card-wall bloat, visible synthetic task labels, generic chat, and top-level nav creep.

Product sources:

- [Frontend Visual Review Scope](../product/frontend-visual-review-scope.md)
- [UX Anti-Slop Rules](../product/ux-anti-slop-rules.md)
- [Design Language and Status Semantics](../product/design-language-and-status-semantics.md)

## Research-to-repo actions

### Completed by source consolidation

- Added source quality register.
- Added decision source map.
- Added consolidated research decisions summary.
- Added this research index.

### Completed by product-source alignment

Added:

- `docs/product/task-progress-review-export-approval.md`
- `docs/product/communications-layer.md`
- `docs/product/frontend-visual-review-scope.md`
- `docs/product/ux-anti-slop-rules.md`
- `docs/product/design-language-and-status-semantics.md`

Updated:

- `README.md`
- `docs/product/README.md`
- `docs/research/README.md`
- `docs/research/research-index.md`
- `docs/product/approval-export-state-model.md`
- `docs/product/permission-matrix.md`
- `docs/product/roles-and-capabilities.md`
- `docs/product/offline-audit-sync-rules.md`
- `docs/architecture/audit-event-schema.md`
- `docs/architecture/README.md`
- `docs/adr/README.md`
- `apps/console/README.md`
- `apps/mobile-pwa/README.md`

### Next coding PR should build

A frontend cleanup PR, not a new feature:

- restore console top-level nav to Today, Tasks, Problems, Evidence, Exports;
- move Supervisor Review and Planner Review into Today/Tasks/Exports sections or saved views;
- move Project Verification under Exports;
- replace visible synthetic labels with sanitized realistic examples;
- compact the mobile sync status area;
- reduce mobile task-card chip density;
- keep visual-only controls disabled;
- preserve Project-boundary warnings.

## Packet use rules

- If a packet conflicts with Microsoft/MPXJ/HSE/MDN/WCAG/OWASP/NIST/PostgreSQL/Spring official documentation, the official source wins.
- If a packet relies on vendor marketing, blogs, GitHub Discussions, StackOverflow, Reddit, or YouTube, treat the claim as exploratory only.
- If a packet suggests schedule automation, direct write-back, native `.mpp` output, Gantt authoring, or broad chat as source of truth, require a separate ADR before any implementation.
- If a packet uses strong language around Physical % Complete, Project Online, AI, event sourcing, Teams, or Project automation, re-check primary sources and product fit before accepting it.

## Review cadence

Review this index after each major research or docs PR.

Expected next source additions:

1. Frontend visual cleanup PR review notes.
2. Backend/API brief for progress submission and review.
3. Evidence upload and chain-of-custody product brief.
4. Mobile offline queue implementation brief.
