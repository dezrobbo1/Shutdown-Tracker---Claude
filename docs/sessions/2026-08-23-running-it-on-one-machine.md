# 2026-08-23 — Running it on one machine

Second entry of the day. The first was Phase 1 slice 3; this one came out of being asked to start
the console and the field app for review, and finding that neither could be.

## Scope

Get the product running on one machine: database, project worker, API, and both front ends, with
the identities the walkthrough needs. In scope: whatever stood between a checkout and a running
stack. Out of scope: the walk itself, which is a person's job, and anything the apps do once up.

## What was found

**Neither front end can reach the API from a dev server.** Both resolve their API base URL to their
own origin — correct, because a deployment serves them together — and **the API declares no CORS
anywhere**. No `WebMvcConfigurer`, no `@CrossOrigin`, nothing in `infra/`. A Vite dev server is a
second origin, so every request fails before it reaches a handler. Nothing in the repository
configured a proxy, which means there was no committed way to run these two apps against a local
API at all.

**The project worker could not start outside its tests.**

```text
APPLICATION FAILED TO START
Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.
```

It holds no state and touches no database — there is no `DataSource`, `JdbcTemplate` or repository
anywhere in its main sources — but it inherits `spring-boot-starter-jdbc`, Flyway and the PostgreSQL
driver from the build, and Boot configures a DataSource from their presence alone. Only
`application-test.yml` excluded them. `services/project-worker/Dockerfile` sets neither a profile nor
an exclusion, so **the shipped image fails the same way**: the test profile had been standing in for
real configuration, and no check could see it because every check runs under that profile.

**The two services have to agree about where files live.** The worker reads the source file the API
stored and writes the artifact the API serves, so `SHUTDOWN_TRACKER_SOURCE_FILE_STORAGE_LOCAL_ROOT`
and `SHUTDOWN_TRACKER_EXPORT_ARTIFACT_STORAGE_LOCAL_ROOT` must be the same directories for both.
Nothing said so.

**The worker's auth secret is required, not optional.** Starting without
`SHUTDOWN_TRACKER_WORKER_AUTH_SHARED_SECRET` fails the context with "shared-secret must be set when
worker authentication is enabled", despite the property defaulting to an empty string. Found by
doing it.

**The `local` Spring profile is the intended local path and already does the right thing** — points
Flyway at `infra/migrations`, applies on start, enables persistence, and turns on the trusted-header
actor. No new configuration was needed for the API.

## Decisions

**A dev-server proxy rather than CORS on the API.** Adding CORS would change what the API allows in
every environment to solve a problem that only exists in one. The proxy keeps requests same-origin,
which is what the apps already assume, and does not exist in a build.

**Fixed, strict ports.** The two apps are meant to run together, and Vite's default of taking the
next free port would put whichever started second on the other's port — indistinguishable once you
are looking at a browser tab.

**The worker's unused dependencies stay.** Removing `spring-boot-starter-jdbc`, `flyway-core`,
`flyway-database-postgresql` and `postgresql` from its pom is the tempting fix and was rejected for
this change: it is a build change with its own blast radius, and the `local` profile below actually
uses Flyway. The exclusion is correct either way.

## Verified

Everything here was run in this session, on the branch:

- **The whole stack, together.** PostgreSQL 16.2, the worker on 8081, the API on 8080 under the
  `local` profile applying V001–V015 to a fresh database, both dev servers. All four review
  identities seeded against the bootstrapped project.
- **The worker starts unaided** — from the jar, no profile and no environment override, `UP`, and no
  `Failed to configure a DataSource` in the log. The same command failed before the change.
- **The worker's `local` profile still applies migrations** — Hikari connects, Flyway validates 15
  migrations and reports the schema up to date. This mattered because the base-level exclusion
  disabled it at first: see Corrections.
- **The proxy works from both origins**, against the committed config rather than a scratch one:
  `/api/review-identities` and `/actuator/health` 200 through 5173 and 5174, and capability-gated
  calls with the trusted-header identities — planner queue through the console, assigned work
  through the field app — 200 on both.
- `npm test` 144 passing; `npm run build` clean on all three workspaces, `tsc --noEmit` included.
  The vite config is shared with vitest, so both paths matter.
- `mvn -pl services/project-worker -am test` — 76 passing.

**Not verified: that either app renders.** There is no browser on this machine and none was
installed. The dev servers serve their transformed entry modules and the API path underneath them is
proven, but whether React mounts is unconfirmed, and a blank page would not have been caught by
anything above.

No manual Microsoft Project verification was performed and none is claimed.

## Corrections

**The first version of the worker fix broke the `local` profile.** Putting
`spring.autoconfigure.exclude` in `application.yml` disables datasource and Flyway auto-configuration
at base level, and `application-local.yml` did not override it — so the profile would have loaded and
done nothing, while `services/project-worker/README.md` says it applies migrations and can be used to
check runtime migrations through this service. A developer would have believed migrations were
exercised when no Flyway instance existed. The profile now clears the exclusion, and the evidence
above is the check that should have been run before claiming the fix.

**`apps/console/README.md` told developers to set `VITE_SHUTDOWN_TRACKER_API_BASE_URL` to an absolute
URL.** That makes the client call that origin directly, going around the proxy and into the missing
CORS configuration — so the documented setup contradicted itself the moment the proxy existed. The
README now says to leave it unset and points at `SHUTDOWN_TRACKER_API_ORIGIN` for a remote API.

Both were found in review of the pull request, not by me.

## Left open

- **The worker's unused database dependencies.** Per the decision above.
- **Whether the apps render.** Needs a browser on the machine, or a person opening the page.
- **The walk itself.** The stack exists now; the last unmet completion condition in
  `docs/goals/ACTIVE.md` still needs somebody to drive it through the interface and write down what
  they found.
