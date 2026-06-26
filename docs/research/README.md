# Research

This folder contains the project research baseline and source governance material for Shutdown Tracker.

Hard product and architecture claims should be checked against [Source Quality Register](source-quality-register.md) and mapped through [Research Decision Source Map](source-map.md). The [Research Decisions Summary](research-decisions-summary.md) is the current consolidated product direction.

## Packet 01: Microsoft Project Import/Export Architecture

Key decisions:

- Use MPXJ for Microsoft Project parsing and export support.
- Import immutable source files and preserve parse warnings.
- Store snapshots rather than treating imported schedules as live-linked documents.
- Export approved progress/actual fields through MSPDI/XML.
- Do not write native MPP files.
- Keep planner-controlled manual Microsoft Project open/check/save outside Shutdown Tracker automation.

## Packet 02: End-to-End Application Architecture and Lifecycle

Key decisions:

- Start with a monorepo.
- Use a modular monolith first.
- Separate the API service from the project import/export worker.
- Use PostgreSQL for relational operational data.
- Use object storage for evidence, source files, and export artifacts.
- Use append-only audit events plus current-state projections for v1 rather than full event sourcing.

## Packet 03: UX/UI and Operational Interaction Design

Key decisions:

- Provide separate Master Console and Mobile Field App experiences.
- Keep the Master Console optimized for coordination, review, approval, and reporting.
- Keep the mobile PWA optimized for assigned work, fast field updates, evidence capture, and visible sync state.
- Keep Master Console top-level zones as Today, Tasks, Problems, Evidence, and Exports.
- Keep Mobile Field App top-level zones as My Work, Today, Problems, Evidence, and Sync.
- Avoid schedule-authoring UI in the MVP.
- Avoid dashboard bloat, badge soup, generic SaaS UI, and chat-style messaging in MVP.

## Packet 04: Configurable Critical Work Package Reporting

Key decisions:

- Treat Critical Work Packages as reporting objects.
- Treat Critical Watchlists as named operational reporting lists.
- Make reporting policies configurable and generic.
- Link Problems, Actions, Evidence, Handover entries, and Critical Updates to Critical Work Packages where needed.
- Do not hardcode company-specific or asset-specific reporting behavior.
- Do not calculate critical path or move dates from Critical Watchlist state.

## Packet 05: Built-in Communications and Messaging Layer

Key decisions:

- Do not build generic chat first.
- Use entity-linked Discussion later where it supports structured operational records.
- Use Mentions and Needs Response as attention mechanisms, not a general inbox.
- Use Announcements as controlled broadcast records, not broad open channels.
- Treat comments as supporting context only unless promoted into task progress, blocker/problem, action, evidence, or handover.
- Do not let export review comments or Project verification notes imply Microsoft Project write-back.

## Packet 06: Communications Layer Visual Review Brief

Key decisions:

- Future communications visual work should be static/read-only until product data model, permissions, and audit rules are approved.
- The first communications visual shell should use Task Discussion, Problem Discussion, Action Update Log, Handover Notes, Export Review Comments, Project Verification Notes, Needs Response, and controlled Announcements.
- Do not add top-level Chat, private DMs, broad channels, arbitrary attachments, voice messages, or AI summaries.

## Packet 07: Shutdown Tracker Functionality, Possibilities, and Next Product Direction

Key decisions:

- The next major product capability is Task Progress Review and Export Approval.
- Build structured task progress capture, supervisor review, planner review/export approval, blockers/actions, evidence, handover, and reporting.
- Defer broad chat, direct Project automation, Project add-ins, Project Online/Dataverse connectors, AI-heavy features, and scheduler-like surfaces.
- Use MSPDI/XML artifact generation plus planner-controlled manual Project verification as the default handoff.

## Packet 08: Task Progress Review and Export Approval

Key decisions:

- Field progress does not go straight to Microsoft Project.
- Supervisor review confirms operational validity; it is not Project export approval.
- Planner review controls whether selected leaf-task values are eligible for export preview.
- MVP export candidates are percent complete, actual start, and actual finish on leaf tasks only.
- Summary-task actuals, planned dates, dependencies, constraints, calendars, baselines, resources, and schedule logic remain read-only.
- Re-import conflicts require planner lineage review before export.

Product source file:

- [Task Progress Review and Export Approval](../product/task-progress-review-export-approval.md)

## Packet 09: Source Quality Register and Source Map

Key decisions:

- Use authoritative sources for hard decisions: Microsoft Project docs, MPXJ docs, HSE, MDN, WCAG, OWASP, NIST, PostgreSQL, Spring, and OIDC/Microsoft identity sources.
- Treat NotebookLM reports, vendor pages, blogs, GitHub issues, forums, and generic articles as synthesis or background unless verified against authoritative sources.
- Correct over-strong NotebookLM claims around MPXJ/JAXB, Physical % Complete, queue assumptions, UID durability, and event sourcing before using them in product docs.

Source files:

- [Source Quality Register](source-quality-register.md)
- [Research Decision Source Map](source-map.md)
- [Research Decisions Summary](research-decisions-summary.md)
- [Research Index](research-index.md)

## Packet 10: UX Anti-Slop and Frontend Visual Review Scope

Key decisions:

- The current Task Progress Review frontend shell is useful but too broad to become final IA.
- Future UI work must avoid dashboard/card-wall bloat and generic AI-generated UI patterns.
- Keep console top-level zones frozen unless a product/ADR decision changes them.
- Treat Supervisor Review, Planner Review, and Verification as scoped review surfaces, not top-level navigation.
- Use sanitized realistic sample data instead of reviewer-facing `Synthetic Task A1` labels.
- Reduce chip/card density and keep mobile My Work focused on assigned work.

Product source files:

- [Frontend Visual Review Scope](../product/frontend-visual-review-scope.md)
- [UX Anti-Slop Rules](../product/ux-anti-slop-rules.md)
- [Design Language and Status Semantics](../product/design-language-and-status-semantics.md)
