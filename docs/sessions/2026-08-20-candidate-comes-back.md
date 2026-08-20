# 2026-08-20 — The candidate comes back

## Scope

Review the repository, then continue. The previous goal — "A front end that does what it shows" —
was complete and merged, so the session's real work was choosing the next one and starting it.

The choice was the source-versus-candidate delta and the planner decision, which
`docs/goals/ACTIVE.md` already named as next and which the root `README.md` lists as the largest
remaining item on the candidate-schedule side. This entry covers the goal contract and its first
slice: bringing the recalculated candidate back. Computing the delta, classifying each difference,
the planner decision, and the adoption record are slices 2 to 4 and are not in this change.

## What was found

`main` was clean, up to date with `origin/main`, with no open pull requests and a green GitHub
Actions run on its head. Verified locally before touching anything: `mvn test` exit 0, `npm test`
and `npm run build` exit 0 across all three workspaces.

All five slices of the previous goal were merged, and nothing in it had been recorded as not taken.

Two things were found while reading, neither of which is fixed here:

- **`critical_updates.idempotency_key` still has only a plain index**, where `problems` (V012) and
  `task_progress_updates` (V009) each have a partial unique one. Two genuinely concurrent retries
  of a queued Critical Update could still produce two rows. Unchanged from the last two sessions;
  it is narrow and belongs to its own change, and it is now recorded in `ACTIVE.md` rather than
  only in a session entry.
- **The read-before-insert idempotency in `OperationalRecordService.raiseProblem` has no catch for
  the race it does not cover.** The unique index is there, so the duplicate cannot be created, but
  a genuinely concurrent retry surfaces as a 500 rather than as the record the first capture made.
  The same shape as the point below about `DuplicateKeyException`, and the same size of change.

## Decisions

**A separate entity, not another export batch state.** `docs/product/approval-export-state-model.md`
already said this outright — candidate work "should either extend them carefully or introduce a
separate candidate-schedule run entity rather than overloading `verified`" — so it was a decision
already made rather than one to make. `verified` means a planner confirmed a generated artifact
opened in Microsoft Project as expected. It does not mean anything was recalculated, and one batch
can have more than one candidate calculation against it.

**The returned file is evidence, not a baseline, so it does not go in `source_files`.** That table
is what import batches and snapshots are built from, and a candidate that lands there is one join
away from becoming a planning baseline nobody adopted. A candidate becomes a baseline only if a
planner adopts it and imports it deliberately, which is slice 4 and a separate decision.

**A decision state is unreachable from `returned`, and the database is what enforces it.** The
handoff contract binds a planner decision to one candidate hash *and* one semantic delta. Accepting
a schedule that nothing has compared is precisely the unreviewed adoption the authority model
exists to prevent, so `returned -> accepted` raises rather than relying on a service to remember.
The transitions into the decision states have no application code behind them yet; they are in the
trigger because the invariant is permanent, not because they are reachable.

**`calculation_pending` was left out of the state type.** The documented lifecycle begins with a
pending calculation, which belongs to the planner-controlled Microsoft Project companion. Nothing
in this repository can produce that state, and declaring it would claim a capability that does not
exist. It can be added when something can reach it.

**Three hashes are recorded on the run, not resolved through the batch later.** The accepted source
hash, the artifact Shutdown Tracker handed Project, and the file that came back. A review that
cannot show all three cannot say what it compared, and reading the source hash back through the
batch chain at review time would report whatever the chain says *then*.

**The upload check reads the first element and stops.** The file must be `.xml` with a `<Project>`
root. That is a check against the wrong file, so a planner is told immediately rather than at
review time, and it claims nothing about whether the file is *this batch's* candidate — only the
delta can say that, and pretending otherwise here would be a false assurance. StAX with DTDs and
external entities disabled, because the document arrives from outside and a parser that resolves
what a document tells it to resolve is a way to read the server's filesystem.

**Hash first, then store.** The idempotent replay is a read-before-insert, like progress and
problems. Doing it in that order costs one extra read of the upload and avoids writing a second
copy of a schedule that can run to hundreds of megabytes every time a planner clicks upload twice.

**The concurrent-duplicate race throws 409 rather than resolving to the winner.** `AssignedWorkService`
catches `DuplicateKeyException` and converts it, which was the model — but resolving to the
existing row from inside the catch cannot work here: the failed statement has already aborted the
PostgreSQL transaction, so the follow-up `SELECT` would fail too. The honest behaviour is to say
what happened and let the caller retry, where the read-before-insert finds the run that won. The
loser's stored bytes are a second copy of a file the store already holds; nothing points at them,
and deleting a candidate schedule is not something this service may do on its own.

**Root-confined file handling was extracted rather than copied.** The evidence store already had
~110 lines of path confinement, streaming hash, and short-write cleanup, and the candidate store
needs exactly the same. Duplicating security-relevant path handling to keep the diff smaller is
the wrong trade, so `LocalFileStore` now backs both, with labels so a refusal still names which
store refused. `LocalEvidenceStorage` delegates and its six existing tests are unchanged, including
the ones that assert exact message text — that parity is the evidence the extraction lost nothing.

**Reading a returned candidate is gated on `RETURN_CANDIDATE_SCHEDULE`, not `VIEW_PROJECT`.** The
list of runs is ordinary operational information, gated like the export preview. The bytes are a
complete recalculated Project schedule, which is not the same kind of thing as a task list, and
whoever may return one is who may read one back.

**The audit row states the three things it is not.** `deltaComputed`, `plannerDecision` and
`masterAdopted` are all recorded as `false`. The whole difficulty in this area is that one fact
reads as another, and an audit row that only said "a candidate was returned" invites a later reader
to treat it as a review. Category `export`, not `approval`: nothing was approved.

**The servlet multipart ceiling was raised from 60MB to 210MB.** A Project plan saved as MSPDI/XML
is several times the size of the `.mpp` it came from, and the candidate store's own limit is 200MB.
Each store still enforces its own smaller limit — evidence stays at 50MB — so this raises the
container's refusal point without loosening any endpoint's rule.

## Verified

Run from the repository root, on this branch:

| Check | Result |
| --- | --- |
| `mvn test` | 476 tests, 0 failures, 0 errors, 0 skipped (409 API, 67 worker) |
| `npm test` | 119 tests across the three workspaces, all passing |
| `npm run build` | both applications built |
| `git diff --check` | clean |

Backend tests rose 456 → 476 and frontend 114 → 119.

`scripts/db/validate-migrations.sh` was **not** run as CI runs it: this host has no Docker and the
script says so rather than skipping quietly. Three things were done instead.

1. `MigrationSchemaTests` applies all fourteen migrations through Flyway on every `mvn test` run,
   which covers clean installation, and now asserts V014 and the new table by name.
2. The committed export-integrity suite — `scripts/db/validation/run-export-integrity-suite.sh` —
   was run in full against a real PostgreSQL 16.2 started from the embedded distribution, with its
   two container mount paths rewritten to the repository. Every scenario passed on the current
   schema, including the ten deterministic concurrency checks, so V014's two `ALTER TABLE`
   constraints do not disturb the export-integrity policy they attach to.
3. For the upgrade path, V001–V013 were applied to a scratch database, populated with users,
   projects, a source file, an import batch, an accepted snapshot and a policy-1 export batch, and
   then V014 applied over that populated baseline — the case a clean install cannot prove. Every
   invariant was then exercised as SQL rather than only through the service: a run naming a batch in
   another project, the same bytes twice against one batch, a malformed content hash, an unknown
   user, `returned -> accepted`, `superseded` without a successor, and a delete were each rejected;
   `returned -> delta_ready -> accepted` was accepted, with the returned file's hash unchanged
   throughout.

The Docker path itself is confirmed only by GitHub Actions.

The manual Microsoft Project gate is untouched by this slice and remains pending. Nothing here has
been tested against a schedule Microsoft Project actually saved: every fixture is a synthetic MSPDI
document written for the test. The goal adds a second human gate for slice 2 onward, because a
delta proved only against synthetic fixtures is not a delta proved.

## Corrections

Mid-session, `mvn test` reported 6 errors in `ImportReviewControllerTests`, all
`Failed to load ApplicationContext` with `ImportReviewControllerTests.class cannot be opened
because it does not exist`. That was not a defect in the change: a second Maven build had been
started against the same `target/` directory while the full suite was running, and the two
clobbered each other's test classes. Re-run alone, the suite passes. Worth knowing, because the
failure looks like a real context-configuration problem and is not.

## Left open

- **Slices 2 to 4 of this goal**, which are the point of it: the semantic delta and its
  classification, the planner decision, and the adoption record. Nothing in this slice compares two
  schedules or offers a decision, and both the console panel and the audit row say so.
- **The two idempotency items under "What was found"** — the missing unique index on
  `critical_updates`, and the unhandled `DuplicateKeyException` in problem raising. One narrow
  change covers both.
- **Sweeping orphaned candidate bytes.** A concurrent duplicate upload leaves a stored file no row
  points at. There is no eviction story for any of the local stores yet, and production object
  storage is where that belongs.
- **`generated_artifact_hash` is nullable.** It is copied from the batch at return time, and a
  batch whose artifact facts were never established leaves it null. Whether that should be a
  refusal instead depends on whether a batch can reach `generated` without a hash, which the
  policy-1 triggers make unlikely but which was not proved either way here.
