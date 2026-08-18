# Active Goal — A Front End That Does What It Shows

## Status

Active on `feat/project-evidence-list`.

Two goals are complete and merged, and are kept below as history: the green baseline that opened
this repository, and the candidate schedule Microsoft Project can open. The checks the first one
fixed are the ones every goal since has been validated by.

A separate branch, `fix/candidate-element-placement`, carries a follow-up correction to the
candidate goal. It is not part of this goal and does not gate it.

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

## Candidate schedule — completed

Merged as pull request [#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3). The
manual Microsoft Project gate below is unchanged and still pending; no automated result stands in
for it.

### Outcome

A generated candidate schedule is a schedule: the accepted Project source with the approved
execution inputs written into it, opening in Microsoft Project with its calendars, dependency
links, WBS ancestry, summary structure and resource assignments intact, and provably identical to
the source everywhere Shutdown Tracker was not authorized to write.

The artifact it replaces was a new, empty `ProjectFile` holding only the approved leaf tasks,
pruned to an element allowlist. It satisfied every authority rule by containing almost nothing,
and Microsoft Project had nothing to recalculate against.

### Success criteria

- The candidate is derived from the accepted source file, located through the existing
  `export_batches -> project_snapshots -> import_batches -> source_files` chain, and refuses to
  build if that file no longer matches the SHA-256 recorded at import.
- Authority is proved by differencing the written candidate against the source: only approved
  `(task UID, field)` pairs may differ, and the comparison must be able to see every difference it
  claims to rule out, including in repeated sibling elements and element attributes.
- An approved UID absent from the source is a hard failure rather than a task Shutdown Tracker
  creates.
- Product documents describe the artifact the code actually produces.

### Non-goals

- Delta classification and the planner adoption record. The read-only source-versus-candidate
  comparison surface and the adoption decision remain future work.
- Native `.mpp` candidate generation. Candidates require an MSPDI/XML source; `.mpp` upload,
  import and reporting are unaffected.
- Any schedule calculation by Shutdown Tracker. Recalculation stays with Microsoft Project.

### Completion conditions for this goal

- The conditions below, plus: the differencing check is covered by tests that exercise it
  directly, not only through a generated artifact.
- The manual Microsoft Project gate is stated as pending, never as passed.

## Outcome

Every surface the two applications show is a surface that works. A control that is visible either
does the thing it names, or says why it cannot — and no screen asks a person to stand in for a
capability the product does not have.

The applications are already wired to the API for import review, mapping, progress, review,
problems, handover, the export lifecycle and Critical Watch. What is left is the set of places
where a screen exists but the capability behind it does not, which the root `README.md` lists under
"Not production-complete yet".

## Slices

Ordered, one reviewed outcome per branch. Each is finished — API, both apps, tests, docs — before
the next starts.

1. **Evidence carries its file.** *(done, `feat/evidence-binary-upload`)* Registering evidence records that a file exists;
   nothing uploaded one, so the console asked a person to type where the file was kept. The record
   is now registered and the binary uploaded against it, downloadable back, with the field app
   capturing from the camera.
2. **A project-wide evidence list.** *(done, `feat/project-evidence-list`)* Evidence was readable
   per task only, so nobody could ask what evidence a shutdown has. The Evidence zone now opens on
   the project and narrows by task, bounded, and says when the list was cut.
3. **Critical Update reporting from the field app.** The console can file one; a field user or
   contractor cannot, which is the wrong way round.
4. **Offline problem raising.** Blocked on problem creation having no server-side idempotency key,
   so a queued retry could raise the same problem twice. Needs a migration.
5. **Assignment-scoped work lists.** The field app lists the snapshot's leaf tasks rather than the
   signed-in user's work, because nothing links a Microsoft Project resource to a Shutdown Tracker
   user. Needs a product decision on that link before it is code.

## Success criteria

- A capability the UI offers is reachable end to end, with the API, authorization, audit and tests
  behind it.
- A state the product can be in is a state the product shows. Evidence whose file never arrived
  reads as outstanding rather than as absent.
- No screen implies a capability that does not exist, per
  `docs/product/ux-anti-slop-rules.md` and `docs/product/frontend-visual-review-scope.md`.
- The information architecture is unchanged: five console zones, five field zones.

## Non-goals

- Production object storage. The evidence store is the provider-neutral abstraction's local
  filesystem implementation, as the architecture already specifies.
- Production authentication. The actor still arrives through a gateway-trusted header; that is its
  own goal and a larger one.
- Offline evidence capture. The progress queue holds small JSON reports; a queue of megabyte
  photos needs its own eviction and retry rules.
- Saved Operational Views, global operational Scope, and entity-linked Discussion.

## Completion conditions for this goal

- Each slice above is merged, or is explicitly recorded as not taken and why.
- `docs/product/frontend-visual-review-scope.md` and the root `README.md` describe what the
  applications actually do.

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

This gate belongs to the completed candidate-schedule goal, not to the frontend goal above. It is
recorded here because it is still outstanding, and nothing in this goal touches it either way.

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
