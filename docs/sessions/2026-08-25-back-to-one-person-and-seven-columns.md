# 2026-08-25 — Back to one person and seven columns

## Scope

Start the console round-trip trial: import, tracking and export, driven by one admin, with the field
app and the role model parked. In scope: the task section's fields and filtering, and the
capabilities one actor needs to walk the whole chain. Out of scope: the field app, Critical
reporting, the tier model, and any change to what an export writes.

## What was found

**Almost none of it needed building.** The estimate this session started with was wrong twice, both
times in the same direction, and both corrections are worth keeping.

*The task box already existed.* `apps/console/src/zones/ExecutionZone.tsx` was already a clickable
task list bound to a detail panel, with a text filter and the leaf-task-only rule enforced. The
trial needed that surface widened, not written.

*The fields were already imported.* Of the seven asked for — name, duration, resource group, planned
start and finish, actuals, percent — five were already columns on `imported_tasks` and already on
the `ImportReviewTaskRow` DTO. The other two were sitting unexposed in `raw_data`:
`durationText` on the task, `group` on the resource. **No migration and no parser change were
needed.** An earlier read of this had concluded the MPXJ extraction was the bottleneck; that was
true of a much wider field list — Critical, slack, constraints, custom fields, none of which are
extracted — and false of this one.

*The join was already being fetched and thrown away.* `useSnapshotTasks` requested the whole
snapshot detail — tasks, resources and assignments — and kept only tasks. Resource group needed no
new request, only the indexing the console was already positioned to do.

**The one real gap was the actor.** `admin` held six capabilities and could not submit progress,
review it, approve an export, generate an artifact, or return a candidate. One admin could not walk
the round trip at all.

## What changed

`ba87021` and the commit above it, on `feat/console-trial-roundtrip`.

Duration and resource group are read out of `raw_data` and exposed on the two DTOs. The console
resolves groups per task through the assignments it already had. The task section carries the seven
fields, filters by resource group as well as text, and splits its columns: everything left of the
shaded three is Microsoft Project's and is never written back.

`admin` gains the nine capabilities the round trip needs.

## Decisions

**Widening the existing zone rather than adding a Tasks zone.** A second task table in a console
whose point is to be simpler would be the confusion the trial exists to remove, and the existing
zone already had the selection behaviour and the leaf-task rule.

**Duration stays the string Project renders.** Filtering or sorting by duration needs the value and
the unit stored apart, which the importer does not do. Offering a filter that sorts "8.0h" against
"2.0d" lexically would be worse than not offering it. Recorded on the field itself so the next
person meets the reason before the idea.

**Shading the writable columns instead of documenting the distinction.** The alternative was a
sentence in a product doc. A table that looks uniformly editable is how somebody comes to type over
a planned date and expect it to mean something, and the console is where that mistake is made.

**Keeping the three review stages, and suspending four-eyes instead.** Collapsing supervisor review,
planner review and export approval into one step was the tempting simplification. Rejected: those
stages are what *produce the export batch*, not merely governance, so collapsing them would mean
rebuilding the chain to ship it. Letting one actor walk all three is a permissions relaxation, and
reversible in an afternoon.

**Repointing the guard tests rather than deleting them.** Four tests asserted that export approval
excludes `admin` — in the console, in the client/server parity check, and in the authorization
service. Deleting them would have made restoring four-eyes a silent re-grant. Each now asserts the
suspension explicitly and still guards the half that has not changed: that no role *other* than the
trial admin may approve. `CapabilityClientParityTests` already existed to stop the two capability
mirrors diverging, and it caught the change in both.

## Verified

- `mvn test` — **537 passing**, 462 in `services/api` and 75 in `services/project-worker`. Two are
  new: `ImportReviewProjectFieldsDatabaseTests`, which persists a parsed schedule to a real
  PostgreSQL and reads duration and resource group back out through `->>`.
- `npm test` — **158 passing**: 81 console, 49 mobile-pwa, 28 api-client. Eight are new, covering
  group indexing (several resources of one group collapsing to one, several distinct groups sorted,
  a resource matched only by external uid, ungrouped and unassigned tasks contributing nothing), the
  filter options, and the two column-legend affordances.
- `npm run build` — both applications, clean. This is what caught the mobile-pwa test fixture that
  the widened DTO left incomplete; `vitest` does not typecheck, so the suite passed while the build
  did not.
- `git diff --check` — clean.

**Could not run:** `scripts/db/validate-migrations.sh` needs Docker and this machine has none. No
migration was added, and `MigrationSchemaTests` applies all fifteen through Flyway against embedded
PostgreSQL in the Maven run.

**Not done:** the trial itself. Nothing here was driven through a browser. The chain was walked by
hand on 2026-08-21 as seeded identities over HTTP, and that walk is not evidence for this one: it
predates both the widened task section and the single-actor capabilities.

## Corrections

The MPXJ extraction was called the bottleneck earlier in this session, before the field list
narrowed. It is not, for these seven fields. It would be for Critical, slack, constraints, deadlines
or custom fields, none of which the importer stores — `raw_data` holds only `guid`, `durationText`
and `milestone` for a task.

`docs/goals/ACTIVE.md` was found describing Phase 1 slice 5 as remaining, though it merged as pull
request #34 on 2026-08-24. That is the second time the document has lagged its own merges — the
first is recorded in the 2026-08-22 entry. Its frontend test counts were also wrong in a way nobody
had noticed: it claimed 43 mobile-pwa tests against an actual 49.

## Left open

- **The trial run.** Import a real schedule, track work through the task box, walk the three review
  stages, generate the export, return the candidate — as one admin, through the interface.
- **Four-eyes is suspended** and must be restored before anything here is called production
  behaviour. The grants to restore are marked `trial:` in `Capability.java` and `identity.ts`.
- **Numeric duration**, if filtering by it is ever wanted. Needs the value and unit stored separately
  by `MpxjProjectEntityExtractionService`.
- **Which percent goes back.** `percent_complete` and `physical_percent_complete` are both stored
  and both shown upstream; the export writes `PercentComplete`. On a shutdown, physical percent is
  often the more honest number, and nobody has chosen deliberately.
- **The role model**, deferred with Phase 2. Its ADR must not be numbered 012 — that number is taken
  in the other repository by an accepted ADR — and `user-tier-and-assignment-model.md` there already
  resolves much of what slice 6 would re-derive.
- Pull requests **#32** and **#33** remain open and untouched by this work.
