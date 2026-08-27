# 2026-08-27 — A button that empties a database

## Scope

The second of the three things asked for in one breath: clear the deployment back to a blank slate
so the round-trip trial can be walked again from nothing. The first — one super user — merged as
[#38](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/38) earlier the same day. The
third, letting the super user define roles, is still not started.

## What the schema had to say about it

**Deleting was never going to work.** Five tables in the export and candidate chain carry
`BEFORE DELETE` triggers that raise — export candidate records, approval records, export batches,
export batch lines, candidate schedule runs. They are append-only by design and by trigger. The
useful detail is that those triggers are *row-level*, and `TRUNCATE` fires only statement-level
triggers, so truncate is not a shortcut past a safety rail: it is the only mechanism that works at
all. A test now pins that, by asserting the delete still raises before asserting the reset succeeds.

**Cascade was the tempting mistake.** The test helper in `src/test` truncates with `CASCADE`, and
copying it would have been the obvious move. Its correctness condition is "wipes everything,
including `users`" — the exact opposite of what production needs. Worse, cascade truncates every
table that *references* one in the list, so the day a migration adds a foreign key from a kept table
into a wiped one, the blast radius widens and nothing says so. Without cascade, PostgreSQL refuses
the statement and names the table. The reset fails closed.

**Two tables were not where they looked.** `reporting_policy_versions` reads like planner
configuration and carries a foreign key to `critical_work_packages`, which is wiped; kept, the
un-cascaded truncate would refuse and the whole reset would fail. That was found by querying
`pg_constraint` rather than by reading migrations, and the query is now a test: no kept table may
reference a wiped one. `project_resource_links` went the other way — kept at first, then wiped,
because the resource ids it holds name a schedule that is about to stop existing.

The lists are written out by hand rather than derived. A derived list is always correct and always
silent; a written list plus the test is wrong loudly, and the next migration fails until somebody
decides which side it belongs on.

## What guards it

Four things, and they are independent on purpose because each one alone would be regrettable. The
bean is conditional on a flag of its own, so a deployment that did not ask for a reset button does
not have the route. The capability is admin-only and resolved from stored membership. The project
must carry the synthetic marker the bootstrap seeder writes, which is the one that actually matters:
even with the flags on and an administrator acting, real project data is unreachable. And the
project's name must be typed back, verified on the server rather than only in the browser.

A dedicated flag rather than reusing the review-identity one. Listing which synthetic people exist
and destroying a trial in progress are not the same power, and one switch controlling both would
mean turning on the harmless one to get a diagnostic and silently arming the other.

## The audit trail is wiped, and that needs saying plainly

AGENTS.md asks that append-only history be preserved. This deletes all of it. That is defensible
here on three conditions, all of which must hold together: the project carries `synthetic: true` and
`allowed_use: review_bootstrap_only`, so no real history is reachable; the feature is behind a flag
that is off by default, tested to be off by default, so a production deployment does not have the
route; and the reset writes its own record as the first row of the trail it created, so the wipe is
itself audited. **If any one of those three stops being true, this stops being defensible.**

## Ordering that is not arbitrary

Files are deleted after the database commits, not before. Files first would mean a failed
transaction leaves rows pointing at bytes that are gone — an actively broken state that reads as
corruption. Database first leaves bytes nothing points at, which is wasted disk and nothing else.
The audit row is written after the truncate for the same class of reason: written before, the
statement that follows would delete it.

Two things the reset cannot reach, both reported rather than hidden. The project worker writes into
two of the same storage roots through its own configuration, and a database lock timeout does not
reach the filesystem — a parse in flight can still be writing while a directory is cleared. And a
field device holding queued reports for tasks that no longer exist will fail to send them; that
needs site data cleared on the handset.

## Still not done

The super user defining roles and responsibilities. And the trial itself: nothing has yet been
imported, tracked, reviewed, exported and returned through the interface. Exports › Mapping became
reachable only this morning and has never been walked.
