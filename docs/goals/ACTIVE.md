# Active Goal — A Candidate Schedule Microsoft Project Can Open

## Status

Merged. Pull request [#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3) landed on
`main` as `2e55d54`, so the outcome below is on `main` and its automated completion conditions are
met. The manual Microsoft Project gate is **not**, and is stated as pending throughout.

`fix/candidate-element-placement` carries a follow-up correction to the same mechanism: an approved
field the source did not carry was placed by an element whose schema position MPXJ's binding does
not know, which could put it out of sequence. It changes nothing about the outcome or its criteria.

No new goal has been chosen. `README.md`'s "Not production-complete yet" list is the menu, and the
"Next after this goal" section below names the largest items on it.

The green-baseline goal that opened this repository is complete and merged; it is kept below as
history, because the checks it fixed are the ones this goal is validated by.

This repository is a fresh start. Its history carries the work previously developed on the
`claude-branch` of `dezrobbo1/Shutdown-Tracker`, minus the source-material archive. No external
gates were inherited from that repository, and nothing open there is live: the active goal it
carried described a final review of PR #48, work already merged into this history.

## Green baseline — completed

### Outcome

`main` is trustworthy: every committed check passes on a developer machine and in GitHub Actions,
each check proves what it claims to prove, and the documented state of the product matches what
the code actually does.

A check that passes locally because it quietly skipped itself is worse than no check. Prefer a
check that runs everywhere over a check that runs only where an optional tool happens to be
installed.

### Completed

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

## Outcome

A generated candidate schedule is a schedule: the accepted Project source with the approved
execution inputs written into it, opening in Microsoft Project with its calendars, dependency
links, WBS ancestry, summary structure and resource assignments intact, and provably identical to
the source everywhere Shutdown Tracker was not authorized to write.

The artifact it replaces was a new, empty `ProjectFile` holding only the approved leaf tasks,
pruned to an element allowlist. It satisfied every authority rule by containing almost nothing,
and Microsoft Project had nothing to recalculate against.

## Success criteria

- The candidate is derived from the accepted source file, located through the existing
  `export_batches -> project_snapshots -> import_batches -> source_files` chain, and refuses to
  build if that file no longer matches the SHA-256 recorded at import.
- Authority is proved by differencing the written candidate against the source: only approved
  `(task UID, field)` pairs may differ, and the comparison must be able to see every difference it
  claims to rule out, including in repeated sibling elements and element attributes.
- An approved UID absent from the source is a hard failure rather than a task Shutdown Tracker
  creates.
- Product documents describe the artifact the code actually produces.

## Non-goals

- Delta classification and the planner adoption record. The read-only source-versus-candidate
  comparison surface and the adoption decision remain future work.
- Native `.mpp` candidate generation. Candidates require an MSPDI/XML source; `.mpp` upload,
  import and reporting are unaffected.
- Any schedule calculation by Shutdown Tracker. Recalculation stays with Microsoft Project.

## Completion conditions for this goal

- The conditions below, plus: the differencing check is covered by tests that exercise it
  directly, not only through a generated artifact.
- The manual Microsoft Project gate is stated as pending, never as passed.

## Next after this goal

The next goal should be chosen from the "Not production-complete yet" list in the root
`README.md`. The largest open items are production authentication — authorization is enforced, but
the actor still arrives through a gateway-trusted header rather than a validated token — evidence
binary upload, Critical Update reporting from the field app, and offline problem raising.

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
round-trip. The remaining human gate is for a planner to generate a synthetic MSPDI/XML candidate
schedule and confirm that it opens in Microsoft Project as a complete schedule, preserves task UID
and ID identity along with the source's summary structure, WBS ancestry, calendars and dependency
links, differs from the accepted source only in the approved leaf-task values for the three
authorized fields, excludes summary-task actuals authored by Shutdown Tracker, leaves the accepted
source file unchanged, and performs no master-file update through Shutdown Tracker.

Microsoft Project recalculating dependent values in the candidate is expected and is not a failure.

## Completion conditions

- `mvn test`, `npm test`, and `npm run build` pass with no skipped test standing in for a check
  that was claimed.
- Migration validation passes, or its blocker is reported precisely.
- GitHub Actions is green on the final head.
- Documentation matches the implementation.
- The handoff states what changed, what was verified, and what remains pending.
