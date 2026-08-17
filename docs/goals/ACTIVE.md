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
  since V008. Statements that attempt to overwrite an established actor name a second real user
  rather than a random UUID; both are rejected, since the immutability rules are BEFORE-row
  triggers and run ahead of the foreign key's AFTER-row trigger, but naming a real user keeps the
  fixture saying what it means.
- `scripts/db/validate-migrations.sh` and `.ps1` no longer require the migration set to be exactly
  V001–V007. That guard rejected the repository on its first line as soon as V008 was added, so
  the job never reached the validation it exists to run.
- The committed PostgreSQL export-integrity suite now validates the current schema. Its
  current-policy database applies the full migration sequence rather than stopping at V007:
  `export-integrity-clean.sql` checks that the tables the export policy depends on are present
  instead of asserting an exact 21-table schema that any unrelated migration would break, and
  `export-integrity-current-policy.sql` seeds the `users` rows its lifecycle actors have needed
  since V008. The V006-to-V007 upgrade and late-failure scenarios stay at their own migration
  levels, because advancing them would stop them validating the historical transition they exist
  for.

## Next

No specific engineering goal is currently active. `main` has a green baseline; the next goal
should be chosen from the "Not production-complete yet" list in the root `README.md`. The
largest open items are production authentication — authorization is enforced, but the actor
still arrives through a gateway-trusted header rather than a validated token — evidence binary
upload, Critical Update reporting from the field app, and offline problem raising.

Record the chosen goal here before starting it, with its outcome, success criteria, non-goals,
required validation, and completion conditions.

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
