# 2026-08-23 — The batch says what it carried

Phase 1 slice 3, picked up from a working tree that already held most of the production code and
none of the tests. The session began as a status review of the repository and became the finishing
of the slice it found half-done.

## Scope

Make `export_state` and `export_batch_id` advance through the export batch lifecycle, so the audit
can answer which batch carried which approved field update. In scope: the migration that retires the
dead states, the three writes an export batch makes to the rows it carries, the tests that prove
them, and the frontend type that mirrors the enum. Out of scope: everything else in the goal.

## What was found

**The working tree held production code with no test behind it.** Seven modified files and three
new ones, +206/−61, of which the entire test-side diff was a third constructor argument threaded
through about twenty call sites. Not one new assertion. The test double's own javadoc named
`TaskProgressExportBindingTests` "against a real PostgreSQL"; no such class existed.

**The migration had never been run.** Its first execution failed:

```text
ERROR: operator does not exist: progress_export_state_v2 = progress_export_state
```

`idx_task_progress_updates_export_candidates` is partial on
`export_state IN ('eligible', 'approved_for_export')`. A partial index predicate holds constants of
the column's type, so `ALTER COLUMN ... TYPE` rebuilds the index and compares the new enum against
the old one, which has no equality operator between them. The migration dropped that index *after*
the type swap. It has to be dropped before.

**The frontend still declared nine states.** `packages/api-client/src/index.ts` and
`apps/console/src/formatting.ts` were untouched by the slice, so once the enum became five the
`Record<ProgressExportState, string>` carried four dead entries and no `EXPORTED` at all —
`ReviewQueueZone` would have rendered `undefined` for any exported update. TypeScript would not have
caught the missing key on its own, because nothing narrowed the union.

**`MigrationSchemaTests` carried a hand-written list of migration filenames**, fourteen long. Adding
a fifteenth migration failed it — not because the migration was wrong, but because a new migration
existed at all. This is the same defect pull request #21 fixed in `validate-migrations.sh`, in a
second place nobody looked.

**The journey test reached batch `VERIFIED` without ever asking what became of the update it
carried.** It asserted `exportState` at `NOT_ELIGIBLE` and `ELIGIBLE` and stopped there, which is
exactly the shape of gap that test exists to close.

## Decisions

**The binding tests drive the real services, not hand-inserted rows.** An export batch line cannot
be inserted by hand without fighting the V007 integrity triggers, and a batch built around them
would not be one the application could produce. `TaskProgressExportBindingTests` therefore walks
candidate → approval → preview through the real services with `JdbcTaskProgressRepository` wired in
as the real binding — deliberately not the recording double, which answers "as many as you asked
for" and would hide a claim that took nothing.

**`MigrationSchemaTests` now derives its expected list** from `infra/migrations` through a new
`EmbeddedDatabase.migrationFileNames()`, rather than gaining a fifteenth hand-written line. Adding
the line would have worked until the sixteenth migration.

**The state model document gets an "as implemented" note rather than an edited table.** The table
under *Export candidate states* is a target lifecycle, and the document says in terms that these
tables must not be edited to match a target before the code does. The column now implements more of
that target than it did, and the three values it still does not carry are named with the column that
owns each fact instead.

**`exported` is terminal and `verified` is not added to this column.** How far the carrying batch
got is the batch's own status, reached through `export_batch_id`. The same document warns against
overloading `verified`, which means the artifact opened in Microsoft Project as expected — not that
anything was recalculated or adopted.

**A rejected batch releases its updates.** Leaving them claimed would strand approved field work
permanently, because the export queue offers only updates no batch has claimed, and there would be
no way back short of a correction.

## Verified

From the repository root, all in this session:

- `mvn test` — **BUILD SUCCESS**, 460 in `services/api` and 75 in `services/project-worker`. The
  goal document's expected 448/75 predates pull request #26, which added two; the other ten are
  this slice's.
- `npm ci`, `npm test` — 73 console, 43 mobile-pwa, 28 api-client, 144 total, all passing.
- `npm run build` — all three workspaces, `tsc --noEmit` included.
- `git diff --check` — clean.
- Every migration V001–V015 applied in order to a real PostgreSQL 16.2, followed by the expected/
  unexpected table comparison from `validate-migrations.sh` (35 tables, none missing, none extra),
  and then `run-export-integrity-suite.sh` in full — the current-policy assertions, all ten
  concurrency checks, and the late-V007 rollback check. All passed.

**`scripts/db/validate-migrations.sh` itself could not be run here**: this machine has no Docker.
What is recorded above is the same sequence of checks driven against a local server started from the
embedded PostgreSQL binaries, which is evidence for the SQL but not for the Docker Compose job.

GitHub Actions then ran that job. All four jobs passed on both commits of pull request #27 —
*Frontend test and build*, *Backend Maven test*, *Docker image build*, and *Migration and
export-integrity validation*. The Docker path is therefore confirmed, by CI rather than locally.

No manual Microsoft Project verification was performed and none is claimed.

## Review

Codex reviewed the pull request and raised three findings. Two were real and are fixed on the
branch; the third is real, pre-existing, and deliberately not taken here.

**`exported` was being set at verification rather than at generation.** Accepted. The row's fact is
that its value was written into an artifact; whether a planner then opened and verified that
artifact is the batch's fact, read from its status. Setting it at verification meant a generated
batch nobody clicked through left its updates saying they were still "in a preview" — and it
contradicted the principle the migration itself argues, that a fact owned by the batch must not be
mirrored onto the row. `FAILED` exists in `ExportBatchState` but nothing in the service ever writes
it, and only a draft preview can be rejected, so generation is the last point at which the outcome
is still in doubt.

**A rejected batch left its id on an update superseded while it held it.** Accepted, and worse than
it first looks: `markSuperseded` sets `export_state = 'superseded'` unconditionally, so the release
predicate — which only looked at `in_export_preview` — skipped exactly those rows and left
`export_batch_id` naming a batch that was rejected and carried nothing. That is false provenance in
the column this slice exists to make trustworthy. Release now unlinks superseded rows too, without
making them eligible again: the value has been replaced and must not travel.

### Second round

Codex reviewed the fixes and raised three more. All three were real; all three are fixed.

**The TypeScript contract still described `EXPORTED` as requiring verification.** My own miss from
the previous fix — I moved the transition and updated the Java and SQL comments but left the
`ProgressExportState` JSDoc in `packages/api-client` and one line of this goal document saying the
opposite. A client reading the published contract would have described carried updates as manually
verified.

**The claim ignored line eligibility.** `is_export_eligible` is computed per line as "the current
approval is `approved_for_export`, the task is a leaf, and the field is on the whitelist", and
`requireEligibleCandidates` only requires that *one* line qualifies — so a batch can be generated
carrying a mix. `ExportArtifactHandoffService` leaves the ineligible lines out of the artifact, but
the claim matched on source type alone, so the excluded line's update was claimed and then marked
exported. That is a row asserting a value travelled when it demonstrably did not.

**A partially carried update was consumed whole.** This one was a regression this branch
introduced, and the fixture in `TaskProgressExportBindingTests` was quietly exercising it: the
helper submitted an update carrying both a percent complete and an actual start, then previewed only
the percent complete candidate. Before this branch nothing was tracked, so the two fields could be
carried by separate batches. With a row-granular binding they cannot: claiming the row marks it
exported and removes it from the queue, so the actual start could never be carried by anything, and
the row would claim a completeness it did not have.

Both are the same root cause — carriage is per line and the binding is per row — and both are fixed
in the claim predicate: it now counts and takes only lines the batch can export, and takes an update
only when the batch covers every exportable value on it. A partial preview fails the existing
shortfall check with a message that names the case. The alternative, per-candidate carriage, is a
schema change and would replace this rule rather than relax it; that is recorded in the state model
document so the choice is visible if it is ever wanted.

Both counts now come from the sealed line set rather than one from the line set and one from the
caller's candidate list, which is what let the two disagree in the first place.

**A candidate can name a progress update it does not describe.** Real, and not taken. Nothing
validates that a candidate whose `source_entity_type` is `task_progress_update` actually matches the
row its `source_entity_id` names — `export_candidate_records` has no foreign key on that column and
V007 validates the candidate against the imported task, not against the progress row. A planner can
therefore cite update X while proposing an unrelated value, and this slice now also binds X to that
batch. The proposed fix — joining task, snapshot, field, value and source version into the claim
predicate — puts the check in the wrong layer: the claim is not where a candidate should first be
found to be lying about its origin. Candidate creation is. That is its own outcome, and it is
recorded under Left open rather than bolted onto this one. Note that the falsifiable *artifact*
predates this change; what is new is the falsifiable reverse link.

## Corrections

The note added to `docs/product/approval-export-state-model.md` in the first commit described the
*Export candidate states* table as "a target lifecycle" and cited the document's own instruction
against editing these tables to match a target. That inverts what the document says: the heading
above those tables states they describe shipped behaviour. The table was in fact accurate for
neither — it listed `rejected` and `exported`, which the column never held, and omitted three values
it did hold — so the honest statement is that the column and the table disagreed, and `V015` moves
the column most of the way to it. The note now says that instead.

## Left open

- **Per-field export carriage, if it is ever wanted.** A batch now takes every exportable value on
  an update or none of them, because the binding is one row per batch. Splitting fields across
  batches would need carriage recorded per candidate. Nothing asks for it today and the shipped
  console never produces a partial preview, so the rule is enforced rather than the schema changed.
- **A candidate that cannot lie about where it came from.** Per the review above: when
  `source_entity_type` is `task_progress_update`, candidate creation should verify the referenced
  row exists, is export eligible, belongs to the same project, snapshot and task, and carries the
  value being proposed for that field. Worth a slice of its own.
- The `distinct()` in the claim's expected count is load-bearing and now has a test
  (`twoFieldsOfOneUpdateClaimItOnce`), but the shortfall path is only proved for a superseded
  update. A second batch racing for the same update is refused by the same guard and is not
  separately tested.
- Everything else in `docs/goals/ACTIVE.md`: Phase 1 slice 5, all of Phase 2, and the walk through
  the interface.
