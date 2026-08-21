# 2026-08-21 — Something that knows what ran

## Scope

Phase 0's last item: a guard against migration drift. The failure it is built for already happened —
the review deployment was found holding V001–V011 and V013, with V012 and V014 silently absent — so
this is not a hypothetical.

## What was found

**Nothing recorded anything.** Flyway is disabled on the deployment with no history table. The
deploy README's own diagnosis was "nothing knows which migrations have run", and the redeploy loop
that applied them started at V001 every time. Against a database that already has a schema that
always fails on the first file, because V001 creates enums and PostgreSQL has no
`CREATE TYPE IF NOT EXISTS` — so the migrations after it were never reached, and the deploy reported
the failure of V001 rather than the absence of V012.

**The check that should have caught it was structurally incapable of it.**
`scripts/db/validate-migrations.sh` verified a hand-written `EXPECTED_TABLES` list, and that list
named 33 tables when the migrations created 35 — missing `project_resource_links` and
`candidate_schedule_runs`. The check asks "does each name I know about exist". A list that has never
heard of a table cannot notice that it is missing.

The script's own header records that a *count* pin was removed for rotting. The name list it was
replaced with rots the same way, one step slower.

## Decisions

**A redeploy-time gate, not Flyway and not a startup assertion.**

- Flyway with `baselineOnMigrate` could not even have repaired the state that prompted this: with
  V012 absent and V013 present, no baseline version is correct — 13 buries V012 permanently and 11
  makes Flyway attempt V013 against a table that already exists. It also needs the migration files
  at a stable runtime path, and they are not deployed.
- A fail-fast startup assertion has the wrong blast radius. It turns a schema problem into a boot
  failure on a host whose healthcheck timer restarts the service every two minutes, trading a silent
  gap for a restart loop. The repository side is in any case already covered by
  `MigrationSchemaTests`, which asserts the full script list against a real server in CI.
- Blocking the deploy is the size of the failure that occurred, and it never takes the site down.

**Both expectations are derived, never transcribed.** The migration set comes from the directory
listing; the table set comes from `CREATE TABLE` in the migration files. A hand-maintained list is
the mistake being fixed, and writing a second one in a different language would repeat it.

**Two checks, because either alone can be fooled.** The ledger against the directory answers "has
every migration been applied". The tables the migrations create against the tables the database has
answers "does the schema actually look right", and does not trust the ledger to say so.

**The ledger is not created by a migration.** `infra/migrations/README.md` keeps operational data
out of migrations, and a ledger that needed a migration to exist could not record the migration that
created it.

**Applying and asserting are separate scripts.** `apply-migrations.sh` runs a file and records it;
`backfill-migration-log.sh` records without running, for a database brought forward by hand, and
marks the row `backfilled = true`. The distinction is worth keeping: a backfilled row means somebody
asserted this had been applied, an ordinary row means the script applied it. This came up during
testing — restoring a deleted ledger row with `apply` correctly failed, because re-running V014
against a database that already has its enum is a real error. Backfill is the honest tool for that,
and the failure demonstrated why there are two.

**A rewritten migration is refused.** The ledger stores each file's SHA-256. A migration is never
rewritten; the next `V###` is added instead.

**The check runs unconditionally**, with or without `--migrate`, before anything is published. The
missing migrations were missed because the only thing that would have noticed was somebody choosing
to pass a flag.

## Verified

Every behaviour was exercised, and each failure case was produced deliberately rather than reasoned
about:

| Case | Result |
| --- | --- |
| No ledger at all | Refuses, and says which script to run |
| Apply from scratch on an empty database | 14 migrations applied, check passes |
| Re-running apply | "nothing to apply", no writes |
| A migration file rewritten after being applied | Refused, both hashes printed, exit 1 |
| A migration recorded as unapplied | Named, exit 1 |
| A table the migrations create but the database lacks | Named, exit 1 |
| A table the database has that no migration creates | Named, exit 1 |
| **A redeploy against a drifted database** | **Refused after the build, before publishing; webroot mtime unchanged** |

The last one is the one that matters, and it was run against the real deployment with V014 hidden
from the ledger. It failed at the gate and the site was never touched.

The live database was then backfilled honestly — 14 rows, all `backfilled = true`, because every one
of them was applied by hand before this existed — and a clean redeploy passed the gate and
published.

| Check | Result |
| --- | --- |
| `mvn test` | **523 tests, 0 failures, 0 errors, 0 skipped** |
| `npm test` | 144 tests across four workspaces |
| `npm run build` | both applications built |
| `git diff --check` | clean |
**Not verified:** the PowerShell twin. `validate-migrations.ps1` was changed to derive its table
list
the same way, and there is no PowerShell on this host to run it. CI runs the shell script, not the
`.ps1`, so nothing else covers it either. Stated rather than implied.

`scripts/db/validate-migrations.sh` itself needs Docker and was not run here; CI runs it on this
branch, which is the first exercise of the derived list.

## Left open

- **The `.ps1` is unexercised**, above. It is a Windows convenience with no automated coverage in
  either state.
- **Nothing asserts the ledger in CI.** The new scripts are tested by having been run, not by a
  test. `MigrationSchemaTests` covers the repository side; the deployment side is covered by the
  gate refusing, which is only observed when it fires.
- **The API still starts against whatever schema it finds.** That was a deliberate decision above,
  but it means a drifted database is caught at deploy time and not at boot.
