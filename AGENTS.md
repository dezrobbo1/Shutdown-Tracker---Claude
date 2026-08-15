# Shutdown Tracker Agent Guidance

This file applies to the entire repository. More specific `AGENTS.md` files may add local guidance for a subtree; the closest applicable file wins.

## Start here

Before changing code or product behaviour:

1. Read the root [README.md](README.md).
2. Read the relevant ADRs in [docs/adr](docs/adr) and the relevant product documents in [docs/product](docs/product).
3. Check [docs/research/source-quality-register.md](docs/research/source-quality-register.md) before relying on research material for a hard product or architecture decision.
4. Inspect the current implementation and tests. Do not infer implemented behaviour from roadmap documents.
5. Read [docs/goals/ACTIVE.md](docs/goals/ACTIVE.md) when it exists. It defines the current branch target and completion conditions.

Do not assume access to earlier ChatGPT conversations, uploaded PDFs, ZIP files, or external project folders. Durable decisions must be present in this repository. If required context is missing or sources conflict, stop and ask rather than inventing a decision.

## Active goal protocol

When `docs/goals/ACTIVE.md` exists, treat these sections as the current task contract:

- Outcome
- Success criteria
- Non-goals
- Required validation
- Safety constraints
- Completion conditions

Continue autonomously through repository inspection, implementation planning, implementation, focused testing, full validation, diff review, documentation, commit preparation, and draft pull-request updates when those actions are authorized by the active goal.

Do not stop merely to ask about routine, reversible implementation choices. Choose the safest option consistent with this file, existing architecture, tests, and the active goal, then document material assumptions.

Stop and report rather than guessing when:

- two authoritative requirements conflict;
- a decision would change the product boundary or a public contract without a defensible repository rule;
- required credentials, external systems, or unavailable software prevent completion;
- proceeding would destroy or overwrite uncommitted work;
- a required validation still fails after reasonable investigation and at least two coherent correction attempts;
- an irreversible or externally visible action requires approval that has not been granted.

A pending manual or external gate does not justify claiming completion. Finish the automated scope, report the remaining gate precisely, and leave the repository in the state required by the active goal.

## Product authority and non-negotiable boundaries

- Microsoft Project remains the schedule authority. Shutdown Tracker is the execution, review, evidence, handover, operational-mapping, export-preparation, verification-metadata, and audit system.
- Do not implement CPM, critical-path or float calculation, resource levelling, recovery scheduling, schedule optimization, dependency-map scheduling, hidden recalculation, or automatic date movement.
- Do not live-feed, silently update, or save the master `.mpp`. Do not write native `.mpp` files.
- Controlled Project handoff uses reviewed MSPDI/XML artifacts. Artifact generation and verification metadata do not update Microsoft Project.
- Field progress must pass through supervisor review, planner review, export eligibility, and export preview before artifact generation.
- Initial export authority is limited to explicitly approved leaf-task execution facts. Summary-task actuals, planned dates, dependencies, constraints, calendars, baselines, resources, and scheduler logic are outside write authority.
- Critical Work Packages and Critical Watchlists are configurable reporting constructs, not calculated critical-path features.
- Project Operational Mapping may interpret imported fields, hierarchy, and resource-assignment metadata operationally, but imported source values remain immutable.
- Project-derived category membership is not application authorization. Visibility/relevance, responsibility, update permission, review permission, and export authority remain separate.
- Mapping revalidation must never silently remap an uncertain Project source after re-import.
- Communications must start with structured domain records. Entity-linked Discussion may support those records later; do not introduce generic chat, channels, or private messaging as an operational source of truth.
- Preserve append-only audit history and explicit approval, correction, rejection, and supersession semantics. Do not replace historical facts in place when a new event or version is required.

Relevant authority documents include:

- [docs/adr/ADR-007-data-ownership-and-schedule-authority.md](docs/adr/ADR-007-data-ownership-and-schedule-authority.md)
- [docs/adr/ADR-008-mvp-scope-boundary.md](docs/adr/ADR-008-mvp-scope-boundary.md)
- [docs/adr/ADR-009-ux-ui-architecture.md](docs/adr/ADR-009-ux-ui-architecture.md)
- [docs/adr/ADR-010-critical-work-package-reporting.md](docs/adr/ADR-010-critical-work-package-reporting.md)
- [docs/adr/ADR-011-project-operational-mapping.md](docs/adr/ADR-011-project-operational-mapping.md)
- [docs/product/project-operational-mapping.md](docs/product/project-operational-mapping.md)
- [docs/architecture/project-operational-mapping-implementation.md](docs/architecture/project-operational-mapping-implementation.md)
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
- For Project Operational Mapping, the worker returns Project source facts/metadata only. The API owns Tracker category/profile meaning, validation decisions, resolved membership orchestration, Scope/Saved Views, authorization, and audit.
- Implement Operational Mapping in vertical slices. The first coding slice is Source Catalogue only; do not jump straight to editable categories, broad frontend configuration, Saved Views, or automatic responsibility rules.
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
- `docs`: product, ADR, architecture, security, testing, concept, research, and active-goal authority.

## Working rules

- Begin with `git status -sb` and preserve unrelated or pre-existing changes.
- Keep each branch and PR focused on one reviewed outcome. Do not mix docs, frontend, backend, database, and infrastructure expansion unless the outcome genuinely requires them.
- Prefer the smallest coherent change. Do not perform broad rewrites, dependency upgrades, formatting churn, or speculative abstractions without explicit scope.
- Follow nearby code patterns and update tests alongside behaviour.
- Update the relevant product or architecture document when a change alters an approved boundary, workflow, state model, permission, or ownership rule.
- Keep environment-specific secrets and generated files out of Git.
- Report assumptions, unavailable checks, and any difference between visual scaffolding and production behaviour.

## Repository safety

- Never use `git reset --hard`, `git clean -fd`, blanket checkout, or another broad destructive cleanup command.
- Never amend, rebase, squash, rewrite existing commits, or force-push unless the user explicitly authorizes that exact operation.
- Never merge a pull request or mark a draft pull request ready unless explicitly instructed.
- Never modify, reset, clean, or switch another Git worktree.
- Never change machine or user execution policy.
- Never install global tooling without explicit approval.
- Never commit secrets, real Project files, generated MSPDI/XML artifacts, database files, customer/site data, screenshots with operational data, IDE state, or temporary validation output.
- Inspect staged content before committing and preserve unrelated uncommitted work.

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

For migration changes, prove both a clean installation and an upgrade from the previous populated baseline. Use PostgreSQL integration tests for constraints, triggers, foreign keys, row locks, concurrency, and rollback behaviour; fake repositories are not sufficient evidence for database invariants.

For export changes, prove that no unauthorized field, task, value, source, stale approval, stale baseline, summary-task actual, or unsupported policy version can reach the worker or generated MSPDI/XML.

Before declaring completion, inspect the complete diff, confirm no temporary files remain, and verify unrelated worktrees are unchanged.

## Definition of done

A change is complete only when its scope is clear, relevant focused and full checks pass, migration and integration evidence match the claimed invariants, documentation and tests agree with the implementation, product boundaries remain explicit, `git diff --check` passes, temporary artifacts are absent, unrelated worktrees remain unchanged, and the final handoff states what changed, what was verified, and what remains deliberately unimplemented or pending manual validation.
