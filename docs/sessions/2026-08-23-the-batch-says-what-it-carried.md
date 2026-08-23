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

- `mvn test` — **BUILD SUCCESS**, 456 in `services/api` and 75 in `services/project-worker`. The
  goal document's expected 448/75 predates pull request #26, which added two; six of the eight new
  api tests are this slice's.
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

## Left open

- The `distinct()` in the claim's expected count is load-bearing and now has a test
  (`twoFieldsOfOneUpdateClaimItOnce`), but the shortfall path is only proved for a superseded
  update. A second batch racing for the same update is refused by the same guard and is not
  separately tested.
- Everything else in `docs/goals/ACTIVE.md`: Phase 1 slice 5, all of Phase 2, and the walk through
  the interface.
