# Shutdown Tracker Agent Guidance

This file applies to the entire repository. More specific `AGENTS.md` files may add local guidance for a subtree; the closest applicable file wins.

## Start here

Before changing code or product behaviour:

1. Read the root [README.md](README.md).
2. Read the relevant ADRs in [docs/adr](docs/adr) and the relevant product documents in [docs/product](docs/product).
3. Check [docs/research/source-quality-register.md](docs/research/source-quality-register.md) before relying on research material for a hard product or architecture decision.
4. Inspect the current implementation and tests. Do not infer implemented behaviour from roadmap documents.

Do not assume access to earlier ChatGPT conversations, uploaded PDFs, ZIP files, or external project folders. Durable decisions must be present in this repository. If required context is missing or sources conflict, stop and ask rather than inventing a decision.

## Product authority and non-negotiable boundaries

- Microsoft Project remains the schedule authority. Shutdown Tracker is the execution, review, evidence, handover, export-preparation, verification-metadata, and audit system.
- Do not implement CPM, critical-path or float calculation, resource levelling, recovery scheduling, schedule optimization, dependency-map scheduling, hidden recalculation, or automatic date movement.
- Do not live-feed, silently update, or save the master `.mpp`. Do not write native `.mpp` files.
- Controlled Project handoff uses reviewed MSPDI/XML artifacts. Artifact generation and verification metadata do not update Microsoft Project.
- Field progress must pass through supervisor review, planner review, export eligibility, and export preview before artifact generation.
- Initial export authority is limited to explicitly approved leaf-task execution facts. Summary-task actuals, planned dates, dependencies, constraints, calendars, baselines, resources, and scheduler logic are outside write authority.
- Critical Work Packages and Critical Watchlists are configurable reporting constructs, not calculated critical-path features.
- Communications must start with structured domain records. Entity-linked Discussion may support those records later; do not introduce generic chat, channels, or private messaging as an operational source of truth.
- Preserve append-only audit history and explicit approval, correction, rejection, and supersession semantics. Do not replace historical facts in place when a new event or version is required.

Relevant authority documents include:

- [docs/adr/ADR-007-data-ownership-and-schedule-authority.md](docs/adr/ADR-007-data-ownership-and-schedule-authority.md)
- [docs/adr/ADR-008-mvp-scope-boundary.md](docs/adr/ADR-008-mvp-scope-boundary.md)
- [docs/adr/ADR-009-ux-ui-architecture.md](docs/adr/ADR-009-ux-ui-architecture.md)
- [docs/adr/ADR-010-critical-work-package-reporting.md](docs/adr/ADR-010-critical-work-package-reporting.md)
- [docs/product/task-progress-review-export-approval.md](docs/product/task-progress-review-export-approval.md)
- [docs/product/communications-layer.md](docs/product/communications-layer.md)
- [docs/product/offline-audit-sync-rules.md](docs/product/offline-audit-sync-rules.md)

## Current implementation guardrails

- Treat the current console and mobile PWA task-progress surfaces as static/synthetic visual-review shells unless the implementation and product documents explicitly say otherwise.
- Keep write-like frontend controls disabled until the corresponding API, authorization, audit, error, and offline behaviours exist.
- Keep the console top-level navigation fixed to Today, Tasks, Problems, Evidence, and Exports.
- Keep the mobile top-level navigation fixed to My Work, Today, Problems, Evidence, and Sync.
- Follow [docs/product/ux-anti-slop-rules.md](docs/product/ux-anti-slop-rules.md) and [docs/product/design-language-and-status-semantics.md](docs/product/design-language-and-status-semantics.md). Prefer narrow operational screens, realistic sanitized examples, limited semantic colours, and visible sync state. Avoid card walls, badge soup, marketing copy, scheduler visuals, and generic AI/copilot UI.
- The API owns request/response workflows and persistence orchestration. Project parsing and artifact generation belong in the project worker; do not move parser execution into the API.
- Keep schema changes in versioned SQL files under `infra/migrations`. Do not rewrite an already applied migration; add the next migration and validate the full sequence.
- Use only synthetic or explicitly approved sanitized fixtures. Do not commit real schedules, real Project files, customer data, secrets, generated export artifacts, screenshots containing operational data, or unrelated binaries.

## Repository map

- `apps/console`: React/Vite master console.
- `apps/mobile-pwa`: React/Vite mobile field shell.
- `packages/api-client`: shared TypeScript API client.
- `services/api`: Java 21 Spring Boot API.
- `services/project-worker`: Java 21 Spring Boot MPXJ worker.
- `packages/project-import-contract` and `packages/project-export-contract`: shared Java handoff contracts.
- `infra/migrations`: PostgreSQL/Flyway-compatible migrations.
- `fixtures`: synthetic test and review inputs only.
- `docs`: product, ADR, architecture, security, testing, concept, and research authority.

## Working rules

- Begin with `git status -sb` and preserve unrelated or pre-existing changes.
- Keep each branch and PR focused on one reviewed outcome. Do not mix docs, frontend, backend, database, and infrastructure expansion unless the outcome genuinely requires them.
- Prefer the smallest coherent change. Do not perform broad rewrites, dependency upgrades, formatting churn, or speculative abstractions without explicit scope.
- Follow nearby code patterns and update tests alongside behaviour.
- Update the relevant product or architecture document when a change alters an approved boundary, workflow, state model, permission, or ownership rule.
- Keep environment-specific secrets and generated files out of Git.
- Report assumptions, unavailable checks, and any difference between visual scaffolding and production behaviour.

## Validation

Run checks from the repository root in proportion to the files changed.

Frontend or shared TypeScript changes:

```text
npm ci
npm test
npm run build
```

Java/backend or shared Java contract changes:

```text
mvn test
```

Migration changes require Docker Desktop or compatible Docker Compose:

```text
./scripts/db/validate-migrations.sh
```

On Windows PowerShell:

```text
.\scripts\db\validate-migrations.ps1
```

For every change:

```text
git diff --check
```

Use the guarded scripts in `scripts/review` only when their prerequisites and explicit synthetic-data safety switches match the task. Never treat a smoke-script result as manual Microsoft Project verification.

## Definition of done

A change is complete only when its scope is clear, relevant checks pass, documentation and tests agree with the implementation, product boundaries remain explicit, and the final handoff states what changed, what was verified, and what remains deliberately unimplemented.
