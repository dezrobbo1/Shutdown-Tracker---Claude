# Research Index

This index records the project research packets, their status, and where their conclusions are used. It is an evidence/provenance index, not an implementation roadmap.

## Research packets

| Packet | Status | Primary use | Notes |
| --- | --- | --- | --- |
| Packet 01: Microsoft Project Import/Export Architecture | Accepted baseline | Microsoft Project boundary, MPXJ/MSPDI path, import/export policy | Supports MSPDI/XML export and no native `.mpp` write |
| Packet 02: End-to-End Application Architecture and Lifecycle | Accepted baseline | Monorepo, modular monolith, API/worker separation, PostgreSQL, storage | Reflected in architecture docs |
| Packet 03: UX/UI and Operational Interaction Design | Accepted baseline | Console/mobile IA, operational UX, sync visibility, evidence capture | Reflected in product/UX docs |
| Packet 04: Configurable Critical Work Package Reporting | Accepted baseline | Critical Watch, Critical WPs, reporting policy | Reporting layer, not scheduling |
| Packet 05: Built-in Communications and Messaging Layer | Direction accepted; implementation deferred | Entity-linked Discussion and communications boundaries | Reflected in communications product doc |
| Packet 06: Communications Layer Visual Review Brief | Visual-design input | Communications components/states/copy | Not a backend contract |
| Packet 07: Shutdown Tracker Functionality, Possibilities, and Next Product Direction | Accepted direction source | Product capability prioritisation and boundary checks | Use current product docs for active scope |
| Packet 08: Task Progress Review & Export Approval | Product brief accepted | Field progress, supervisor review, planner review, export candidates | Reflected in product doc |
| Packet 09: Source Quality Register and Source Map | Active governance | Source tiering and decision-to-source mapping | Controls evidence quality |
| Packet 10: UX Anti-Slop and Frontend Visual Review Scope | Active product guardrail | IA, visual hierarchy, status semantics, anti-dashboard rules | Reflected in product/UX docs |
| NotebookLM: Technical Implementation Blueprint | Working synthesis | Architecture framing and source discovery | Not authoritative without verification |
| NotebookLM: Technical Development Report | Working synthesis | Architecture synthesis and roadmap comparison | Not authoritative without verification |
| Historical chat/PDF summaries | Historical context only | Project chronology | Do not use as current product authority |

## Current product-source links

Research conclusions that remain active should be represented in current product or architecture documents, including:

- [Task Progress Review and Export Approval](../product/task-progress-review-export-approval.md)
- [Communications Layer](../product/communications-layer.md)
- [Frontend Visual Review Scope](../product/frontend-visual-review-scope.md)
- [UX Anti-Slop Rules](../product/ux-anti-slop-rules.md)
- [Design Language and Status Semantics](../product/design-language-and-status-semantics.md)
- [Architecture](../architecture/README.md)

For source governance, use:

- [Source Quality Register](source-quality-register.md)
- [Research Decision Source Map](source-map.md)
- [Research Decisions Summary](research-decisions-summary.md)

## Packet use rules

- Prefer authoritative primary documentation for hard technical, safety, accessibility, security, and platform claims.
- Treat vendor marketing, blogs, community posts, forums, videos, and AI-generated synthesis as supporting/exploratory unless independently verified.
- Research does not override an accepted product/ADR decision without an explicit revision.
- Suggestions involving scheduler ownership, automatic Project write-back, native `.mpp` writing, critical-path/float calculation, resource levelling, automatic date movement, or broad unstructured chat require explicit product/ADR review before implementation.
- Historical implementation notes belong in GitHub PR/commit history rather than this index.

## Review cadence

Review this index when a research packet is added, materially revised, superseded, or absorbed into product/architecture documentation. Update packet status and links; do not add chronological "next PR" instructions here.
