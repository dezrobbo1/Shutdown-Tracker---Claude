# 2026-08-25 — A job that never ran Flyway

## Scope

Review the two open pull requests — #32, the session entry for the unpatched runtime, and #33, the
field app's service worker — and answer the Codex feedback still outstanding on them. In scope:
verifying each finding against the code and correcting what was wrong. Out of scope: the two
service-worker defects standing open on #33, which want their own change.

## What was found

**#33 had no unanswered feedback.** Three Codex rounds, each already carrying a written verdict.
Two findings from the third round remain genuinely open and are recorded under "Left open" below.

**#32 had four findings across two rounds with no reply** — the second round landed six minutes
after the previous session's response comment, and the third arrived this morning. Three were right.

The one worth keeping is that **the CI job named "Migration and export-integrity validation" does
not exercise Flyway at all**. `scripts/db/validate-migrations.sh` contains no reference to it: the
container loop applies each migration with `psql --single-transaction -v ON_ERROR_STOP=1` and then
checks the resulting tables. `ci.yml` says so itself, two lines above the job — "Whether the
migrations actually apply is covered by `MigrationSchemaTests` in the backend job". So the entry's
claim that the job "confirms the Flyway 11.7.2 sequence through the containerised path" was reading
the job's *name*. Flyway runs in exactly one place in CI: `MigrationSchemaTests`, which asserts
against `flyway_schema_history` on embedded PostgreSQL. The 11.7.2 upgrade is validated on one path,
not two.

**Five booleans are not literals.** The entry closed the strict-boolean-parsing risk on "every
boolean across the seven `application*.yml` files was already a literal `true` or `false`". Four
properties in the API and one in the worker are `${SHUTDOWN_TRACKER_*_ENABLED:…}` placeholders,
where the checked-in literal is only the *fallback* and the real value comes from outside the
repository. The finding was right that the file check alone does not close it.

The deployment does close it, and not the way the finding assumed. It supplies none of those
environment variables: both systemd units pass the properties as command-line arguments with literal
`true`/`false`. So no permissive spelling — `yes`, `on`, `1` — can reach Boot 3.5's strict
converter here. That evidence lives entirely outside the repository, which is the same gap #33
recorded for the Vite `base`.

**`main` carries no branch protection.** The protection endpoint returns 404 "Branch not protected".
The entry's "none of them a quality gate" was ambiguous between "no lint/coverage/SBOM/SAST check
exists" and "nothing gates a merge"; both readings are true, and the second is now checked rather
than assumed.

## What changed

`92a67e2` on `docs/session-entry-unpatched-runtime` corrects the three claims above and records the
branch-protection evidence in the CI row. No code changed in this session.

## Decisions

**Amending the entry rather than writing a correcting one.** `docs/sessions/README.md` says not to
rewrite an entry after the fact — a later session that changes the picture gets its own entry. That
rule protects the *landed* record, and this entry has never been in it: it is still in review on
#32, and the previous session amended the same unmerged file for the same reason. A draft corrected
before it lands leaves nothing for a future reader to be misled by; a correction filed against a
claim that never reached `main` leaves two documents to reconcile.

**Recording the deployment evidence in the entry, not just the review comment.** The alternative was
to leave the boolean risk marked pending, which the finding proposed. But the check was cheap and
the answer is durable — the units are the deployment — and "pending" would have invited the next
session to redo it. What is *not* claimed is that any other deployment is safe.

**Not folding #33's two open defects into this session.** Both are about the shell's cache
lifecycle rather than about what may be cached, and the first is a redesign of the worker's cache
promotion. Bolting either onto a documentation PR would mix outcomes and put a service-worker change
behind a docs review.

## Verified

- `scripts/db/validate-migrations.sh` and `infra/docker/docker-compose.postgres.yml`: no occurrence
  of "flyway", case-insensitive. Migrations are applied by `psql`.
- `MigrationSchemaTests` queries `flyway_schema_history` and compares against
  `EmbeddedDatabase.migrationFileNames()` — Flyway does run there.
- Five `${SHUTDOWN_TRACKER_*_ENABLED:…}` properties across `services/api/src/main/resources/
  application.yml` (four) and `services/project-worker/src/main/resources/application.yml` (one).
- Both deployed systemd units pass the corresponding properties as `--…enabled=` arguments with
  literal values, and declare no `Environment=` or `EnvironmentFile=` for them.
- `main` branch protection: HTTP 404, "Branch not protected".
- CI on both pull requests: four jobs each, all passing, on runs 32812882113 (#33) and 32812899931
  (#32).
- `docs/sessions/README.md`'s index is in chronological order; the merge-order conflict the previous
  session flagged against #34 did not materialise.

**Not run:** no test suite was executed. This session changed one documentation file and added this
entry; nothing it touched is covered by a test.

## Corrections

Nothing stated earlier in this session turned out to be wrong. The corrections this session made are
to the 2026-08-23 entry and are listed above.

## Left open

- **#33, promote the document only after its assets are cached.** `networkFirst` caches a new
  document independently of the install lifecycle, so a device on release A that loads online after
  B is deployed overwrites A's cached document immediately. If B's precache then fails on a weak
  connection, A's worker stays active pointing at B's uncached bundles and the next no-signal launch
  is blank. Per-release cache names promoted only after the whole precache succeeds.
- **#33, fall back to the cached shell for every in-scope navigation.** Install precaches the base
  only and `caches.match(request)` matches the query string, so a first visit to `/mobile/?source=…`
  reloaded offline gets the 503 notice with a complete shell in the cache.
- **`base` belongs in `vite.config.ts`**, not only in the deploy script. Carried from #33 round one.
- **`cache-first` as an allowlist** over the generated shell assets rather than a catch-all.
- **A service-worker session entry** is still owed by whoever built #33; this entry does not stand
  in for it.
- **The stale test counts in `docs/goals/ACTIVE.md`.** It still names 144 frontend tests, 43 of them
  mobile-pwa, as the green-tree loss guard. #33 reports 66/167 on its branch; #34 has merged since
  the number was written. The suite was not run this session, so the current figure is unknown here
  — it wants one update once #33 lands, not competing ones from each open branch.
- **The 25 research sources** behind the 2026-08-23 entry are still unrecorded, and
  `docs/research/source-quality-register.md` has no entries for them. Raised on #32 and left as a
  call about what the repository should hold.
