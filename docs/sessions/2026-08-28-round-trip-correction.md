# Session: repository reset and round-trip correction

Date: 2026-08-28
Scope: PRs #40–#45, plus off-repo evidence work
Outcome: process scaffolding removed, the disproven assigned-task export corrected against real
evidence, and a candidate file generated for manual Microsoft Project verification.

## Why this session happened

A full review of the repository found strong engineering (authorization, SQL hygiene, CI,
difference-based export integrity) weighed down by process ceremony, and one critical open
question: whether the Microsoft Project XML round trip was actually proven. Investigation of the
legacy source consolidation archive (`Shutdown_Tracker_Source_Consolidation_2026-08-28`) showed it
was not — the archive's `Plan Round Trip Trial.txt` records a real trial on the BOILER schedule in
which Microsoft Project rejected the console's three-field export (`PercentComplete`,
`ActualStart`, `ActualFinish`) on assigned tasks, with the verdict that the export method "is
therefore disproven for assigned tasks and should be disabled until corrected." The current
repository's exporter carried that same defect.

## What was merged

- **#40 — docs purge.** Removed 32 session journals, research provenance, source material, and a
  stale handover doc (~50 of 86 markdown files); condensed `docs/goals/ACTIVE.md` from 383 lines
  to 47. No code, tests, ADRs, CI, or migrations touched.
- **#41 — AGENTS.md slim-down.** 214 lines to 48. Kept the three-authority boundary,
  review-before-export, append-only audit, and validation; dropped the stop-and-ask ceremony and
  pre-authorized the next deliverables. Fixed stale "Placeholder" pom descriptions.
- **#42 — fixture policy.** Removed the synthetic/sanitized-only fixture rule from AGENTS.md,
  CONTRIBUTING.md, README.md, one architecture doc, and `.gitignore`, so real Project files can be
  committed as deliberate fixtures.
- **#43 — evidence base.** Committed the real BOILER WG110 before/after pair under
  `fixtures/project-files/boiler/` — the after file carries progress entered natively in
  Microsoft Project on task UIDs 43/318/319, the same tasks the disproven trial used — and
  `docs/product/project-progress-field-contract.md`, the field contract derived by diffing the
  pair: 10 task fields, 7 assignment fields per assignment, timephased Type 1 → Type 2
  conversion, the legitimate-recalculation set, and the open partial-progress evidence gap.
- **#44 — fail-loudly guard.** The exporter refuses task-level progress on assignment-bearing
  tasks instead of silently emitting the disproven transaction, tested against the real BOILER
  fixture (task UID 43).
- **#45 — full completion transaction.** An approved 100% completion with actual dates on an
  assigned task now writes the complete evidenced transaction (task, assignment, and timephased
  levels, all values derived from the accepted source). The guard narrowed to its evidence
  boundary: partial or undated progress on assigned tasks is still refused. Derived mutations are
  recorded in an exact path-to-value ledger and excused by the difference engine only at exactly
  the derived value. Worker suite 80/80.

## Off-repo evidence work

The legacy archive also contained real KILN and CALCINER schedules and the prototype lineage
(v1–v35). The BOILER before/after pair was extracted from user-provided files, diffed to derive
the field contract, and a verification candidate was generated with the merged exporter
(task UID 43 completed) for manual Microsoft Project verification.

## Open items after this session

1. **Manual Project verification** — open the generated candidate in Microsoft Project,
   recalculate (F9), save; if progress survives, commit the candidate/Project-saved pair as the
   round-trip proof fixture. This is the decisive step and requires a human with Project.
2. **Partial-progress evidence** — one native sample of an in-progress task (e.g. 40%) to close
   the field contract's open gap and lift the remaining guard.
3. **Console round-trip trial** — the active goal in `docs/goals/ACTIVE.md`, now with a working
   export step behind it.
4. **Candidate loop and auth** — delta classification, planner decision, adoption record; then
   replace trusted-header auth before any multi-user trial.
