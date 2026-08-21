# Active Goal — A Product Somebody Can Walk

## Status

Slice 1 of Phase 1 is **merged** as pull request
[#12](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/12). Slices 2–9 are not started.

**The next work taken is not slice 2.** It is Phase 0 below — making the deployment walkable — and
the reason is this goal's own completion condition that *the journey has been walked by a person
end to end*. A test proves the chain to CI; it does not prove it to a person, and hand-walking has
already found four defects no test would have: two unset storage roots, no way to import a
schedule through the interface, no way to download the generated artifact, and a fixture with no
resources or assignments in it. Slice 2 remains wanted and unchanged as the safety net before
Phase 2 changes roles underneath it.

The candidate-schedule goal is **paused, not abandoned**. Its first slice — the candidate coming
back — is merged as pull request [#11](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/11).
Its remaining three slices are recorded at the end of this document and resume after this goal.

## Outcome

Somebody can walk the product from a field update to a returned candidate schedule, as three roles
rather than nine, on a surface that looks like Design C.

The reason this comes before finishing the candidate goal is that **the journey does not currently
work**. The README describes a continuous chain — field update, supervisor review, planner review,
export preview, artifact, Microsoft Project, candidate returned. The first three steps work.
Everything from the fourth onward has been unreachable through the console since the export
lifecycle was built, behind four stacked defects. Building the next candidate-schedule slice on top
of that would be building a review surface for a batch nobody can create.

Every test passed the whole time. Each proved one step in isolation, and the defects lived between
steps.

## Phases

Ordered. Each slice is one reviewed outcome on one branch, finished before the next starts.

### Phase 0 — A deployment somebody can walk on

Interposed. None of it changes what the product does; all of it is what stands between the product
and a person using it. Taken in this order, because each makes the next possible.

- **The schema and the host.** The live database is missing V012 and V014 — V012 was skipped and
  V013 applied on top of the gap, so offline problem raising would fail on a row it cannot write.
  Two storage roots are unset and resolve under a root-owned working directory, so the first
  evidence or candidate upload fails at request time while the health check still reports UP.
  Deployment configuration only; no code.
- **Identities to walk it as.** This *is* slice 4 below, taken early, because nothing downstream of
  supervisor review can be reached by one person holding one membership.
- **Import and download through the interface.** A planner can currently get a schedule in only by
  `curl` and get the generated artifact out only from the server filesystem. Both are product gaps,
  not testing conveniences.
- **A fixture worth walking.** The only fixture declares no resources, no assignments and no custom
  fields, which leaves Operational Mapping, Exports › People and the field app's My Work inert.
- **The walkthrough itself**, so the walk is repeatable and its findings traceable rather than
  remembered.
- **A migration drift guard.** Migrations are applied by hand against a database with no history
  table, and the check that should have caught the missing ones carries a hand-maintained table list
  that is itself two tables out of date. This is why the first item exists, and it will recur every
  time a migration lands.

### Phase 1 — A working flow path

1. **The export queue.** *(merged,
   [#12](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/12))* The console built its
   candidate list from the planner queue and filtered it for updates the planner had already
   approved — an empty intersection, because an update leaves that queue at the moment it becomes
   eligible. Adds the queue that answers the question actually being asked, and repairs three
   defects stacked behind it: the missing candidate approval event, the unaccepted-snapshot
   selection, and a field the export whitelist always refuses.
2. **The journey test.** One test that walks every step through the controllers against a real
   database, asserting the output of each step is a legal input to the next. This is the artefact
   that fails when a link between two working steps is severed, and it is the safety net Phase 2
   changes roles underneath.
3. **The batch says what it carried.** `export_state` and `export_batch_id` advance through the
   batch lifecycle, so the audit can answer which batch carried which field update.
4. **Identities to walk it as.** *(taken early, in Phase 0)* The guarded review bootstrap seeds
   one user per journey role.
   Today a membership can only be created by raw SQL, so nobody can walk the chain at all.
5. **The field evidence gate.** `CAPTURE_EVIDENCE` is never checked in the field app; the control
   is offered and the server refuses it.

### Phase 2 — Three role tiers

6. **The contract.** ADR-012 records four roles, the capability mapping, the four-eyes rule, and
   organisation/discipline as a membership attribute rather than a role. No ADR currently mentions
   roles at all, and this changes a documented boundary, so the decision is written before the code.
7. **The tiers.** `control`, `supervisor`, `field`, and a read-only `viewer`, replacing nine roles.

### Phase 3 — Design C

8. **The prototypes.** Restore the two Design C files, which are recoverable from history despite
   the README saying otherwise, and correct that claim.
9. **A shared token layer**, then Design C's palette, visible focus, the six documented status
   classes, and the console and field component treatments.

## Success criteria

- A planner can create an export preview, approve it, generate the artifact, record the Microsoft
  Project open and verification, and return the recalculated candidate — through the interface, not
  only through the API.
- One test walks the whole chain and fails if any link between two steps is severed.
- Four roles replace nine, and no single person can advance both halves of the two-step review.
- The same operational state looks and reads the same in both applications.
- Every control a role cannot use says why, rather than failing at the server.

## Non-goals

- Any schedule calculation by Shutdown Tracker. Unchanged and non-negotiable.
- New top-level navigation. The console stays Today, Tasks, Problems, Evidence, Exports; the field
  app stays My Work, Today, Problems, Evidence, Sync. Design C's own zone names are explicitly not
  the product's information architecture.
- User and membership management over HTTP. The actor still arrives on a gateway-trusted header, so
  a membership endpoint would grant any role to anyone who can set a header — worse than the raw SQL
  it would replace. It belongs to the production authentication goal.
- Supervisor review on the field app. Recorded below as deferred, with the reason.
- The source-versus-candidate delta, the planner decision, and the adoption record. Paused, below.

## Standing constraints

The product boundaries in `AGENTS.md` apply unchanged: Microsoft Project remains the schedule
authority, no CPM or schedule calculation, no native `.mpp` writing, no silent write-back, and
append-only audit with explicit approval and supersession semantics.

Field progress must pass through supervisor review and then planner review before candidate
generation. Phase 2 moves that separation from *roles* to *people* — the two decisions must be made
by two different users — and must not weaken it.

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

`mvn test` and the frontend checks need no Docker. `validate-migrations.sh` does, and states so when
it is missing. Report any check that could not be run rather than implying it passed.

Verify GitHub Actions on the branch head. A previously green run is not evidence for a later commit.

## Completion conditions

- Each slice is merged, or explicitly recorded as not taken and why.
- `mvn test`, `npm test`, and `npm run build` pass with no skipped test standing in for a check that
  was claimed.
- Migration validation passes, or its blocker is reported precisely.
- GitHub Actions is green on the final head.
- The journey has been walked by a person end to end, and the handoff says so.
- Documentation matches the implementation, including what remains unimplemented.

## Deferred, and why

**Supervisor review on the field app.** Supervisors hold `REVIEW_TASK_PROGRESS`, but the field app
has no review surface, so review is desktop-only. Taking it needs a crew-scoped review list, offline
semantics for a review *decision* rather than a report, and a placement decision under the navigation
freeze. That is a product goal, not a wiring fix, and the journey is demonstrable without it because
the console has the surface.

**`critical_updates.idempotency_key` still has a plain index** where `problems` and
`task_progress_updates` each have a partial unique one, so two concurrent retries of a queued
Critical Update could produce two rows. Narrow, and its own change. Unchanged from three sessions ago.

## Paused — the candidate schedule delta

Resumes after this goal. Slice 1 of four is merged; the outcome and the remaining three slices stand
as written:

2. **The delta, and what each difference is.** Compare the accepted source against the returned
   candidate and classify every difference as `approved_input`, `project_calculated_consequence`, or
   `unexpected_difference`, in the project worker where Project processing lives.
3. **The planner decision.** Accept or reject, bound to one candidate hash and one delta, audited,
   surfaced read-only in Exports.
4. **The adoption record.** A separate fact with its own actor, timestamp and lineage. Accepting a
   candidate never records adoption.

## Manual Microsoft Project gate

Unchanged and still pending. No automated result may be reported as a manual Microsoft Project
round-trip.

A planner must generate a synthetic MSPDI/XML candidate schedule and confirm that it opens in
Microsoft Project as a complete schedule, preserves task UID and ID identity along with the source's
summary structure, WBS ancestry, calendars and dependency links, differs from the accepted source
only in the approved leaf-task values for the three authorized fields, excludes summary-task actuals
authored by Shutdown Tracker, leaves the accepted source file unchanged, and performs no master-file
update through Shutdown Tracker.

Microsoft Project recalculating dependent values in the candidate is expected and is not a failure.

A second human gate belongs to the paused goal's slice 2 onward: the candidate a planner returns
must be one Microsoft Project actually saved, so the delta is proved against a real recalculation
rather than only against synthetic fixtures.

## Completed goals

### Green baseline — merged

`main` became trustworthy: every committed check passes locally and in GitHub Actions, and each
check proves what it claims to prove. See
[docs/sessions/2026-08-17-fresh-repo-green-baseline.md](../sessions/2026-08-17-fresh-repo-green-baseline.md).

### Candidate schedule — merged

A generated candidate became a schedule rather than an extract: the accepted source with the approved
inputs written into it, proved by differencing the candidate against the source so that only approved
`(task UID, field)` pairs may differ. Pull requests
[#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3) and
[#5](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/5).

### A front end that does what it shows — merged

Five slices: evidence carrying its file, a project-wide evidence list, Critical Update reporting from
the field app, offline problem raising, and assignment-scoped work lists. The information architecture
was unchanged.

### The candidate comes back — merged

The schedule Microsoft Project calculated is recorded when a planner returns it, as a separate entity
bound to the accepted source hash, with a database that refuses a planner decision on a candidate
nothing has compared. Pull request
[#11](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/11).
