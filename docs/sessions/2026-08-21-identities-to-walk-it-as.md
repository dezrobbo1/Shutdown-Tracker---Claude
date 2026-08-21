# 2026-08-21 — Identities to walk it as

## Scope

Phase 0's identity slice, which is Phase 1 slice 4 taken early. Seed one synthetic person per
journey role, list them, and let both applications act as one. In scope: the seeder, the listing
endpoint, and the pickers that replace the console's role selector. Out of scope: seeding any work,
and any way to create a user or membership over HTTP.

## What was found

**Three roles are a complete cover.** Working `Capability` against the client capability map, every
one of the twenty-four capabilities is held by at least one of planner, supervisor and field user.
None is exclusive to admin, coordinator, shutdown control, contractor or inspector. That is the
goal's "three roles rather than nine" arrived at from the code rather than assumed, and it is why
the seeder creates the people it does.

**The two writes the seeder needs could not carry a marker.** `users.metadata` and
`project_memberships.metadata` have existed since V008 as `JSONB NOT NULL DEFAULT '{}'` with an
object check constraint, but `UserCreateRequest` had no metadata component and neither INSERT wrote
the column. The strategy doc requires every seeded row to be traceable to a dataset, so the marker
is not optional and the repository had to be extended. Both methods were called from tests only, so
the change is additive.

**The console's role selector could not do what it appeared to do.** `changeRole` rewrote the role
header and re-derived nineteen capability flags, but `ProjectAuthorizationService` resolves the role
from the caller's `project_memberships` row and never reads the header. With one identity and one
membership configured, the selector had exactly two possible effects: un-grey a control the server
would refuse, or grey out one it would have allowed. Its own panel copy — "Selecting a role here
only changes what this interface offers" — was literally true, and was the problem.

**The field app's offline queue makes identity switching hazardous.** `useFieldQueue` memoises the
queue on the API client, which is rebuilt from the session. Switching identity with reports still
pending would flush work captured by one person under another's actor header — a misattribution,
and for a role that may not submit, a refusal whose message would say nothing about the real cause.

## Decisions

**A separate seeder, not an extension of the review project bootstrap.** The strategy doc's baseline
says that bootstrap creates project metadata only and explicitly not demo users. Identities are the
one class of seeded row that grants something, so it must remain possible to have the synthetic
project without people who can act on it.

**`docs/testing/seeded-review-demo-data-strategy.md` was extended first.** Its allowed-data list did
not cover users or memberships, and its baseline named demo users among what is deliberately not
created. Shipping the seeder against a spec that forbade it would have made the spec worthless.

**`GET /api/review-identities` is registered on the bean, not guarded by a runtime check.** A flag
tested inside the method leaves the route mapped and is one refactor from being tested in the wrong
branch. Conditioned on both the demo flag and persistence, the controller does not exist in a real
deployment and the URL is an ordinary 404.

**Why this is not the "no user and membership management over HTTP" non-goal.** It is GET only;
it creates, changes and revokes nothing. It moves no authority, because anyone who can set an actor
header can already claim any user id. It returns only rows carrying the marker the server itself
wrote, so a real user cannot appear even if the flag were on by mistake. And the memberships it
lists exist only on the synthetic project. The residual is real and is recorded in the controller:
with the flag on, this publishes ids that are valid actor headers for one synthetic project.

**Rejected: seeding work as well as people.** Snapshots, progress and previews are what a person is
there to create. Seeding them would put facts into the audit trail and the export chain that nobody
performed, which is the opposite of what walking the product is for.

**Rejected: a reset path.** Every `*_by_user_id` column across the schema references `users`, so
deleting a seeded identity either cascades through the audit trail or fails on a foreign key.
Reruns reuse what is there.

**Rejected: falling back to the nine-role selector when no identities are listed.** That is the
control being removed. With none available the picker shows the configured actor, disabled, and says
why.

**The listing reports the membership's role, not the role the seeder asked for.** They are the same
on a fresh seed. If they ever diverge, what the picker shows had better be what the server will
enforce.

## Verified

| Check | Result |
| --- | --- |
| `mvn test` | **501 tests, 0 failures, 0 errors, 0 skipped** (434 API + 67 worker), up from 480 |
| `npm test` | **130 tests** across the three workspaces, up from 122 |
| `npm run build` | both applications built |
| `git diff --check` | clean |

`ReviewDemoIdentityDatabaseTests` runs against a real PostgreSQL and asserts the outcome rather than
the mechanism: each seeded person is allowed their own step of the journey, the planner is refused
`SUBMIT_TASK_PROGRESS`, the supervisor is refused `APPROVE_EXPORT_BATCH`, and the two are different
users — the four-eyes separation, asserted now so Phase 2 cannot weaken it silently. A second
`ensureReviewIdentities()` creates nothing, which the partial unique index on active memberships
would otherwise catch as a failure.

`ReviewDemoIdentityWiringTests` proves the endpoint's absence in all three negative cases and its
presence in the positive one, so the "not in a real deployment" claim does not rest on reading an
annotation correctly.

**Not run:** `scripts/db/validate-migrations.sh`. This slice adds no migration, and Docker is not
available on this host. Nothing here changes any SQL file.

## Left open

- **The seeder has not been run on the deployment.** It ships disabled; enabling it, splitting the
  two build actors and redeploying are Phase 0 deployment work, taken next and in one redeploy.
- **`redeploy.sh` still bakes one actor into both bundles.** Changing only the role variable would
  fix nothing, because the server resolves the role from the membership — the mobile build needs a
  genuinely different seeded user. The lookup should be by role against the database so the script
  cannot drift from whatever seeds the people.
- **A seeded field user's My Work list is empty** until a planner links them to a Project resource
  in Exports › People. That is correct behaviour and a step of the walk, not a defect.
- **No controller test asserts the response omits `email`.** The record has no such component, so
  there is nothing to leak today; a test would pin that.
