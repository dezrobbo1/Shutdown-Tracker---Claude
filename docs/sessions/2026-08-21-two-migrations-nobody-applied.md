# 2026-08-21 — Two migrations nobody applied

## Scope

Asked to review what has been done and what needs doing next. The review covered the repository,
the review deployment, and the two competing orderings for "next". This entry covers the review and
the goal-document change that records its conclusion. No application code changed.

## What was found

**The repository is complete and green.** Twelve pull requests, all merged; no open pull request and
none ever closed unmerged; every branch merged; working tree clean. `main` is green at `2e10abb`.
The last recorded run was 480 backend tests with 0 skipped and 122 frontend tests. There is no
`TODO`, `FIXME` or `@Disabled` anywhere in the source — unfinished work in this repository lives in
`docs/goals/ACTIVE.md` and these entries, not in the code.

**Nothing had been done since the previous session ended.** That session finished at 08:14 on
2026-08-20; pull request #12 merged at 08:31; a plan for making the product walkable was written at
08:34 and never started. Every finding in it still held.

**The deployment's schema is missing two migrations, and one was skipped out of order.** The applied
set is V001–V011 plus V013. Neither `candidate_schedule_runs` (V014) nor V012's two columns on
`problems` are present, while `project_resource_links` (V013) is — so V012 was jumped over and V013
applied on top of the gap:

```text
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'problems'
   AND column_name IN ('idempotency_key', 'offline_local_id');   -- 0 rows

SELECT to_regclass('public.project_resource_links')  IS NOT NULL;  -- t
SELECT to_regclass('public.candidate_schedule_runs') IS NOT NULL;  -- f
```

**Offline problem raising would therefore fail on the deployment.** It was merged four sessions ago
and has never been exercised there — `problems` has 0 rows, so a write carrying an idempotency key
has never hit the column that does not exist.

**The check that should have caught this cannot.** `scripts/db/validate-migrations.sh` verifies a
hardcoded `EXPECTED_TABLES` list, and that list has 33 entries naming neither
`project_resource_links` nor `candidate_schedule_runs`. It is a per-name `to_regclass` loop, so it
is structurally incapable of noticing a table it was never told about.
`infra/migrations/README.md` compounds it by describing the list as "all 21 expected tables" —
wrong twice over. The header comment on that script records that a *count* pin was removed for
rotting; the name list it was replaced with rots the same way, one step slower.

**The deployment's `--migrate` cannot be used to repair it.** The loop applies every `V*.sql` from
V001 with `ON_ERROR_STOP=1` and exits on the first error. V001 creates its enums without
`IF NOT EXISTS` — PostgreSQL has no such form for `CREATE TYPE` — so the first file always fails
against an existing database and the loop never reaches V012 or V014. The script's own comment says
this is deliberate. It fails safe, aborting before anything is published, but it means the repair
must be done by hand.

**Two storage roots are unset**, so evidence upload and candidate return are both unusable. The
service configures the source-file and export-artifact roots but not `evidence-storage.local-root`
or `candidate-schedule-storage.local-root`, which default to *relative* paths and resolve under a
working directory the service user cannot write to. `LocalFileStore` does not create its root at
construction — it calls `createDirectories` on first upload — so this fails at request time, not at
boot. That is why a healthy liveness check has been hiding it, and why `evidence` has 0 rows.

**One identity is baked into both applications.** The deployment holds one user, one membership
(`planner`), one project, and the build compiles that same actor into the console *and* the field
bundle. Changing only the role variable would fix nothing: authorization resolves the role from
`project_memberships` for that user id and ignores the header, so the field application needs a
genuinely different seeded user, not a different label on the same one.

## Decisions

**Phase 0 is taken before slice 2.** The goal document said the journey test was next. It is
deferred, and the reason is written into the document: a test proves the chain to CI, not to a
person, and this goal's own completion conditions require that the journey has been walked by a
person end to end. Hand-walking had already found four defects — the two storage roots, no import
path through the interface, no artifact download, and an inert fixture — that no test would have
surfaced. Slice 2 remains wanted and unchanged as the safety net before Phase 2 changes roles
underneath it.

**Rejected: repairing the schema with Flyway `baselineOnMigrate`.** With V012 absent and V013
present, no baseline version is correct — baselining at 13 buries V012 permanently, and baselining
at 11 makes Flyway attempt V013 against a table that already exists. It also needs the migration
files at a stable runtime path, and they are not deployed.

**Rejected: a fail-fast schema assertion at API startup.** `flyway.validate()` would be the
non-drifting form of it, but it converts a schema problem into a boot failure on a host whose
watchdog restarts the service unattended — trading a silent gap for a restart loop. The repository
side is in any case already covered by `MigrationSchemaTests`, which asserts the full script list
against a real server in CI. The gap is deployment-side only.

**Preferred: a redeploy-time gate over a ledger.** Blocking the deploy is the size of the failure
that occurred, and it never takes the site down. It also forces the durable half — the deployment's
own README diagnoses the root cause as "nothing knows which migrations have run", and a check needs
something to check against. The expected set must be derived from the migrations directory, never
transcribed; a hand-maintained list is the mistake being fixed.

**Rejected: hand-listing MSPDI element order for a new fixture.** `MspdiTaskElementOrder` derives
the order by reflecting over MPXJ's own binding precisely so a transcribed copy cannot go silently
wrong. A future fixture should be asserted against that same authority rather than generated,
because generating it would make review theatre of a document the fixture policy requires a human
to review.

## Verified

Read-only inspection only. Nothing was deployed, restarted, migrated or written to any database.

| Check | Result |
| --- | --- |
| `git status --porcelain -uall` | empty; no stashes |
| `git fetch origin`, `git pull --ff-only` | `main` advanced `4b7968b` → `2e10abb` |
| `git branch --no-merged main` | empty — all twelve branches merged |
| `git branch -d` × 12, `git push origin --delete` × 12 | all succeeded; only `main` remains |
| `git diff --check` | clean |
| Live schema queries | as quoted above |

**Not run, and not claimed:** `mvn test`, `npm test`, `npm run build`, and
`scripts/db/validate-migrations.sh`. This change is documentation only and touches no code path any
of them cover; they are required before the Phase 0 slices that do.

## Corrections

**Stated during the session that V014 alone was missing from the deployment.** V012 is missing too,
and its absence is the more consequential of the two because a merged feature depends on it. The
applied set is V001–V011 plus V013.

**The plan approved on 2026-08-20 said to redeploy with `--migrate`.** That would fail on V001 and
never reach the migrations it was meant to apply. The repair is manual, per file, in a single
transaction each.

## Left open

Everything in Phase 0, in order. Specifically, and precisely enough to act on:

- **V012 and V014 to apply by hand**, each with `--single-transaction`; neither contains
  `CREATE INDEX CONCURRENTLY`, so both are transaction-safe. Take a dump first.
- **The two storage roots to set**, their directories to create owned by the service user, and the
  unit reloaded.
- **A second seeded identity before the build actors can be split**, because the role label alone
  changes nothing. The deployment build should look identities up by role rather than hold their
  ids, so it cannot drift from whatever seeds them.
- **`EXPECTED_TABLES` and the "21 expected tables" claim**, both stale, both to be replaced with a
  derived set rather than corrected in place.
- **The four items already recorded as deferred** in `docs/goals/ACTIVE.md` are unchanged:
  supervisor review on the field app, `critical_updates.idempotency_key`'s non-unique index — now
  unchanged for five sessions — the paused candidate-schedule delta, and the manual Microsoft
  Project gate.
