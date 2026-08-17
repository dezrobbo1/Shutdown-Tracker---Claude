# Active Goal — Green Baseline on the Fresh Repository

## Status

Active.

This repository is a fresh start. Its history carries the work previously developed on the
`claude-branch` of `dezrobbo1/Shutdown-Tracker`, minus the source-material archive. There are no
open pull requests, no other branches, and no external gates inherited from that repository. The
previous active goal described a final review of PR #48 in the old repository; that work is
already merged into this history and the goal is retired.

## Outcome

`main` is trustworthy: every committed check passes on a developer machine and in GitHub Actions,
each check proves what it claims to prove, and the documented state of the product matches what
the code actually does.

A check that passes locally because it quietly skipped itself is worse than no check. Prefer a
check that runs everywhere over a check that runs only where an optional tool happens to be
installed.

## Completed in this goal

- `ExportIntegrityPostgresIntegrationTests` runs against the shared embedded PostgreSQL server
  used by every other database test, instead of starting its own Docker container and skipping
  itself when no Docker daemon is reachable. It previously reported green locally while never
  running, and failed in CI because its fixture predates V008.
- That fixture now seeds the `users` rows every export-lifecycle attribution column has referenced
  since V008, and statements that attempt to overwrite an established actor name a second real
  user rather than a random UUID, so they are rejected by the immutability rule under test rather
  than by a dangling foreign key.
- `scripts/db/validate-migrations.sh` and `.ps1` no longer require the migration set to be exactly
  V001–V007. That guard rejected the repository on its first line as soon as V008 was added, so
  the job never reached the validation it exists to run.
- `scripts/db/validation/run-export-integrity-suite.sh` scopes its current-policy database to
  V001–V007 explicitly, with the reason stated in the script, instead of globbing every migration
  and then rejecting the result.

## Next: extend export-integrity validation to the current schema

The committed PostgreSQL export-integrity suite validates the schema as it stood at V007. Two
things pin it there:

- `scripts/db/assertions/export-integrity-clean.sql` asserts an exact 21-table baseline.
- `scripts/db/assertions/export-integrity-current-policy.sql` records actor UUIDs that V008 gave a
  foreign key to `users`, and seeds no `users` rows.

### Success criteria

- The current-policy database applies the full migration sequence, not V001–V007.
- The clean-baseline assertion describes the current table set and does not need editing for
  reasons unrelated to export integrity when a table is added.
- Actor UUIDs used by the current-policy assertions exist in `users`.
- Every export-integrity invariant the suite asserts today still holds, and still fails for its
  own reason rather than a foreign-key violation.
- The V006-to-V007 upgrade scenario and the late-V007 rollback scenario stay at their own
  migration levels; they validate a historical transition and must not be advanced.
- `bash scripts/db/validate-migrations.sh` passes end to end.

### Non-goals

- Rewriting V006 history, or altering any applied migration.
- Adding export authority beyond `percent_complete`, `actual_start`, and `actual_finish`.
- Product or frontend feature work.

## Standing constraints

The product boundaries in `AGENTS.md` apply unchanged: Microsoft Project remains the schedule
authority, no CPM or schedule calculation, no native `.mpp` writing, no silent write-back, and
append-only audit with explicit approval and supersession semantics.

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

## Manual Microsoft Project gate

Unchanged and still pending. No automated result may be reported as a manual Microsoft Project
round-trip. The remaining human gate is for a planner to generate a synthetic MSPDI/XML artifact
and confirm that it opens in Microsoft Project, preserves task UID and ID identity, contains only
approved leaf-task values for the three authorized fields, excludes summary-task actuals, and
performs no recalculation or master-file update through Shutdown Tracker.

## Completion conditions

- `mvn test`, `npm test`, and `npm run build` pass with no skipped test standing in for a check
  that was claimed.
- Migration validation passes, or its blocker is reported precisely.
- GitHub Actions is green on the final head.
- Documentation matches the implementation.
- The handoff states what changed, what was verified, and what remains pending.
