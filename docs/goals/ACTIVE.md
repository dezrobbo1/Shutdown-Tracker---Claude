# Active Goals

Phases 0, 1, and 3 are complete. The end-to-end chain (field update → review → export → returned
candidate) has been walked over HTTP and is held by `ProductJourneyTests`. What remains is driving
it through the interface, then resuming the candidate-schedule work.

## Now: console round-trip trial

- **Super user grants roles and responsibilities.** Roles and capability grants are compile-time
  today; move them into the database so one admin can define who else exists and what they may do.
- **The trial itself.** Import a real schedule, track work through the task box, walk the three
  review stages, generate the export, and return the candidate — as one admin, through the console.
  Record what was seen.

## Next: candidate-schedule loop (paused, not abandoned)

The first slice — the candidate coming back — is merged (PR #11). Remaining:

1. Source-versus-candidate delta classification.
2. The planner candidate decision.
3. The master-adoption record.

## Deferred: three role tiers (Phase 2)

`control`, `supervisor`, `field`, and read-only `viewer` replace the nine declared roles, with a
four-eyes rule so no single person advances both halves of the two-step review. Do not number the
contract ADR-012 — that number is taken in `dezrobbo1/Shutdown-Tracker`. Read
`docs/product/user-tier-and-assignment-model.md` in that repository before starting.

## Smaller items

- Error bodies that name the field and the reason, instead of bare `Bad Request`.
- A database test asserting `expected-operational-mapping.json` against the walkable fixture.
- Remove two unreferenced fixture example files and two `@ConditionalOnProperty` spike runners
  superseded by real tests; add the repeated-`Resource` case to `MspdiCandidateDifferenceTests`.

## Standing constraints

Microsoft Project remains the schedule authority: no CPM or schedule calculation, no native `.mpp`
writing, no silent write-back, append-only audit with explicit approval and supersession semantics.
Field progress passes through supervisor review and then planner review before candidate
generation; Phase 2 moves that separation from roles to people and must not weaken it.

## Validation

From the repository root, run the backend and frontend test suites as wired in CI
(`.github/workflows/ci.yml`) before merging.
