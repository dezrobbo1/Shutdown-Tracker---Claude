# 2026-08-24 — Answering a machine reviewer

Second entry of the day. The first was Phase 1 slice 5; this one came out of being asked to review
the Codex connector's comments on the open pull requests, and then to act on them.

## Scope

Assess eight automated review findings across pull requests
[#32](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/32) and
[#33](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/33), then a further four raised
when the first fixes were pushed. In scope: judging each finding against the code and the
deployment, and taking the ones that hold. Out of scope: the product work in either pull request,
which is theirs.

## What was found

**Six of the twelve findings were real, and the two most useful were not the highest-graded.** The
one P1 of the first round was wrong on its central claim; two P2s were bugs worth the trip.

**The P1 that was not one.** "Configure the production base before registering the worker" reasoned
that `vite.config.ts` sets no `base`, so `BASE_URL` is `/`, so the worker requests `/sw.js` and
scopes to `/` — either 404ing or taking over the console. The premise is true and the conclusion is
not: the deployment builds with `npx vite build --base=/mobile/`, in `redeploy.sh`, which the
Caddyfile comment also states. `BASE_URL` is `/mobile/` in the only build that reaches a device.

What the finding did surface is real and outlives the pull request: **that base lives in a deploy
script outside the repository**, so the committed `npm run build` produces a bundle wrong for the
deployment. The assets have always had that dependency and fail loudly. A mis-scoped service worker
registration is the first thing that fails *silently*, because the `.catch()` that keeps a failed
registration from breaking startup also swallows the evidence. Left open below.

**A cache write could fail the request it came from.** `put()` is awaited after the network has
already answered. A rejection — storage unavailable, restricted, over quota — was therefore read by
`networkFirst` as a failed request, serving a stale document or the "no offline copy" notice to
somebody who had a connection; and in `cacheFirst`, where it sat outside any `try`, it rejected the
asset outright and broke a page load on a fully online device. This was the cheapest fix of the set
and the one with the worst failure mode.

**Every release's bundles accumulated.** The cache name changes when the caching *rules* change, not
when the app does, and activation deleted only *other* cache names — so each release added its
bundles and nothing ever removed the previous ones. The end of that is quota eviction, which takes
the offline shell with it: the one thing the worker exists to protect.

**Two more arrived after the first fix, both about reach rather than about what may be cached.**
Activation deleted every cache name that was not the current one, on an origin that also serves the
console from `/` — so the field app's activation would have deleted another application's storage.
And `cache-first` was the catch-all for anything under the base, which caught the two files Vite
copies verbatim from `public/`. Those carry no content hash, so a release that changes their bytes
does not change their URL, and the pruning added an hour earlier deliberately spares them. Cached
forever, nothing to clean them, and no second URL to ask for.

That second one is the interesting one, because **the first fix created it**. Sparing the unhashed
files from pruning was correct — a rule that kept only what the build manifest lists would delete
the offline document on the next release — but it turned "never cleaned" into a permanent state for
two files that also had no other way to change. The review round caught a consequence of the
previous review round.

**On the documentation side**, the storage inventory in the pull request #32 entry said
`LocalFileStore` backed all four configured roots. It backs three; source uploads go through
`LocalSourceFileStorage`, a separate implementation with its own hashing and filesystem writes. That
entry was also missing from the session index, which is where `AGENTS.md` sends a session to find
what earlier ones learned.

## Decisions

**Cache-first now means content-hashed, and nothing else.** The alternative for the unhashed files
was to add them to the install manifest so pruning could manage them. Rejected: they would then be
fetched on install, which is the one moment a field device's connection is being spent on the shell,
to precache an icon. Network-first gives them the document's treatment — current online, last copy
offline — for no install cost.

This also collapsed two overlapping rules into one. **The set that is cached forever is now exactly
the set that activation prunes.** Before, `cache-first` covered a superset of what
`supersededAssetPaths` cleaned, and the gap between the two was where a file could be stranded. A
disagreement between two rules is harder to see than either rule being wrong.

**The cache name moved into `serviceWorkerPolicy.ts`.** That file already declares itself the owner
of every caching decision, and the deciding factor was that a name in `sw.ts` is a name no test can
reach — the same argument the file makes for itself.

**Scoping the sweep by prefix rather than by an allowlist of known caches.** An allowlist would have
to be updated by whoever adds the next application to this origin, which is exactly the kind of
maintenance nobody performs. A prefix is owned by the code that creates the caches.

**Two findings were judged correct and deliberately not taken**, both recorded on the pull requests
rather than folded in: moving `base` into the build is a deployment-behaviour change with its own
blast radius, and committing the 25-source research behind the #32 entry is a decision about what
the repository should hold rather than a defect in the entry.

**One finding was correct when written and had resolved itself.** The #32 entry described the
manifest fix as done while #31 was unmerged; #31 has since merged and the sentence is now true.

## Verified

Run in this session, on the pull request #33 branch:

- `npm test` — **167 passing**, being 73 console, **66 mobile-pwa** and 28 api-client. The branch
  had 157 before this session and the ten added here are all service-worker rules.
- `npm run build` — clean on all three workspaces, `tsc --noEmit` included, and the built `sw.js`
  still has its shell manifest substituted: `["","assets/index-….css","assets/index-….js"]` in
  `dist/`, checked rather than assumed, because the plugin that fills it is what a change to the
  worker's shape could quietly break.
- `git diff --check` clean.
- **Each new rule was severed and watched to fail.** Dropping the `assets/` guard from
  `supersededAssetPaths` failed four tests, including the one holding the offline document;
  dropping the prefix guard from `supersededCacheNames` failed the test that protects another
  application's storage; and restoring the `cache-first` catch-all failed the unhashed-files test.
  All were restored and the suite re-run green.
- GitHub Actions green on `4b70860`, the first of the two commits — checked on that commit rather
  than on an earlier run.

**Not verified: the deployed behaviour of any of this.** The browser evidence in pull request #33 —
the worker controlling `/mobile/`, the cache holding exactly the shell, the app rendering offline —
was produced by the session that wrote the worker and is recorded in that pull request's
description. This session did not re-run it, and the changes here are precisely the kind a unit test
cannot fully speak for: cache eviction, activation ordering, and quota behaviour are properties of a
real browser. Anybody merging this should treat one online load followed by one offline load, twice
across a release, as the check that matters.

**Not verified: that a quota rejection is in fact caught.** The best-effort `put()` is asserted by
reading, not by a test. `sw.ts` has no test harness — the policy module exists so the *decisions*
can be tested, and the worker's plumbing is deliberately outside that. Filling a device's quota in
CI is not a test this repository should grow.

## Corrections

Nothing stated in this session turned out to be wrong. Two things stated *elsewhere* were, and are
corrected on their pull requests: the storage inventory in the #32 entry, and — in the first round of
my own fixes — leaving the unhashed public files with no update path, which the second round
repaired.

## Left open

- **No session entry exists for the service worker itself.** `AGENTS.md` asks for one from the
  session that changed the repository, and pull request #33 carries none; Codex raised this and it
  is correct. This entry is not a substitute: it records reviewing and amending that work, not the
  decisions taken while building it, and writing those up second-hand would put verification in the
  history that this session did not run. Its pull request description holds the reasoning in the
  meantime.
- **`base` belongs in `vite.config.ts`**, so that a build made from a checkout matches the build
  that is deployed, and so that the correct value stops living only in an uncommitted script.
- **The expected test counts in `docs/goals/ACTIVE.md` are stale** — 144 frontend and 43 mobile,
  against 167 and 66 here. They are the guard that says a test was lost rather than that the suite
  got faster, so a stale figure is a weakened guard. Deliberately not updated in any one of the
  three open pull requests: each would write a number the other two falsify, which is the drift the
  document has already suffered twice. It wants one update after they land.
- The API-prefix finding, judged unreachable as deployed but pointing at a better shape: restricting
  `cache-first` to the build's own asset allowlist rather than to a directory convention.
