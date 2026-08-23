# 2026-08-23 — A runtime nobody was patching

Third entry of the day. This one started as a stack review and found that the most urgent thing in
the repository was not a missing capability but an unpatched one: the Spring Boot the whole backend
runs on had stopped receiving security fixes.

## Scope

Asked to review the repository systematically and then research the best stack for it. The review
was read-only. The research ran as a fan-out web search with adversarial verification. Two fixes came
out of it. No slice of the active goal was taken and `docs/goals/ACTIVE.md` is unchanged.

## What was found

The tree is healthy: `mvn test` and the frontend checks pass, 356 Java files across ~35,500 lines, 39
TypeScript files across ~11,500, 15 migrations, 81 test classes.

What it does not have is more interesting than what it does, because each absence is a decision
nobody has made yet rather than a defect:

| Absent | Present instead |
|---|---|
| Spring Security — no dependency at all | `TrustedHeaderActorResolver`; authorization *is* enforced from stored membership |
| Object storage | `LocalFileStore` only, backing four configured roots |
| Job queue | Synchronous HTTP to the worker, shared secret, disabled by default |
| Service worker | A manifest and an IndexedDB mutation queue, and nothing that caches the shell |
| Lint, format, coverage, SBOM, SAST | Four CI jobs, none of them a quality gate |
| Runtime deployment | Two Dockerfiles and a compose file marked "migration validation only" |

The authentication seam is worth recording as *fine*, so nobody redesigns it: because the actor
arrives through a resolver interface and the role is resolved from membership rather than the header,
adding real authentication does not disturb `ProjectAuthorizationService` or the `Capability` enum.

**The finding that became work.** `spring-boot-starter-parent` was pinned to **3.3.0**, a line that
ended OSS support on 30 June 2025 and *commercial* support on 30 June 2026 — no security fixes on
either track.

**Two documents are stale, and one number is reserved.** ADR-004 still records the backend language
as open between Kotlin and Java. `docs/goals/ACTIVE.md` Phase 2 slice 6 reserves **ADR-012** for the
roles contract, so any new ADR must start at 013 or collide.

**Why the mobile manifest shipped wrong.** `redeploy.sh` builds the app with `--base=/mobile/`, and
**Vite copies `public/` verbatim without rewriting URLs inside a `.webmanifest`**. So `start_url: "/"`
and `icons[0].src: "/pwa.svg"` shipped as written, and the installed field app launched the console.
The icon failure was invisible rather than loud: `/pwa.svg` returns `200 text/html`, not a 404,
because the console's SPA fallback answers with `index.html`.

## What changed

Two branches, one reviewed outcome each.

- Pull request [#30](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/30) raises Spring
  Boot 3.3.0 to 3.5.16. One line; every Spring dependency in both services is parent-managed.
- Pull request [#31](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/31) gives the mobile
  manifest relative URLs.

Neither was merged.

## Decisions

**3.5.16 rather than 4.1.x, and the reason is a count.** Boot 4 ships Jackson 3, which moves
`com.fasterxml.jackson.core` and `.databind` to `tools.jackson`. Twenty-four files import them:
seven in main (`ApiStrictJsonConfiguration`, `JdbcAuditEventRecorder`, and the five JSONB-handling
repositories) and seventeen in test. `identity/ProjectRole.java` is the only Jackson file unaffected,
because `com.fasterxml.jackson.annotation` keeps its package in Jackson 3. Boot 4 also renames
`spring-boot-starter-web` to `spring-boot-starter-webmvc` and requires an explicit
`spring-boot-starter-flyway`. Recorded here so the count does not have to be rediscovered.

**3.5.16 is a waypoint, not a destination.** It is itself the last OSS patch of the 3.5 line, whose
free support also ended 30 June 2026. It buys two years of accumulated fixes and the version Spring
asks you to reach before 4.0; it does not buy ongoing free patching, which lives on 4.1.x.

**Relative manifest URLs rather than a build-time substitution or a second manifest.** They resolve
against the manifest's own location, so one file is correct under both bases — `/mobile/` in
production and `/` in development.

**No ADR was written, deliberately.** The research answered decisions 1–3 and not 4–6, and the
decision was to hold all of them until the evidence is complete rather than record two now and three
later.

**What the research settled, and what it did not.** Five angles, 25 sources, 125 claims extracted,
**25 verified** — 21 confirmed, 4 refuted. The budget went on the first three decisions, so the
report is not an answer to six. It does support: Spring Security OAuth2 Resource Server with a
multi-tenant `JwtIssuerAuthenticationManagerResolver`, and Keycloak for the on-prem path, with
Zitadel disfavoured on AGPL and Ory Hydra on shipping no user management; **MinIO archived** since
April 2026, read-only and source-only, and not to be shipped; **Garage** implementing no object
versioning and none of the six Object Lock endpoints; **Ceph RGW** implementing Compliance-mode WORM,
but only at the S3 API layer. db-scheduler satisfies the no-second-stateful-component constraint,
which is not the same as being the best queue.

**Four claims were refuted 0-3 and must not reappear**: that Keycloak 26.6.0 promoted the RFC 7523
JWT authorization grant to supported; that Keycloak 27.x carries a three-year lifecycle as framed;
that MinIO officially positions AIStor as the remedy and the community edition as EOL; and that Ceph
Object Lock carries a Cohasset SEC 17a-4 assessment. Several surviving findings have short
half-lives — Keycloak ships roughly four minors a year, Garage has live work in progress on
versioning, and MinIO's situation escalated twice in nine months.

## Verified

`mvn test`, `npm test` and `npm run build` pass locally on both branches.

Two upgrade risks were live and both closed clean. **Flyway moved 10.10.0 to 11.7.2** and validated
all fifteen migrations, applying them to a real PostgreSQL 16.2 and reaching v015. **3.5 parses
booleans strictly** — every boolean across the seven `application*.yml` files in both services was
already a literal `true` or `false`. Jackson stayed on 2.x (2.17.1 to 2.21.4), so no import moved.

The manifest was verified by building with `--base=/mobile/` and with the default base, then
resolving each URL against where the manifest lands.

CI is green on both pull requests, four jobs each — including **Migration and export-integrity
validation**, the Docker job this machine cannot run, which confirms the Flyway 11.7.2 sequence
through the containerised path and not only the embedded one.

## Corrections

The Spring Boot pin was first reported in this session as version drift, alongside Java 21 versus
Java 25. That understated it: 3.3.0 is end of life on both support tracks, which makes it a security
finding rather than a currency one, and it is the only thing here that justified jumping the active
goal's queue.

## Left open

- **A second research pass** for decisions 4, 5 and 6 — the PWA offline layer, deployment and
  air-gapped delivery, and CI/CD tooling — plus the halves left unfinished: AWS S3, Azure Blob and
  SeaweedFS on the storage axes, the Java storage abstraction, and pgmq and JobRunr against
  db-scheduler.
- **The ADRs**, once that lands. Start at ADR-013 and amend ADR-004 at the same time.
- **The field app still has no service worker.** The manifest is now correct, so it installs
  properly — and what installs is a shell that does not load offline. Only mutations survive a signal
  loss. "Installable" currently promises more than it delivers, and that is the substance of the
  unresearched offline decision rather than a bug to fix.
- Neither pull request was merged.
