# 2026-08-17 — Fresh repository review and green baseline

## Scope

Review the `claude-branch` of `dezrobbo1/Shutdown-Tracker` and continue that work here, in the
fresh repository. In practice that meant establishing whether this repository's committed checks
actually pass, and fixing them where they did not. No product or feature work.

## What was found

### This repository is `claude-branch` minus one archive

The tree differs from `dezrobbo1/Shutdown-Tracker@claude-branch` by exactly two files: the
`docs/source-material/archive/` README and its 1.9 MB zip. Nothing else. 213 commits here against
216 there.

### CI had never passed

The first and only run on `main` failed two of four jobs. Both failures were invisible from a
developer machine, for different reasons.

**Backend Maven test.** `ExportIntegrityPostgresIntegrationTests` started its own Docker container
and skipped itself when no daemon was reachable. Locally that reported 399 tests green with 3
skipped — and those 3 were the entire export-integrity guarantee set. In CI, where Docker is
present, they ran and errored:

```text
ExportIntegrityPostgresIntegrationTests.candidateApprovalAuditsUseStateSpecificImmutableEventTypes
  » ResponseStatus 409 CONFLICT "Candidate creation requires an accepted snapshot,
    a matching imported task, and immutable source provenance."
```

The cause was the fixture, not the code under test. V008 gave every export-lifecycle
`*_by_user_id` column a foreign key to `users`; the fixture seeds no `users` rows, so candidate
creation was rejected before any assertion ran. The commit that made this suite "green so it is
worth reading" had made it green by not running it.

**Migration and export-integrity validation.** `validate-migrations.sh` and its PowerShell twin
asserted the migration set was exactly V001–V007. With V008–V011 present, that failed on the
script's first few lines; the job spent six seconds refusing to run the validation it exists to
run. The expected-table lists inside the same scripts had already been extended for V008–V011 —
only the count guard was left behind.

**Underneath both.** Once the migration job ran again, the suite it calls turned out to be
validating the schema as it stood at V007, four migrations behind the application.

### The active goal pointed at another repository

`docs/goals/ACTIVE.md` described a final review of PR #48 in `dezrobbo1/Shutdown-Tracker` — work
already merged into this history — and referenced a `frontend/rebuild-review-shell-ia` worktree
that does not exist here. `AGENTS.md` instructs every agent to treat that file as the current task
contract.

## What changed

Pull request [#1](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/1), four commits.

The integration test moved to the embedded PostgreSQL server every other database test already
uses, and its fixture seeds the actors the schema has required since V008. The migration-script
guards stopped pinning a fixed migration count. The export-integrity suite's current-policy
database moved onto the full migration sequence. `ACTIVE.md` was rewritten for this repository.

Pull request [#2](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/2) then added this
folder, later in the same session, and this entry with it.

## Decisions

**Embedded PostgreSQL rather than Docker for the integration test.** The repository already runs
100+ database tests against a zonky embedded distribution with no Docker, and documents that as
deliberate. The export-integrity suite was the one class that did not. Making it conditional on
Docker was the original mistake: a test that skips itself where a tool is missing reports green
without running. Moving it to the shared server removes the failure mode rather than making the
skip more visible.

It still uses `@SpringBootTest` with `spring.datasource.*` pointed at that server, rather than
being handed the shared `DataSource` — the class exists to exercise the real pool, transaction
proxy, and repository beans, and the `AopUtils.isAopProxy` and `JdbcExportPreviewRepository`
assertions depend on that.

**Rejected: seeding users only, and leaving the Docker gate in place.** That would have fixed the
CI failure while leaving the suite unrunnable on a developer machine, which is how the fixture got
four migrations out of date without anyone noticing.

**Fixing the export-integrity suite's schema coverage rather than documenting the gap.** The first
attempt scoped the suite to V001–V007 explicitly and recorded the gap as the next goal. That was
the right *shape* — the stale count guard was replaced with a stated reason — but once a local
PostgreSQL was available it became cheap to do properly, and a validation suite four migrations
behind the application is not worth much. Both blockers turned out to be small:

- `export-integrity-clean.sql` asserted an exact 21-table schema. It now checks that the tables the
  export policy depends on are *present*. An exact match failed on any unrelated migration adding a
  table, which says nothing about export integrity and stopped the rest of the file running.
  Whether the full schema is the expected one is already checked by `validate-migrations.sh`.
- `export-integrity-current-policy.sql` now seeds the eight `users` rows its lifecycle actors need.

**Rejected: advancing the upgrade and late-failure scenarios too.** Those validate the historical
V006→V007 transition. Moving them forward would stop them validating it. They stay at their own
migration levels, and the script now says why.

## Verified

Linux, Java 21.0.12, Node 22, PostgreSQL 16.2.

| Check | Result |
| --- | --- |
| `mvn test` | 399 tests, 0 failures, 0 errors, **0 skipped** — was 3 skipped locally, 3 errors in CI |
| `npm test` | 84 passed across the three workspaces |
| `npm run build` | both apps built |
| `git diff --check` | clean |
| Export-integrity suite | passes with the current-policy database on V001–V011, all ten concurrency scenarios included |
| GitHub Actions on the branch head | all four jobs green |

`bash scripts/db/validate-migrations.sh` was **not** run locally — it needs Docker, which this
machine does not have. The suite it calls was run directly against a real PostgreSQL 16.2 server
instead, and the Docker path itself is covered by the pull request's own CI run.

A negative check confirmed the assertions still bite: removing one seeded actor and re-running
showed the immutability rules, not the foreign key, rejecting the statement.

## Corrections

The first commit's message claims that overwriting an established actor with a random UUID would
have left an assertion passing for the wrong reason. **That is wrong.** The immutability rules are
BEFORE-row triggers and run ahead of the foreign key's AFTER-row trigger, so a dangling UUID is
still rejected with the immutability rule's own SQLSTATE. Confirmed directly against PostgreSQL
16.2 with a minimal reproduction.

Naming a real user is still worth doing — it keeps the fixture saying what it means, and keeps the
assertions independent of constraint evaluation order — but that is the reason, not the one
originally given. Corrected in the later commit, in the code comments, and in the pull request
body. The commit message itself was left unamended, per the repository's history rules.

## Left open

- Pull request #1 is a **draft** and unmerged, per `AGENTS.md`.
- `docs/goals/ACTIVE.md` has no goal selected. The largest open item from the root `README.md` is
  production authentication: authorization is enforced, but the actor still arrives through a
  gateway-trusted header rather than a validated token.
- The manual Microsoft Project round-trip gate remains pending and was not touched.
- CI emits deprecation warnings for `actions/setup-java@v4` and Node 20 based actions. Not acted
  on; no job fails because of them yet.
