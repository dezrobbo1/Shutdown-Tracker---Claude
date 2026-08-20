# Active Goal — What Microsoft Project Did To The Schedule

## Status

Active on `feat/candidate-return-path`, the first of four slices.

Three goals are complete and merged. They are summarised under "Completed goals" below rather
than kept in full; the session entries in [docs/sessions](../sessions/README.md) hold what was
decided and why.

## Outcome

A planner can see what Microsoft Project did to the schedule, and decide about it.

Candidate generation already exists: Shutdown Tracker writes the approved execution inputs into
the accepted source and proves it wrote nothing else. What happens next is unrecorded. Microsoft
Project opens the candidate, recalculates it, and produces a schedule whose dates, durations,
roll-ups, slack and criticality may all have moved — and none of that ever comes back. Nothing
compares the recalculated candidate to the source, nothing separates the two inputs the planner
approved from the two hundred consequences Project derived from them, and no record says whether
a planner accepted the result or adopted it as the next master.

`docs/product/project-candidate-schedule-handoff.md` has described this contract from the start,
under "Candidate delta" and "Adoption". This goal implements it.

The end state: a planner opens a candidate run, sees the source and candidate identities and
their hashes, sees every difference between them classified as an approved input, a
Project-calculated consequence, or something unexplained, decides whether the candidate is
acceptable, and — separately, later, and never by implication — records that it became the master.

## Slices

Ordered, one reviewed outcome per branch. Each is finished — API, console, tests, docs — before
the next starts.

1. **The candidate comes back.** *(active, `feat/candidate-return-path`)* There is no way to
   return the schedule Microsoft Project calculated, so there is nothing to compare against
   anything. A planner uploads the recalculated candidate against the export batch whose artifact
   Project opened. It becomes a candidate schedule run with its own file identity and SHA-256,
   bound to the accepted source it must have been derived from.
2. **The delta, and what each difference is.** Compare the accepted source against the returned
   candidate and classify every difference as `approved_input`,
   `project_calculated_consequence`, or `unexpected_difference`. The comparison belongs to the
   project worker, which is where Project processing lives.
3. **The planner decision.** Accept or reject a candidate, bound to one candidate hash and one
   delta, audited, and surfaced read-only in the console's Exports zone.
4. **The adoption record.** A separate fact with its own actor, timestamp and lineage. Accepting a
   candidate never records adoption, and nothing may infer one from the other.

## Success criteria

- A returned candidate is a separate immutable artifact. The accepted source and the generated
  candidate are unchanged by its arrival, and its own identity cannot be edited afterwards.
- A candidate run is bound to the exact accepted source file hash recorded at import, so a
  candidate can never be reviewed against a schedule other than the one it was derived from.
- Every source-versus-candidate difference is classified, and an unexplained difference is
  reported as unexplained rather than absorbed into either explained category.
- A Project-calculated consequence is labelled as one. Its presence never widens what Shutdown
  Tracker may write directly.
- Candidate acceptance and master adoption are two records, two decisions, and two audit events.
- The console shows the states this creates, including a candidate that was rejected and a
  difference nothing can explain.

## Non-goals

- Any schedule calculation by Shutdown Tracker. The delta reports what Project did; it does not
  derive dates, durations, roll-ups, slack or criticality, and it never fills a value in.
- An editable Gantt, dependency editor, or replacement scheduling UI. The candidate comparison is
  read-only, as `AGENTS.md` requires.
- Writing back to the accepted master. Adoption is recorded, never performed.
- The planner-controlled Microsoft Project companion. Returning the candidate stays a manual
  planner action; the automated mechanism needs its own implementation ADR.
- Native `.mpp` candidates, unchanged from the completed candidate goal.
- Production authentication and production object storage, both their own goals.

## Standing constraints

The product boundaries in `AGENTS.md` apply unchanged: Microsoft Project remains the schedule
authority, no CPM or schedule calculation, no native `.mpp` writing, no silent write-back, and
append-only audit with explicit approval and supersession semantics.

`docs/product/approval-export-state-model.md` states the shape this work must take: candidate work
introduces a separate candidate-schedule run entity rather than overloading the export batch's
`verified` state, which means only that a generated artifact opened as expected.

## Required validation

From the repository root:

```text
git status -sb
git diff --check
mvn test
npm ci
npm test
npm run build
bash scripts/db/validate-migrations.sh
```

`mvn test` and the frontend checks need no Docker. `validate-migrations.sh` does, and states so
when it is missing. Report any check that could not be run rather than implying it passed.

Verify GitHub Actions on the branch head. A previously green run is not evidence for a later
commit.

## Completion conditions

- Each slice above is merged, or is explicitly recorded as not taken and why.
- `mvn test`, `npm test`, and `npm run build` pass with no skipped test standing in for a check
  that was claimed.
- Migration validation passes, or its blocker is reported precisely.
- GitHub Actions is green on the final head.
- `docs/product/project-candidate-schedule-handoff.md`, `docs/product/approval-export-state-model.md`
  and the root `README.md` describe what the code does, including what remains unimplemented.
- The handoff states what changed, what was verified, and what remains pending.

## Manual Microsoft Project gate

Inherited from the completed candidate-schedule goal, unchanged and still pending. No automated
result may be reported as a manual Microsoft Project round-trip.

The remaining human gate is for a planner to generate a synthetic MSPDI/XML candidate schedule and
confirm that it opens in Microsoft Project as a complete schedule, preserves task UID and ID
identity along with the source's summary structure, WBS ancestry, calendars and dependency links,
differs from the accepted source only in the approved leaf-task values for the three authorized
fields, excludes summary-task actuals authored by Shutdown Tracker, leaves the accepted source file
unchanged, and performs no master-file update through Shutdown Tracker.

Microsoft Project recalculating dependent values in the candidate is expected and is not a failure.

This goal adds a second human gate, for slice 2 onward: the candidate a planner returns must be
one Microsoft Project actually saved, so the delta is proved against a real recalculation rather
than only against synthetic fixtures.

## Completed goals

### Green baseline — merged

`main` became trustworthy: every committed check passes locally and in GitHub Actions, and each
check proves what it claims to prove. `ExportIntegrityPostgresIntegrationTests` had been reporting
green locally while silently skipping itself for want of Docker, the migration validation script
rejected the repository as soon as V008 existed, and the committed PostgreSQL export-integrity
suite validated a schema the application no longer ran. The checks this goal fixed are the ones
every goal since has been validated by. See
[docs/sessions/2026-08-17-fresh-repo-green-baseline.md](../sessions/2026-08-17-fresh-repo-green-baseline.md).

### Candidate schedule — merged

A generated candidate became a schedule rather than an extract: the accepted Project source with
the approved execution inputs written into it, opening in Microsoft Project with calendars,
dependency links, WBS ancestry, summary structure and resource assignments intact. Authority is
proved by differencing the written candidate against the source and requiring that only approved
`(task UID, field)` pairs differ, which is stronger than the element allowlist it replaced: an
allowlist proves nothing about what it removed. Merged as pull request
[#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3), with a follow-up correction to
element placement in [#5](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/5). The
manual Microsoft Project gate above belongs to this goal and is still pending. See
[docs/sessions/2026-08-17-candidate-schedule-differencing.md](../sessions/2026-08-17-candidate-schedule-differencing.md)
and [docs/sessions/2026-08-18-candidate-element-placement.md](../sessions/2026-08-18-candidate-element-placement.md).

### A front end that does what it shows — merged

Every surface the two applications show became a surface that works. Five slices, each its own
branch: evidence carrying its file, a project-wide evidence list, Critical Update reporting from
the field app, offline problem raising, and assignment-scoped work lists. The information
architecture was unchanged — five console zones, five field zones — and nothing in the goal was
recorded as not taken. See the session entries from 2026-08-18 and 2026-08-19.

One item was left open by the last slice and remains open: `critical_updates.idempotency_key` has
a plain index where `problems` and `task_progress_updates` each have a partial unique one, so two
genuinely concurrent retries of a queued Critical Update could still produce two rows. It is narrow
and belongs to its own change.

## Next after this goal

Production authentication is the largest item left on `README.md`'s "Not production-complete yet"
list. The actor still arrives through a gateway-trusted header rather than a validated token;
authorization itself is enforced from stored membership and is not what is missing.
