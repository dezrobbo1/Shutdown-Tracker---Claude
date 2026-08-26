# 2026-08-26 — One person, and one rule

## Scope

Make the console round-trip trial actually be driven by one person. In scope: the capability model,
the review identity seeder, and the console's acting identity. Out of scope: clearing the trial back
to a blank slate, and letting the super user define roles of their own — both asked for in the same
breath, both still to do.

## What was found

**"Admin" was not a super user.** It held fifteen of twenty-four capabilities. The nine it lacked
were not an oversight from a session that forgot them; they were the capabilities the permission
matrix deliberately puts elsewhere — mapping configuration, problems, actions, evidence, handover,
Critical Watch, task lineage.

**The console could still be somebody else.** Two selectors have stood in that sidebar. The first
changed the role header, which the server ignores. The second — added when the first was found
misleading — changed the whole identity and remembered it in `localStorage`. That is what was
actually reported: a console that "still has users with different permissions". A browser that had
once chosen "Review Planner" kept acting as one through every redeploy, because a stored identity
replaced the build-time actor wholesale and nothing ever expired it.

**Four other people existed, active and selectable.** The seeder created a field user, a supervisor,
a planner and a viewer so the four-eyes journey could be walked by four people. The trial is
deliberately the opposite.

## What changed

`admin` holds every capability, by one rule — `Capability.SUPER_USER` on the server,
`superUserRole` in the client — and not by admin being appended to twenty-four grant lists. The
grants added for the trial last session were **reverted**: the declared matrix now says what the
permission matrix says again, and the relaxation is exactly one line wide on each side.

The seeder creates the administrator and retires the other four: membership revoked, account
deactivated. Both, and in that order — a deactivated account holding a live membership still shows
up wherever memberships are listed, as somebody who looks entitled and is refused.

The console's selector is gone, and so is the stored identity behind it. The build decides who the
console is, and nothing in the browser can disagree.

`redeploy.sh`, which is not in this repository, now builds **both** entrypoints as the super user.
It previously required a seeded `field_user` and would have refused to deploy this branch at all.

## Decisions

**One rule, not twenty-four grants.** Writing `admin` into every capability list would have been
indistinguishable from twenty-four considered decisions, would have silently omitted the super user
from the next capability somebody adds, and would have made ending the trial an edit a reviewer has
to check is complete. It also destroys the permission matrix as a record of who owns what — which
is the thing that has to come back when the trial ends.

**Retired, not deleted.** The ask was to remove the other users. Thirty-six columns across the
schema reference `users`, so deleting them either cascades through the audit trail or fails on a
foreign key. Retirement is what a real deployment does with somebody who has left, and it produces
the outcome asked for: they cannot act, cannot be selected, and do not appear. What they did stays
attributable. **They are still rows in the database** — that is the honest description.

**Matched on the addresses the seeder gave them.** Retirement touches only accounts at
`<role>@review.invalid`, the ones this seeder created. A real planner who happens to hold the
planner role is not its business, and there is a test that says so.

**The field app is built as the super user too, rather than left broken.** It is parked for the
trial, but the field user it was built as no longer exists. Building it as the one person who does
exist keeps it openable. A device holding a stored identity for a retired account will be refused
until it re-selects — the mobile switcher still exists, and now offers exactly one person.

**The console's role is still cosmetic to the server.** Worth restating because it is the source of
every wrong conclusion in this area: `ProjectAuthorizationService` resolves the role from
`project_memberships` and ignores the header. The bundle's role decides only what the interface
offers.

## Verified

- `mvn test` — **541 passing**, 466 in `services/api` and 75 in `services/project-worker`.
  Three database tests were replaced rather than deleted: the journey they asserted was walked by
  four seeded people, which no longer exists. What replaced them asserts the outcomes that do — the
  super user is allowed every capability against a real membership, the four retired identities are
  refused by the *server* rather than merely hidden by the console, and a real viewer gained nothing.
- `npm test` — **183 passing**: 80 console, 72 mobile-pwa, 31 api-client.
- `npm run build` — both applications, clean.
- `git diff --check` — clean.

**Could not run:** `scripts/db/validate-migrations.sh` needs Docker and this machine has none. No
migration was added; the two new repository methods are `UPDATE`s against existing columns.

**Not verified through a browser.** Nothing in this entry was driven on the deployment. The
retirement path in particular has only ever run against embedded PostgreSQL — on the live database
it will meet four accounts that have existed for days, and the console bundle is rebuilt from
whatever the seeder leaves. That is the next thing to do, before the trial itself.

## Corrections

The `// trial:` comments added to nine capability grants on 2026-08-25 are removed, not because they
were wrong but because the grants they annotated are gone. Anyone reading that session's entry will
find it describes grants this one reverted; the relaxation it describes still holds, by a different
mechanism.
