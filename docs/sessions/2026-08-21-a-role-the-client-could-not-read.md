# 2026-08-21 — A role the client could not read

## Scope

Deploying the review identities and walking the first step. This entry records the one defect that
found, which the slice's own tests had not. Follows
[Identities to walk it as](2026-08-21-identities-to-walk-it-as.md).

## What was found

The seeder ran on the deployment and created four identities. The endpoint answered. And the answer
was wrong in a way nothing in the branch could see:

```json
{ "id": "...", "displayName": "Review Field User", "role": "FIELD_USER", "projectId": "..." }
```

`ProjectRole` had no `@JsonValue`, so Jackson fell back to the enum constant name. Every other
consumer of that enum reads `databaseValue()` — `field_user` — and so does the schema, the
permission matrix, and the TypeScript `ProjectRole` union the client validates against.

Two consequences, both silent:

- `projectRoleLabels[identity.role]` is `undefined`, so every option in the picker renders with no
  role beside the name.
- `isProjectRole("FIELD_USER")` is false, so a stored identity is discarded on the next reload and
  the session falls back to the build-time actor. The picker would appear to work and then quietly
  forget.

## Why the tests did not catch it

Every test in the slice worked in Java types. `ReviewDemoIdentityDatabaseTests` asserts capability
outcomes, `ReviewDemoIdentityServiceTests` asserts the seeded set, `ReviewDemoIdentityWiringTests`
asserts the bean is absent unless asked for. **None of them serialised anything.** The one test that
would have — a `@WebMvcTest` asserting the JSON — was the one deliberately left out of the slice,
recorded in that entry's Left open as "no controller test asserts the response omits `email`".

This is the same shape as the defect the previous goal was built around: every test passed, each
proved one step, and the defect lived in between — here, between the server's enum and the client's
union. The lesson is narrower and worth stating plainly: **a type shared across a process boundary
is not covered by tests that never cross it.**

## What changed

`@JsonValue` on `ProjectRole.databaseValue()`, with `@JsonCreator` on `fromDatabaseValue` so the
form it accepts matches the form it emits. This endpoint is currently the only place a role is
serialised — checked before changing it — so the fix is global in effect and affects no existing
response.

`ReviewIdentityControllerTests` is the test that was missing. It asserts the wire form of the role,
that the endpoint answers with no actor header, and that the response carries no `email`,
`externalSubject` or `status`.

## Verified

| Check | Result |
| --- | --- |
| `mvn test` | **504 tests, 0 failures, 0 errors, 0 skipped** (437 API + 67 worker) |
| `npm test` | 130 tests across the three workspaces |
| `npm run build` | both applications built |
| `git diff --check` | clean |

The new test was **run against the unfixed code first**, and failed with
`JSON path "$[0].role" expected:<field_user> but was:<FIELD_USER>`. A guard that has only ever been
seen to pass proves nothing about what it would catch.

`scripts/db/validate-migrations.sh` was not run: no migration changed, and this host has no Docker.

## Left open

The walk itself has not gone past the first step. Everything the previous entry left open stands,
and the deployment still needs its two build actors split.
