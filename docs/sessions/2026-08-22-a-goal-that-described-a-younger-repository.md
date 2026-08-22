# 2026-08-22 — A goal that described a younger repository

Third entry of the day. The previous two closed a cleanup backlog; this one found that the document
those sessions were supposed to be working against had stopped describing the repository some ten
pull requests earlier.

## Scope

Asked to review the current repository state for continuation. The review found the active goal
stale rather than the code broken, so the work became reconciling `docs/goals/ACTIVE.md` against
`git log` and rewriting it. Documentation only; no runtime change, and no slice of the goal was
taken.

## What was found

**The contract had not been updated in ten pull requests.** `git log --follow -- docs/goals/ACTIVE.md`
gives its last commit as `aeb554d`, *Take the deployment repairs before the journey test*. Pull
requests #14 through #24 all merged after that and none touched it. `AGENTS.md` makes this document
the first thing a session reads and the definition of the current task contract, so the drift is not
cosmetic: a session following the protocol as written would have re-taken finished work.

The Status section still read *"Slice 1 of Phase 1 is merged. Slices 2–9 are not started."* Against
`git log --first-parent origin/main`:

| Claimed | Actually |
|---|---|
| Phase 0 upcoming, six items | All six merged — #14/#15, #16, #17, #18, #21, plus a deployment repair |
| Slices 2–9 not started | Slice 4 merged (#14), slice 8 merged (#19), slice 9 merged (#20) |
| Phase 3 pending | Complete, and taken **before** Phase 2 rather than after it |
| Four stacked defects block the journey | All four repaired; the chain was walked end to end on 2026-08-21 |

Genuinely not started, and confirmed in source rather than inferred from the document: **the journey
test** — nothing under `services/api/src/test` walks the chain, and the only files matching *journey*
do so in prose; **the field evidence gate** — `CAPTURE_EVIDENCE` is honoured by the console and
declared in `packages/api-client`, with zero references to capabilities anywhere in
`apps/mobile-pwa`; and **both Phase 2 slices** — `docs/adr` holds ADR-001 through ADR-011 only, and
all nine roles are still declared in `ProjectRole` and mirrored in the API client.

**One completion condition is genuinely unmet, and it needs no code.** The goal requires that the
journey has been walked by a person end to end. It has been walked over HTTP, and
[the first walk](2026-08-21-the-first-walk.md) is careful to say that this "proves the chain, not the
console". Searching the whole of `docs/` for *interface walk* returns only that entry and the
walkthrough document. The review deployment answers `{"status":"UP"}` and returns all four seeded
identities, so nothing stands between this condition and somebody doing it.

**Two items were homeless.** *Error bodies that name the problem* was raised as its own slice by the
first walk and appeared in no phase of the goal. The Operational Mapping test asserting
`expected-operational-mapping.json` was left open by two separate entries and likewise tracked
nowhere.

**Two backlogs were tracking one change.** Phase 1 slice 3 makes `export_state` and `export_batch_id`
advance through the batch lifecycle. Item 5 of the hygiene backlog retires the six
`ProgressExportState` values nothing writes and rebuilds the partial index in `V009` that is built on
one of them. These are one migration, and were being carried as two pieces of work in two documents.

**The tree itself is green and needed nothing.** That was checked before concluding the problem was
documentation.

## What changed

- `docs/goals/ACTIVE.md`, rewritten. Phase 0 and Phase 3 move into Completed goals with their pull
  request numbers; the Status section states what is merged and what remains; the Outcome narrows
  from *the journey does not work* to *the journey has not been driven through the interface*; the
  success criteria are marked met, partly met or unmet individually; the two homeless items are
  adopted; and the hygiene backlog is linked rather than copied, with its item 5 named as Phase 1
  slice 3.
- This entry, and its index line.

Unchanged, because they were still accurate: the Non-goals, the Standing constraints, the Required
validation set, the paused candidate-schedule slices, and the Manual Microsoft Project gate.

## Decisions

**Link the hygiene backlog; do not copy it.** Restating items 2 to 9 inside the goal document would
have created a second copy that drifts from the session entry that owns them — which is the exact
failure this session was cleaning up. The one thing that had to be stated in both places is that
item 5 and slice 3 are the same migration, because that is a collision a reader of either list alone
cannot see.

**Narrow the Outcome rather than declare it met.** The temptation was to mark the walkability outcome
achieved, since the chain demonstrably runs. Rejected: the goal's own completion condition asks for a
person walking it through the interface, and fifteen successful `curl` steps are not that. Weakening
the condition to match what was done would have destroyed the only thing that still makes the goal
falsifiable.

**Record the counts to expect in Required validation.** 523 backend and 144 frontend, with their
per-module split. A validation section that names commands but no expected results cannot catch a
test being lost — the suite goes quietly green with fewer tests in it.

**Keep the stale-document story in the goal, not only here.** Two sentences in the Status section
record that the document went stale and where the entry is. A goal document that silently becomes
accurate again teaches nobody why it drifted.

**Do not renumber the surviving slices.** They keep the numbers 2, 3, 5, 6 and 7 despite the gaps,
because four earlier entries and several pull request descriptions refer to them by number.

## Verified

Run on this machine, on this branch:

- `mvn test` — BUILD SUCCESS. **523 tests**, 448 in `services/api` and 75 in
  `services/project-worker`, 0 failures, 0 errors, 0 skipped. The database-backed tests executed
  against the embedded PostgreSQL rather than being skipped.
- `npm test` — **144 passing**, being 73 console, 43 mobile-pwa and 28 api-client, 0 failures.
- `npm run build` — all three workspaces, including `tsc --noEmit`, clean.
- `git status -sb` and `git diff --check` — clean.
- Every pull request number written into the rewritten document checked against
  `git log --first-parent origin/main`.
- Every relative link in the two edited files resolved against the working tree.

The claim that a slice is unstarted was checked in source in each case, not taken from the previous
state of the goal document.

Not run, and not claimed: `scripts/db/validate-migrations.sh`, which needs the Docker Compose
PostgreSQL stack that this host does not have. It is unreachable from a documentation-only change.
No manual Microsoft Project round trip was attempted; that gate remains as it was.

## Corrections

The rewritten document corrects several claims that were true when written and had since become
false. They are listed here because the goal document no longer contains them:

- *"the journey does not currently work"*, and *"everything from the fourth step onward has been
  unreachable through the console"*. Both were repaired across #16 and #12; the chain was walked on
  2026-08-21.
- *"Today a membership can only be created by raw SQL, so nobody can walk the chain at all."* False
  since #14 seeded four review identities behind a default-disabled flag.
- The Design C prototypes being *"recoverable from history despite the README saying otherwise"* —
  that was a pending correction and is now a made one, in #19.
- The deferred `critical_updates.idempotency_key` note said *"unchanged from three sessions ago"*.
  Every entry since has repeated a count and understated it; the rewrite records the fact without
  counting, so the number cannot go stale again.

## Left open

- **The interface walk**, unchanged and now the only unmet completion condition of this goal. The
  deployment is up and seeded; the procedure is written; nobody has clicked it.
- **Phase 1 slices 2, 3 and 5, and Phase 2**, as the goal now states.
- **Hygiene items 2 to 9**, unchanged, per
  [A handover that had aged](2026-08-22-a-handover-that-had-aged.md#left-open).
- **The drift guard has never been run against the review deployment.** It is read-only and needs no
  Docker, but it needs to reach that database, and this session did not. Recorded in the goal under
  Open verification, because behavioural evidence that V012 and V014 are present is not the same as
  the ledger saying so.
- **No check asserts that this document stays current.** The failure this entry describes was found
  by a person reading two documents side by side. Nothing would have caught it, and nothing would
  catch it happening again — a pull request can merge a slice without touching the goal that
  describes it.
