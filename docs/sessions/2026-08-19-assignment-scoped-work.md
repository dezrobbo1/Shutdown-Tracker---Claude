# 2026-08-19 — Which work is yours

Slice 5 of "A front end that does what it shows", and the last of that goal. Also the two open
draft pull requests, and the deployment host, which was down.

## The deployment was down, and had been for hours

`https://dez.tsenior.uk/` served its static builds and failed every API call. `systemctl` reported
`shutdown-tracker-postgres` as `active`; the postmaster was alive and every new backend died with
`could not open shared memory segment "/PostgreSQL.706405550": No such file or directory`.

The cause is systemd-logind, not PostgreSQL. `RemoveIPC` defaults to `yes`, and the database runs
as `User=dez` — uid 1000, a normal login user, not a system user. When the owner's last login
session ended, logind removed the POSIX shared memory belonging to that uid, taking the running
server's dynamic shared memory with it. `/dev/shm` was empty. **This would have recurred on every
logout.**

Fixed at the cause: `RemoveIPC=no` in `/etc/systemd/logind.conf`, which is what PostgreSQL's own
documentation prescribes for exactly this. Verified live rather than by reading the file back —
`busctl get-property org.freedesktop.login1 … RemoveIPC` now reports `false`.

A watchdog was added beside it, because the shape of this failure matters more than the instance:
the unit was `active` throughout, so nothing that looks at process state would ever have noticed.
`shutdown-tracker-healthcheck.timer` runs every two minutes and proves the database by running a
query, not by looking at a PID. If the database is dead it restarts it and then the API, to rebuild
the connection pool; if the database is healthy but the API is not, it restarts only the API. If the
database will not come back it stops and leaves it for a person rather than restarting in a loop.

These are host changes, outside the repository, and are recorded in `~/shutdown-tracker-deploy`.

## What the product decision turned out to be

Slice 5 was blocked on "a product decision on what links a Microsoft Project resource to a Shutdown
Tracker user". The decision did not need inventing. Three rules already in the repository determine
it, and each rules out an alternative:

**Explicit, never inferred.** Matching resource `J. Okafor` to user `Joseph Okafor` is a guess about
identity, and `project-operational-mapping.md` forbids activating an uncertain source identity
without review. So no name matching, and no email-in-a-custom-field matching either. Proposing a
match for a human to confirm stays permitted, and is not built.

**Relevance, not permission.** `AGENTS.md` keeps visibility, responsibility, update permission,
review permission and export authority separate, and says Project-derived membership is not
application authorization. So no authorization check reads the link. `SUBMIT_TASK_PROGRESS` and
`SUBMIT_CRITICAL_UPDATE` stay granted by role, and their two "responsibility scoping, still to come"
comments were corrected rather than acted on: narrowing them to the link would stop a supervisor
reporting for a crew that has none, and would lock somebody out of work they had been told to do
because a planner had not got to the link yet.

**Survives re-import.** The link is keyed on the project and the resource's Project UID, not on the
`imported_resources` row, which belongs to one snapshot. A snapshot that has lost the resource keeps
the link and reports it unmatched, per the rule that configuration for absent source values stays
available for history and reappearance.

Written up in `docs/product/field-identity-and-assigned-work.md`.

## Decisions worth keeping

**An empty work list is four different facts.** No accepted snapshot; nobody has linked you; your
link points at a resource this schedule has lost; you are genuinely clear. Only the last means the
reader is finished, and a field app that renders all four as "no work" tells somebody standing in a
plant to go home. `AssignedWorkView` carries enough to tell them apart — `projectSnapshotId`,
`linked`, `unmatchedResourceUids` — and eight tests hold the distinction in place. The third case is
the one that would otherwise be invisible: a re-import that renames a resource silently empties
somebody's day, and nothing would say why.

**`tasks` was kept alongside the assigned list in the field app.** My Work now shows assigned work,
but the problem and evidence pickers still list the whole schedule. Somebody who sees a fault on a
job that is not theirs must still be able to report it against that job. Scoping those pickers too
would have been a capability regression the goal never asked for.

**No general users endpoint.** The console's link form needs a person picker, and there is no HTTP
way to list users. Rather than inventing one — who may enumerate an organisation's user directory is
its own product question — `GET /resource-links/candidates` answers the narrower question the form
actually asks: who is a member of *this* project, and which resources does the accepted snapshot
carry. It is gated on `MANAGE_RESOURCE_LINK` rather than `VIEW_PROJECT`, since only somebody about
to link needs it.

**Revoking is `POST /revoke`, not `DELETE`.** Nothing is deleted; the row changes state so the trail
keeps who linked whom and who undid it. `RequestOptions` in the API client only permits GET and
POST, which surfaced the question — and the honest answer matched the surrounding endpoints, where
snapshots are accepted and problems are closed.

**The audit category is `project`, not `permission`.** Both already exist in the `audit_event_category`
enum, so this was a choice rather than a constraint. Filing it under `permission` would claim the
link grants something. A test asserts the category, because the claim is the whole design.

**One active link per resource, not per user.** A resource is one person; a person may hold several
resources, since a named resource and a trade resource can both be theirs. The unique index is
partial on `active`, so a revoked link does not block linking that resource to somebody else.

## Verified

Run on this branch, from the repository root:

| Check | Result |
| --- | --- |
| `mvn test` | 456 tests, 0 failures, 0 errors, 0 skipped |
| `npm test` | 114 tests across the three workspaces, all passing |
| `npm run build` | both applications built |
| `git diff --check` | clean |

Backend tests rose 438 → 456 and frontend 101 → 114.

`scripts/db/validate-migrations.sh` was **not** run as CI runs it: this host has no Docker and the
script says so rather than skipping quietly. Two things were done instead. `MigrationSchemaTests`
applies all thirteen migrations through Flyway on every `mvn test` run, which covers clean
installation. For the upgrade path, V001–V012 were applied to a scratch database on a real
PostgreSQL 16.2, populated with projects and users, and then V013 applied over that populated
baseline — which is the case a clean install cannot prove. Every invariant was then exercised as
SQL rather than only through the service: a second link on one resource, a blank UID, `active` with
`revoked_at` set, inactive without it, an unknown user, and an unknown project were each rejected;
one person holding two resources was accepted; and re-linking a resource after revoking it was
accepted with both rows still present. The Docker path itself is confirmed only by GitHub Actions.

Two of the eighteen new backend tests failed first time, and both were wrong expectations rather
than defects: the electrician holds T-2 as well as T-3 because the fixture assigns both crews to it,
and the two crews have equal leaf-task counts so ranking by count could not order them. The ranking
test was rebuilt around a material resource with nothing booked against it, which is the case the
ordering exists for.

The manual Microsoft Project gate is untouched by this slice and remains pending.

## Left open

- **`critical_updates.idempotency_key` still has no unique index.** Unchanged from the last session:
  the service reads before it inserts, so two genuinely concurrent retries could produce two rows.
  Narrow, and its own change.
- **Proposed match candidates.** Presenting evidence for a probable resource-to-user match, for a
  planner to confirm, is permitted by the mapping rules and is not built. Every link is typed in.
- **Crew resources standing for several people.** One resource links to one user. A crew resource
  resolving to a group needs a group model first.
- **Offline evidence capture**, for the reason the goal states.
