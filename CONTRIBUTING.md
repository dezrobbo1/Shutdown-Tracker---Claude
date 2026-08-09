# Contributing

## Branches and Pull Requests

- Create focused branches for each change.
- Open pull requests into `main`.
- Keep pull requests small enough to review.
- Explain user impact, product boundaries, and verification steps in each PR.
- Do not mix repository hygiene, product-scope changes, architecture changes, and implementation changes unless the coupling is necessary and explicit.

## Architecture Decisions

Architectural changes require an ADR update or a new ADR before implementation. This includes changes to system boundaries, data ownership, Microsoft Project import/export flow, offline sync, security model, or deployment architecture.

## Scope Approval

Features that expand Shutdown Tracker beyond its approved execution-control boundary require explicit product/ADR approval before design or implementation.

Examples include:

- CPM, critical-path, float, or recovery-schedule calculation;
- resource levelling or schedule optimisation;
- automatic date movement or hidden schedule recalculation;
- uncontrolled/live Microsoft Project write-back or native `.mpp` writing;
- automatic changes to dependencies, constraints, calendars, baselines, or planned dates;
- broad AI-driven schedule decisions or predictions that change authoritative schedule state;
- generic dashboard-builder/platform functionality unrelated to the approved product workflows;
- communications features that make unstructured chat the operational source of truth.

Frontend/mobile delivery technology may evolve through explicit product and architecture decisions; it is not, by itself, a scheduler-boundary expansion.

## Documentation Authority

Use documentation according to its purpose:

- `docs/concept` for high-level product/MVP definition;
- `docs/product` for current product behavior and UX rules;
- `docs/architecture` for durable technical boundaries;
- `docs/adr` for decision history;
- `docs/research` for evidence/provenance, not implementation roadmaps;
- GitHub history for completed implementation chronology.

Do not maintain stale "next PR" instructions in durable product, architecture, research, or testing documents.

## Secrets and Artifacts

Do not commit secrets, `.env` files, uploaded source archives, real Microsoft Project files, PDFs, DOCX files, generated exports, local database files, evidence uploads, screenshots containing operational data, or other confidential/binary artifacts unless an explicit fixture policy approves a fully synthetic test asset.
