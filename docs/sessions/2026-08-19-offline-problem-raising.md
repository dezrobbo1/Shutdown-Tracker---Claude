# 2026-08-19 — Raising a problem where there is no signal

## Scope

Slice 4 of the frontend goal: offline problem raising. The slice was blocked in the plan rather
than merely unstarted — `docs/goals/ACTIVE.md` recorded it as needing a migration first — so this
session covers the schema change as well as the API and field-app work.

Out of scope: the three earlier slices, which were already finished and green on their own
branches, and slice 5, which needs a product decision before it is code.

## What was found

### The block was real, and was one column

`task_progress_updates` (V009) and `critical_updates` (V006) both carry an `idempotency_key`, and
both services return the record an earlier submission created when a key repeats. That pairing is
the entire reason the offline queue is safe to retry. `problems` (V010) had no such column and
`ProblemCreateRequest` had no such field, so the queue could not hold a problem without risking a
second one on every retried send.

The two existing keys are not enforced identically. V009 has a partial unique index on
`(project_id, idempotency_key)`; V006's `idx_critical_updates_idempotency_key` is a plain index,
so the Critical Update service's read-then-insert has no database backstop under concurrency.
V009's is the stronger pattern and is the one V012 follows. V006 was left alone: it is a separate
defect on a different table, and widening this slice to fix it would have put an unrelated schema
change in a branch about problems.

### What the field app did instead

`ProblemsScreen` called `client.problems.raise` directly, and `describeRaiseFailure` existed to
say the honest thing about the consequence: *"Could not send, and this is not saved on this
device. Raise it again when you have a connection."* That message was accurate and was the
clearest statement in the repository of why this slice existed.

The queue itself needed almost nothing. Slice 3 had already generalised it from progress-only to
kind-carrying, so a third kind is a variant on `QueuedSubmission`, one `enqueue` wrapper, and one
more branch where the hook picks an endpoint.

## What changed

`V012__problem_offline_capture.sql` adds `idempotency_key` and `offline_local_id` to `problems`,
with a partial unique index on `(project_id, idempotency_key)`. `OperationalRecordService`
returns the problem an earlier capture raised when a key repeats, and does not audit the replay —
the problem was raised once. The field app raises through the queue rather than the client, and
the Problems screen lists what the device is still holding above the project's open problems, with
its sync state, so a problem raised with no signal stays visible on the screen that raised it.

## Decisions

**The key is scoped to the project, not global.** It is generated on a device and only has to be
unique there; pairing it with the project makes a collision between two devices impossible without
requiring devices to coordinate. This matches V009. A test asserts the same key on two projects
raises two problems.

**A blank key is not a key.** `ProblemCreateRequest` nulls a blank `idempotencyKey`, as
`CriticalUpdateSubmitRequest` already does. Without it, a client sending `""` twice would be
rejected by the partial unique index rather than recognised as sending nothing.

**`ProblemRecord` does not expose the key.** `CriticalUpdateRecord` does not expose its own, and
the queue only needs the server id to mark an item synced. Adding a field to a response that
nothing reads would be inventing a contract.

**`describeRaiseFailure` was kept, not deleted.** Its old premise — a send that failed — can no
longer happen, because sending is now Sync's business. What can still happen is the device failing
to store the capture, and then nothing holds the problem at all. That is worth a distinct message,
so the function now says the device could not keep it. Deleting it would have left a genuine
failure silent.

**Unsent problems are listed separately, not merged into the open-problems list.** They are shown
first, with a sync chip, above the server's list. Merging them would imply the project already
holds a record nobody else can see yet.

**V006's non-unique index was left as it is.** See above.

## Verified

Run on this branch, from the repository root:

| Check | Result |
| --- | --- |
| `mvn test` | 432 tests, 0 failures, 0 errors, 0 skipped |
| `npm test` | 101 tests across the three workspaces, all passing |
| `npm run build` | both applications built |
| `git diff --check` | clean |

Six of the new backend tests cover the key directly: a retry returning the first problem, the
replay not being audited twice, the same key on another project raising its own problem, keyless
problems never colliding, a blank key being treated as absent, and the index refusing a second row
under one key even if the service's check were bypassed.

`scripts/db/validate-migrations.sh` was **not** run as CI runs it — this machine has no Docker,
and the script says so rather than skipping quietly. The suite it exists to run was executed
directly against a local PostgreSQL 16.2 instead, with the two container mount paths rewritten to
the repository, and reported *"PostgreSQL export-integrity validation passed"* with all ten
concurrency scenarios and the late-V007 rollback scenario included, against the full V001–V012
sequence. `MigrationSchemaTests` also applies all twelve migrations through Flyway on every
`mvn test` run. The Docker path itself is confirmed only by GitHub Actions.

The manual Microsoft Project gate is untouched by this slice and remains pending.

## Left open

- **Slice 5, assignment-scoped work lists.** Unchanged and still the last slice of this goal. It
  needs a product decision on what links a Microsoft Project resource to a Shutdown Tracker user
  before any of it is code.
- **`critical_updates.idempotency_key` has no unique index.** The service reads before it inserts,
  so two genuinely concurrent retries of one Critical Update could produce two rows. Narrow, and
  its own change: an index plus a migration, matching what V009 and V012 do.
- **Offline evidence capture** remains out of scope for this goal, for the reason the goal states:
  a queue of megabyte photos needs its own eviction and retry rules.
