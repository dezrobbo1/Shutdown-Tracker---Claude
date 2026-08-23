# Active Goal — A Product Somebody Can Walk

## Status

Phase 0 is **complete**. Phase 3 is **complete**, taken out of order before Phase 2. Of Phase 1,
slices 1, 2 and 4 are merged — slice 1 as pull request
[#12](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/12), slice 2 as the journey test,
and slice 4 early as part of Phase 0.

Slice 3 is **open as pull request
[#27](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/27) and not yet merged**; see
[the session entry](../sessions/2026-08-23-the-batch-says-what-it-carried.md). Move it up to the
merged list when it lands, rather than leaving the document to describe a younger repository
again.

What remains is **Phase 1 slice 5, and all of Phase 2**, listed under Remaining work below.

This document was itself out of date between 2026-08-21 and 2026-08-22. Ten pull requests — #14
through #24 — merged without it being updated, so it went on describing Phase 0 as upcoming work
after all six of its items had landed, and went on describing four defects that were by then
repaired. It is restated here against `git log --first-parent origin/main`. The lesson is recorded in
[this session's entry](../sessions/2026-08-22-a-goal-that-described-a-younger-repository.md).

The candidate-schedule goal is **paused, not abandoned**. Its first slice — the candidate coming
back — is merged as pull request [#11](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/11).
Its remaining three slices are recorded at the end of this document and resume after this goal.

## Outcome

Somebody can walk the product from a field update to a returned candidate schedule, as three roles
rather than nine, on a surface that looks like Design C.

**The chain now runs end to end.** All fifteen steps were walked successfully on 2026-08-21 and are
recorded in [docs/sessions/2026-08-21-the-first-walk.md](../sessions/2026-08-21-the-first-walk.md).
That walk was taken over HTTP as the seeded identities, which proves the chain but not the console.

So the residual claim is narrower than the one this goal was written against. It is no longer that
the journey does not work; it is that **the journey has not been driven through the interface**, and
that nothing fails when a link between two working steps is severed. Each remaining slice below
addresses one of those two.

## Remaining work

Ordered. Each slice is one reviewed outcome on one branch, finished before the next starts.

### Phase 1 — A working flow path

- ~~**Slice 3 — the batch says what it carried.**~~ **Done, awaiting review.** `V015` retires the
  six `ProgressExportState` values nothing ever wrote, leaving
  `not_eligible -> eligible -> in_export_preview -> exported` plus `superseded`, and an export
  preview now claims the updates it was built from, releases them if it is rejected, and marks them
  exported when its artifact is generated. This was also item 5 of the hygiene track below; it must not be
  taken again there.

  The Docker Compose migration job still has to confirm `V015` — this machine has no Docker, so the
  migration sequence and the export-integrity suite were driven against a local PostgreSQL instead.
- **Slice 5 — the field evidence gate.** `CAPTURE_EVIDENCE` is never checked in the field app; the
  control is offered and the server refuses it. The capability exists in `packages/api-client` and
  is honoured by the console; `apps/mobile-pwa` does not reference capabilities at all.

### Phase 2 — Three role tiers

- **Slice 6 — the contract.** ADR-012 records four roles, the capability mapping, the four-eyes
  rule, and organisation/discipline as a membership attribute rather than a role. No ADR currently
  mentions roles at all — `docs/adr` holds ADR-001 through ADR-011 — and this changes a documented
  boundary, so the decision is written before the code.
- **Slice 7 — the tiers.** `control`, `supervisor`, `field`, and a read-only `viewer`, replacing
  nine roles. All nine are still declared, in `ProjectRole` and mirrored in
  `packages/api-client/src/identity.ts`.

### Raised during the walk, and not yet placed in a phase

- **Error bodies that name the problem.** Three requests refused during the first walk each returned
  a bare `Bad Request` naming neither the field nor the reason. The applications send typed requests
  and do not hit this, so it blocks nothing — but it makes the API hard to drive by hand and will
  slow anyone else's first integration. Recorded as its own slice in
  [the first walk](../sessions/2026-08-21-the-first-walk.md), and homeless until now.
- **A mapping test.** `expected-operational-mapping.json` records what the walkable fixture should
  resolve, and nothing asserts it. That is a database test in the API, and it is the natural thing
  to hold the fixture honest.

## Hygiene track

A separate backlog, recorded by the repository-wide review in
[2026-08-22-a-handover-that-had-aged.md](../sessions/2026-08-22-a-handover-that-had-aged.md) and
restated as unchanged by
[the entry after it](../sessions/2026-08-22-left-open-and-closed-the-same-hour.md). Items 2 to 9 of
that list stand; item 1 closed as pull request
[#23](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/23).

It is referenced rather than copied here, so the two lists cannot drift. **Item 5 of that list is
Phase 1 slice 3 above** — the same migration, tracked once.

The items are ordered there by ratio of value to risk, and the first three are free: two
unreferenced fixture example files, two `@ConditionalOnProperty` spike runners superseded by real
tests, and a repeated-`Resource` case in `MspdiCandidateDifferenceTests`.

## Success criteria

- ~~A planner can create an export preview, approve it, generate the artifact, record the Microsoft
  Project open and verification, and return the recalculated candidate — through the interface, not
  only through the API.~~ **Met.** The import and download doors landed in pull request
  [#16](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/16); the controls exist and are
  capability-gated. Whether a person can in fact drive them is the interface walk, below.
- ~~One test walks the whole chain and fails if any link between two steps is severed.~~ **Met.**
  `ProductJourneyTests` walks all sixteen steps through the controllers against a real database, as
  three identities. Every severed link tried against it failed it; see the session entry for which,
  and for the honest limit — each was also caught by an existing unit test.
- Four roles replace nine, and no single person can advance both halves of the two-step review.
  **Not met** — Phase 2.
- ~~The same operational state looks and reads the same in both applications.~~ **Met** by the
  shared token layer in pull request
  [#20](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/20).
- Every control a role cannot use says why, rather than failing at the server. **Partly met.**
  Disabled controls now look disabled; the field evidence gate is Phase 1 slice 5.

## Non-goals

- Any schedule calculation by Shutdown Tracker. Unchanged and non-negotiable.
- New top-level navigation. The console stays Today, Tasks, Problems, Evidence, Exports; the field
  app stays My Work, Today, Problems, Evidence, Sync. Design C's own zone names are explicitly not
  the product's information architecture.
- User and membership management over HTTP. The actor still arrives on a gateway-trusted header, so
  a membership endpoint would grant any role to anyone who can set a header — worse than the raw SQL
  it would replace. It belongs to the production authentication goal.

  The review-identity seeder shipped in Phase 0 does not breach this. `GET /api/review-identities`
  is read-only, seeds only against the synthetic review project, and is disabled by default; the
  reasoning is in
  [2026-08-21-identities-to-walk-it-as.md](../sessions/2026-08-21-identities-to-walk-it-as.md).
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

The counts to expect on a green tree are **535 backend** — 460 in `services/api`, 75 in
`services/project-worker` — and **144 frontend**, being 73 console, 43 mobile-pwa and 28 api-client.
A number below these means a test was lost, not that the suite got faster. The backend figure was
523 when this document was last restated, before the journey test added two and slice 3 added
ten.

Verify GitHub Actions on the branch head. A previously green run is not evidence for a later commit.

## Completion conditions

- Each slice is merged, or explicitly recorded as not taken and why.
- `mvn test`, `npm test`, and `npm run build` pass with no skipped test standing in for a check that
  was claimed.
- Migration validation passes, or its blocker is reported precisely.
- GitHub Actions is green on the final head.
- **The journey has been walked by a person end to end, through the interface, and the handoff says
  so.** This is the last unmet condition. The chain was walked over HTTP on 2026-08-21 and the
  repeatable procedure is written down at
  [docs/testing/product-walkthrough.md](../testing/product-walkthrough.md), but its controls have
  never been clicked. The review deployment is up and its four identities are seeded, so this needs
  no code — only somebody doing it, and recording what it found.
- Documentation matches the implementation, including what remains unimplemented.

## Deferred, and why

**Supervisor review on the field app.** Supervisors hold `REVIEW_TASK_PROGRESS`, but the field app
has no review surface, so review is desktop-only. Taking it needs a crew-scoped review list, offline
semantics for a review *decision* rather than a report, and a placement decision under the navigation
freeze. That is a product goal, not a wiring fix, and the journey is demonstrable without it because
the console has the surface. It is also item 9 of the hygiene track, where it is named as the one
genuine gap in the console/mobile split.

**`critical_updates.idempotency_key` still has a plain index** where `problems` and
`task_progress_updates` each have a partial unique one, so two concurrent retries of a queued
Critical Update could produce two rows. Narrow, and its own change. `V006` still carries the
non-unique index and no later migration touches it. This has now been carried unchanged for eight
sessions, and each entry has understated the count; it is recorded here rather than counted again.

## Open verification

**The drift guard exists; the deployment has not been checked with it.** Pull request
[#21](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/21) built
`scripts/db/check-schema-drift.sh`, which is read-only and needs no Docker — but it needs to reach
the database it is asking about, and no session records it having been run against the review
deployment. V012 and V014 were applied there by hand, and the walk that followed exercised the
features depending on them, so there is behavioural evidence but no ledger assertion. Running the
guard against the deployment would close this in one command.

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

## Completed goals

### Phase 0 — A deployment somebody can walk on — merged

Interposed ahead of Phase 1 slice 2, because the journey could not be walked at all until it was
done. None of it changed what the product does; all of it was what stood between the product and a
person using it.

- **The schema and the host.** The live database was missing V012 and V014 — V012 was skipped and
  V013 applied on top of the gap — and two storage roots were unset and resolved under a root-owned
  working directory, so the first evidence or candidate upload failed at request time while the
  health check still reported UP. Deployment configuration only; no pull request. Both roots now
  hold files. See Open verification above for what is still unasserted.
- **Identities to walk it as.** Pull requests
  [#14](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/14) and
  [#15](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/15), the second correcting the
  role wire format. This is Phase 1 slice 4, taken early: nothing downstream of supervisor review
  could be reached by one person holding one membership.
- **Import and download through the interface.** Pull request
  [#16](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/16). A planner could previously
  get a schedule in only by `curl` and the generated artifact out only from the server filesystem.
- **A fixture worth walking.** Pull request
  [#17](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/17). The only fixture declared no
  resources, assignments or custom fields, which left Operational Mapping, Exports › People and the
  field app's My Work inert.
- **The walkthrough itself.** Pull request
  [#18](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/18), producing
  [docs/testing/product-walkthrough.md](../testing/product-walkthrough.md).
- **A migration drift guard.** Pull request
  [#21](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/21). Migrations were applied by
  hand against a database with no history table, and the check that should have caught the missing
  ones carried a hand-maintained table list that was itself two tables out of date. Both the guard
  and the validation script now derive the expected tables from the migration files.

### Phase 3 — Design C — merged

Taken before Phase 2 rather than after it.

- **The prototypes.** Pull request
  [#19](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/19) restored the two Design C
  files from history and corrected the README claim that they were unrecoverable.
- **A shared token layer.** Pull request
  [#20](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/20) added the
  `packages/design-tokens` workspace, then Design C's palette, visible focus, the six documented
  status classes, and the console and field component treatments. Three treatments were deliberately
  left short of the prototypes and are recorded in
  [the surface entry](../sessions/2026-08-21-a-surface-that-looks-operational.md).

### Phase 1 slice 2 — The journey test — merged

One test walks the whole chain through the controllers against a real PostgreSQL, as a planner, a
supervisor and a field user, feeding each step only what the previous step returned. The two project
worker client interfaces are the only things stubbed. It is the safety net Phase 2 changes roles
underneath. See
[the journey-test entry](../sessions/2026-08-22-one-test-that-walks-the-whole-thing.md).

### Phase 1 slice 1 — The export queue — merged

Pull request [#12](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/12). The console built
its candidate list from the planner queue and filtered it for updates the planner had already
approved — an empty intersection, because an update leaves that queue at the moment it becomes
eligible. Added the queue that answers the question actually being asked, and repaired three defects
stacked behind it: the missing candidate approval event, the unaccepted-snapshot selection, and a
field the export whitelist always refuses.

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

### Repository hygiene — merged

Two pull requests outside any slice of this goal:
[#22](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/22) deleted two signpost READMEs
that had drifted into being false and replaced a committed DOCX the repository's own policy forbids;
[#23](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/23) left one document answering
"what is this product", naming ADR-008 the owner of the MVP scope boundary.
