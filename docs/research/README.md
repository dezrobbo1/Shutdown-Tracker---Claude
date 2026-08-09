# Research

This folder contains research evidence, source governance, and decision provenance for Shutdown Tracker. It is not the primary product specification and it should not be used as an implementation roadmap.

## How to use this folder

Use the research material to understand why a product or architecture decision was made and how strong the supporting evidence is.

Authoritative current behavior belongs elsewhere:

- product behavior and scope: [`docs/product`](../product/README.md)
- architecture: [`docs/architecture`](../architecture/README.md)
- architecture decisions: [`docs/adr`](../adr/README.md)
- testing requirements: [`docs/testing`](../testing/README.md)

Git history and pull requests are the source for implementation chronology. Do not maintain completed PR history or "next coding PR" instructions in research documents.

## Research governance

- [Source Quality Register](source-quality-register.md) defines source-strength expectations.
- [Research Decision Source Map](source-map.md) maps important decisions to supporting sources.
- [Research Decisions Summary](research-decisions-summary.md) consolidates accepted research conclusions.
- [Research Index](research-index.md) lists the research packets and their current status/use.

When research conflicts with authoritative primary documentation, product/ADR decisions, or verified implementation constraints, resolve the conflict explicitly rather than silently carrying both positions forward.

## Accepted research areas

The current research baseline covers:

1. Microsoft Project import/export architecture and the MSPDI/XML boundary.
2. End-to-end application architecture and lifecycle.
3. UX/UI and operational interaction design.
4. Configurable Critical Work Package reporting.
5. Built-in communications and messaging boundaries.
6. Communications visual-review guidance.
7. Product functionality and direction.
8. Task Progress Review and Export Approval.
9. Source quality and decision provenance.
10. UX anti-slop and frontend visual-review guardrails.

The detailed conclusions that remain active should be reflected in current product or architecture documents. Research packets may retain historical context, but they do not override those current sources.
