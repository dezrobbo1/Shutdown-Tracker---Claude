# 2026-08-22 — One test that walks the whole thing

Fourth entry of the day, and the first this week to add runtime coverage rather than documentation.
Phase 1 slice 2 — the journey test — taken directly after the goal document was corrected to say
it was the next thing.

## Scope

One test that walks every step of the product through the controllers against a real database,
asserting the output of each step is a legal input to the next. In scope: the API's own chain, from
a planner uploading a schedule to a returned candidate being recorded. Out of scope: the project
worker's internals, the console, and the manual Microsoft Project gate.

## What was found

**The chain already worked.** No defect was found in the walk itself — every one of the sixteen
steps passed on the first complete run. That is worth stating plainly, because the last time this
chain was examined end to end it was severed in four places.

**Three assertions in the first draft were wrong about the wire format, not about the product.**
Enum values serialize as their Java constant names — `PENDING`, `SUPERVISOR_ACCEPTED`,
`APPROVED_FOR_EXPORT` — and `packages/api-client/src/index.ts` declares exactly those, so client and
server agree. The exception is `ProjectRole`, which carries `@JsonValue` and emits `field_user`.
That inconsistency is deliberate and documented in `ProjectRole`: it was the defect pull request #15
repaired, because the TypeScript `ProjectRole` union contains the lowercase form and would silently
fail to recognise its own role. Nothing else on the wire has the same problem, so nothing was
changed.

**`ExportPreviewBatchRecord` names its lifecycle column `status`, not `state`**, while
`ExportBatchState` is the type. Only a naming wrinkle; recorded because the first draft assumed the
symmetry and got an empty string rather than an error.

**A malformed body is refused before the capability check.** A planner posting progress with an
unparseable `executionState` gets 400, not 403, because Spring deserializes `@RequestBody` during
argument resolution — before the handler that would have refused them. This leaks nothing (the
refusal is on the shape of the request, not on what the caller may do) and is ordinary framework
behaviour, so it is recorded rather than changed.

## What changed

`services/api/src/test/java/com/shutdowntracker/api/journey/`, two files:

- `ProductJourneyTests` — the walk, plus a second test asserting the separation the walk depends on.
- `StubProjectWorker` — the stand-in for `services/project-worker`.

`docs/goals/ACTIVE.md` records slice 2 as merged and marks the matching success criterion met.

## Decisions

**Stub the worker at its two declared client interfaces, and nothing else.** `ProjectParseJobClient`
and `ProjectExportArtifactJobClient` already exist as seams with a `Disconnected` and an `Http`
implementation each, so replacing them is using the boundary the application defines rather than
reaching around it. Everything between them is real: controllers, capability checks, services,
hand-written JDBC, the migrations, and a genuine PostgreSQL. The alternative — standing the real
worker up in-process — was rejected because it makes the test a two-service integration test and
would have made a failure ambiguous about which service caused it.

**The stub writes the artifact bytes rather than only describing them.** The download step reads
that file and the return step hands it back, so a stub that reported a file it had not written would
pass generation and fail download. That is precisely the between-steps break this test exists to
catch, and it should not be able to originate in the test harness.

**Walk it as three people, not one actor with every capability.** A single omnipotent actor would
walk a journey the product does not have. Actor resolution is the real `TrustedHeaderActorResolver`
driven by the headers a gateway sets, so the identity switch is genuine rather than simulated, and a
second test asserts the two crossings the separation forbids — a planner submitting progress, and a
supervisor approving an export — are refused with 403 at the server.

**One long test rather than several short ones.** Splitting the walk into per-step tests would
recreate the exact failure this slice exists to prevent: each step green, the joins untested. The
steps are numbered in comments and each carries the reason it is a step rather than an implementation
detail.

**Seed the three memberships with SQL.** There is no membership endpoint, and `ACTIVE.md` records
why one would be worse than the raw SQL it replaced while the actor arrives on a trusted header.
Seeding is setup, not a step of the journey, and is not asserted as one.

## Verified

Run on this machine, on this branch:

- `mvn test` — BUILD SUCCESS. **525 tests**, 450 in `services/api` and 75 in
  `services/project-worker`, 0 failures, 0 errors, 0 skipped. The two new tests are the difference
  from the previous 523.
- `npm test` — 144 passing. `npm run build` — clean, including `tsc --noEmit`.
- `git status -sb`, `git diff --check` — clean.

**The test was checked for sensitivity, not only for passing.** A test that walks sixteen steps and
asserts nothing load-bearing would pass forever. Three links were severed in turn in the main source
and the full suite run against each:

| Severed | Journey test |
|---|---|
| Planner approval no longer marks the update eligible | fails |
| The export queue asks for `approved_for_export`, a state nothing writes | fails |
| The recorded artifact URI is not the path the worker was told to write | fails |

The second is the shape of the original defect: a query whose intersection is empty by construction.

**The honest limit: in all three cases an existing unit test also failed** —
`TaskProgressServiceDatabaseTests` for the first two, `LocalExportArtifactStorageTests` for the
third. So this test has not yet caught anything nothing else would. That is a statement about how
well covered these particular links are, not evidence the test is redundant: the defects that
prompted the slice lived in joins that no single test owned, and the mutations available to try were
ones inside components that do have owners. All three mutations were reverted and the tree confirmed
clean before committing.

Not run, and not claimed: `scripts/db/validate-migrations.sh`, which needs Docker. No migration
changed. No manual Microsoft Project round trip was attempted; the candidate returned in the last
step is the downloaded artifact handed straight back, which is why the run is asserted on the lineage
it records rather than on a schedule having been recalculated.

## Left open

- **The interface walk**, unchanged, and still the goal's only unmet completion condition. This test
  proves the chain to CI. It still does not prove it to a person, and it deliberately cannot: it
  drives controllers, not the console.
- **The journey covers one path.** Rejection, correction-requested and supersession are not walked,
  and neither is Operational Mapping — step 4 of the walkthrough document has no counterpart here,
  because `expected-operational-mapping.json` is still unasserted by anything.
- **A mutation the unit tests miss has not been found.** Worth one deliberate attempt when a future
  slice changes a join rather than a component; Phase 2 moving the review separation from roles to
  people is the obvious candidate, and is the change this test was built to sit underneath.
- **The stub worker's schedule is three tasks.** Enough to exercise summary-versus-leaf and one
  assignment per leaf, and deliberately not the walkable fixture: this test asserts the chain, and
  `synthetic-shutdown-areas` exists to be walked by a person.
