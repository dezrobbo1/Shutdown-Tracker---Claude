# Shutdown Tracker Agent Guidance

Applies to the whole repository. Read the [README](README.md), the relevant
[ADRs](docs/adr) and [product docs](docs/product), and [docs/goals/ACTIVE.md](docs/goals/ACTIVE.md)
before changing behaviour. Do not infer implemented behaviour from roadmap documents.

## The one boundary that matters

Three authorities stay separate:

- **Shutdown Tracker** captures and reviews execution facts (progress, actuals, blockers, evidence).
- **Microsoft Project** calculates the schedule. Tracker never computes CPM, float, dates, durations,
  or dependency consequences, and never writes native `.mpp` or silently updates the master schedule.
- **The planner** decides whether a recalculated candidate becomes the next master.

Field progress passes supervisor review then planner review before export. Audit history is
append-only: correct and supersede, never rewrite. Project-derived category membership is not
application authorization: visibility/relevance, responsibility, update permission, review
permission, and export authority remain separate. Details: ADR-001, ADR-007, ADR-008, and
[project-candidate-schedule-handoff.md](docs/product/project-candidate-schedule-handoff.md).

## Authorized work

The next deliverables are pre-approved — do not stop to ask before building them:

1. Database-backed role and capability grants (super-user administration).
2. The console round-trip trial described in ACTIVE.md.
3. Source-versus-candidate delta classification, the planner candidate decision, and the
   master-adoption record.

Choose the safest reversible option for routine implementation decisions and note assumptions in
the PR. Stop only for conflicts between authoritative documents, product-boundary changes without a
repository rule, or irreversible external actions.

## Working rules

- Keep each PR one focused outcome; smallest coherent change; follow nearby code patterns.
- Schema changes are new versioned files under `infra/migrations` — never rewrite an applied one.
- Real Microsoft Project schedule files are permitted as committed test fixtures. No secrets,
  credentials, or generated build artifacts in Git.
- No history rewriting, force-pushes, or merging PRs without explicit instruction.

## Validation

Run the suites CI runs (`.github/workflows/ci.yml`): `npm ci && npm test && npm run build` for
frontend/TypeScript, `mvn test` for Java, `./scripts/db/validate-migrations.sh` for migrations,
and `git diff --check` for everything. Manual Microsoft Project verification remains required for
handoff milestones and cannot be replaced by smoke scripts.
