# 2026-08-22 — A handover that had aged

A repository-wide cleanup review, commissioned by a handover brief that turned out to describe a
repository several days older than the one it was pointed at.

## Scope

Asked to perform the review set out in `Shutdown_Tracker_Claude_Code_Review_Handover.docx`: an
independent, review-first pass over the entire repository for stale material, duplicated authority,
dead code, temporary scaffolding and unnecessary complexity, producing a disposition for every
significant subtree and an explicit list of disagreements with the brief.

Review-first was taken literally. The review itself changed nothing; the changes in this entry were
made afterwards and are only the subset that carries no runtime risk. Everything structural is left
open below.

## What was found

**The brief was stale, and its most serious finding was already fixed.**

Section 6 of the handover records a merge-blocking correctness issue: the XML authority verifier
indexing repeated non-`Task` siblings by local element name with `putIfAbsent`, so that a second
`Resource`, `Assignment`, `Calendar`, `PredecessorLink`, `ExtendedAttribute` or `Baseline` could
collapse onto the first and escape the "only approved execution inputs changed" proof. That code no
longer exists. `MspdiCandidateDifference.indexByKey` now keys every child by identity *and*
occurrence:

```java
int occurrence = seen.merge(identity, 1, Integer::sum) - 1;
byKey.put(identity + "#" + occurrence, element);
```

`Task` is the deliberate exception, keyed by UID so like compares with like regardless of position.
`MspdiCandidateDifferenceTests` carries 11 tests covering exactly this, including a change to a
repeated sibling *after the first*, a dropped one, an added one, and a rejected second copy of an
approved field. The only two `putIfAbsent` calls left in the repository are unrelated.

**The authority proof is two-part, and both parts had to be checked.** `MspdiCandidateDifference`
explains a difference when it matches an approved `(task UID, field name)` pair — by name only. Read
alone, that proves only approved *fields* changed, not that they changed to the approved *values*.
The second half is in `MpxjMspdiExportArtifactService.verifyOnlyApprovedInputsChanged`, which walks
the candidate again and throws unless every approved value arrived with its exact canonical value.
Together the claim holds. Checking one without the other would have produced a false finding.

**Two residual gaps in that proof, neither blocking.** Tasks match by UID, so a candidate that
reordered task elements while preserving UIDs and IDs would report no difference — the generator
edits in place, so this cannot currently arise. And `compare()` only inspects text when *both* sides
have no element children, so text alongside child elements is never compared; MSPDI does not use
mixed content.

**Six of nine `ProgressExportState` values are never written.** Only `not_eligible` and `eligible`
(`TaskProgressService:159-161`) and `superseded` (raw SQL, `JdbcTaskProgressRepository:210`) are ever
assigned. `EXPORT_BLOCKED`, `APPROVED_FOR_EXPORT`, `IN_EXPORT_PREVIEW`, `ARTIFACT_GENERATED`,
`OPENED_IN_MICROSOFT_PROJECT` and `VERIFIED` are dead — they mirror `ExportBatchState`, which owns
that lifecycle for real. `V009:152-153` builds a partial index `WHERE export_state IN ('eligible',
'approved_for_export')` on a value nothing writes.

**Two signpost READMEs were not merely redundant but false.** `apps/README.md` claimed the mobile
PWA "currently uses static synthetic scaffold data only" and that neither app "creates execution
records"; the PWA has an IndexedDB offline queue, evidence capture with upload, and offline problem
raising. `services/README.md` claimed "no task execution domain logic, scheduler logic … exists
here", while `services/api` contains `execution/`, `operations/`, `criticalwatch/`, `candidate/` and
`exportpreview/`.

**Three of the repository's largest artifacts are absent from the brief's inventory:**
`services/api/README.md` (34.8 KB — the largest single document here),
`V007__enforce_export_candidate_integrity.sql` (67 KB, protected), and
`scripts/db/assertions/export-integrity-current-policy.sql` (59 KB), which is a hand-written mirror
of V007's policy. With `ExportIntegrityPostgresIntegrationTests` that is roughly 150 KB across three
artifacts that must be kept consistent by hand — the largest such liability in the repository.

**Neither open remote branch had anything in it.** `origin/feat/migration-drift-guard` and
`origin/docs/product-walkthrough` are both 0 commits ahead of `main`, so no unmerged work could
conflict with any cleanup.

**Microsoft Project `Critical`, Total Slack and Free Slack are not imported at all.** Nothing in the
worker's importer or in `project-import-contract` reads them. Anything framed as "align to Project
critical path" is new feature work with schema consequences, not cleanup.

## What changed

Only the risk-free subset, in one change:

- deleted `apps/README.md` and `services/README.md` — zero inbound links, and `README.md:150-178`
  already carries the structure map they duplicated;
- replaced the committed `Shutdown_Tracker_Claude_Code_Review_Handover.docx` with a markdown
  conversion. `CONTRIBUTING.md` § Secrets and Artifacts bars committed DOCX files, so the brief was
  in breach of the repository's own policy from the moment it landed. The original stays in git
  history;
- this entry.

The two merged remote branches were deleted separately, outside this change:
`feat/migration-drift-guard` at `58146d4` and `docs/product-walkthrough` at `7a6c764`. Both were
re-confirmed 0 commits ahead of `main` immediately beforehand, so both commits remain reachable as
ancestors of `main`.

## Decisions

**Delete the two false READMEs rather than correct them.** Rewriting them accurately was considered
and rejected: they would then restate the root structure map and the per-app READMEs, which is the
condition that let them drift out of true in the first place. A signpost that lies is worse than no
signpost, and the information exists in two accurate places already.

**Do not archive `docs/sessions/` as the brief proposes.** It calls the folder one of the largest
sources of active-documentation noise and recommends moving all of it to an archive labelled
historical. Twelve of the seventeen entries are dated 2026-08-17 to 2026-08-21 and describe work
still landing. `docs/README.md` already draws the boundary correctly in one paragraph. If sessions
are ever archived it should be by age relative to a closed goal, never wholesale.

**Do not remove `reviewdemo`.** The brief treats it as accumulated temporary infrastructure with a
removal condition overdue. Its first commit is 2026-08-21 — *Let a person be more than one person* —
and `2026-08-21-identities-to-walk-it-as.md` records why: the active goal requires walking the
product as three roles. It is correctly guarded, default-disabled in `application.yml:53-61`, with
no frontend caller. `ReviewProjectBootstrap*`, from 2026-06-19, is the older and fairer target when
the time comes.

**Treat `AGENTS.md` as load-bearing when slimming it.** The brief marks it SIMPLIFY HARD. Line 54 is
where the corrected authority interpretation actually lives — that the prohibition is on hidden or
independent scheduling by Shutdown Tracker, not on Microsoft Project recalculating a review
candidate. Product detail can move out; that text cannot.

**Keep the state separation the dead enum values sit inside.** `TaskProgressRepository:40-41` and
`JdbcTaskProgressRepository:180` both document why the handoff queue keys on `export_state` rather
than the planner's decision: supersession must not rewrite the record of what a planner once
approved. The unused values are the target; the separation is not.

## Verified

Run on this machine, on the change described above:

- `mvn test` — BUILD SUCCESS. 448 tests in `services/api`, 75 in `services/project-worker`, 0
  failures, 0 errors, 0 skipped. The database-backed tests (`JdbcUserRepositoryTests`,
  `OperationalMappingServiceDatabaseTests`, `ExportIntegrityPostgresIntegrationTests` among them)
  executed against a local PostgreSQL rather than being skipped.
- `npm test` — 73 console, 43 mobile-pwa, 28 api-client. 144 passing, 0 failures.
- `npm run build` — both apps build; `api-client` type-checks clean.
- Inbound-reference search for every deleted path, across all markdown, TypeScript and Java: no
  remaining references to `apps/README`, `services/README`, or the `.docx`.

Not run, and not claimed: the migration and export-integrity validation job
(`scripts/db/validate-migrations.sh`), which needs the Docker Compose PostgreSQL stack; and any
manual Microsoft Project round trip. Neither is reachable from a documentation-only change.

Findings were verified against a read-only `git archive` extract of `origin/main` at `675e2da`, so
no part of the review could modify what it was reviewing.

## Corrections

Two recommendations made earlier in this same session, before the inbound links were checked, were
wrong:

- **`docs/product/frontend-visual-review-scope.md` is not a completed brief to archive.** It is
  cited as current guidance by `docs/architecture/README.md:141`, `apps/console/README.md:53`,
  `apps/mobile-pwa/README.md:40`, and a source comment at `apps/mobile-pwa/src/App.tsx:916`, and it
  was updated on 2026-08-21. It stays.
- **`docs/product/communications-layer.md` cannot be archived cheaply.** Five inbound links,
  including `AGENTS.md:97` and a behavioural rule at `docs/architecture/README.md:132`. The module it
  documents is genuinely unbuilt — no discussion or comment entity exists in any migration or API
  package — but moving it means redirecting those references first.

The brief itself also states, in section 1.1, that the repository holds an outdated reading in which
"Shutdown Tracker is not a scheduler, therefore schedule facts must not change". No document
asserting that was found. `AGENTS.md:54`, `docs/product/README.md` and
`task-progress-review-export-approval.md:131` all already carry the corrected reading.

## Left open

Ordered by ratio of value to risk.

1. **Fold `docs/concept/README.md` into `docs/product/README.md`.** Both answer "what is this
   product"; the product README is the more current and already carries the corrected authority
   model. The concept pack's four-option progress-method detail is the content that must survive the
   merge. Touches `README.md` (structure block and authority list), `CONTRIBUTING.md` §
   Documentation Authority, and the concept-pack link at
   `docs/design/prototypes/design-c/README.md:11`.
2. **Delete `fixtures/import-export/example-fixture-manifest.json` and
   `expected-import-summary.example.json`.** Their only references anywhere are the two lines of
   `fixtures/import-export/README.md` that describe them.
3. **Delete `ProjectExportSpikeRunner` and `ProjectImportSpikeRunner`.** Both
   `@ConditionalOnProperty` on a property nothing sets; superseded by
   `MpxjMspdiExportArtifactServiceTests`, the synthetic fixtures and
   `scripts/review/source-import-export-smoke.ps1`. Removing them obsoletes about ten lines of
   `services/project-worker/README.md`.
4. **Add a repeated-`Resource`/`Assignment` case to `MspdiCandidateDifferenceTests`.** Existing
   fixture content exercises `Calendar`/`WeekDay` and `PredecessorLink`. The keying mechanism is
   element-name-agnostic, so this is cheap confirmation rather than a suspected hole.
5. **Retire the six dead `ProgressExportState` values.** Needs a new migration — PostgreSQL enum
   values cannot be dropped by edit — and the `V009:152-153` partial index rebuilt on values that
   are actually written. Requires the full migration validation job.
6. **Split the monoliths**, preserving every assertion and exported symbol:
   `apps/mobile-pwa/src/App.tsx` (50.9 KB), `apps/console/src/App.test.tsx` (33.5 KB),
   `apps/mobile-pwa/src/App.test.tsx` (29.1 KB), `packages/api-client/src/index.ts` (57.5 KB),
   `apps/console/src/zones/ExportZone.tsx` (25.6 KB), `services/api/README.md` (34.8 KB).
7. **State which layer owns which export-integrity invariant**, across `V007`,
   `scripts/db/assertions/export-integrity-current-policy.sql` and
   `ExportIntegrityPostgresIntegrationTests`. Not a deletion — the duplication is deliberate — but
   nothing currently records the division, so all three drift together or not at all.
8. **Consolidate the two documentation overlap pairs**: `approval-export-state-model.md` with
   `task-progress-review-export-approval.md`, and `roles-and-capabilities.md` with
   `permission-matrix.md`. Product-scope work, not hygiene.
9. **Build mobile supervisor mode** — crew allocation, and approve/reject/request-correction away
   from the desk. The one genuine gap in the console/mobile split; a feature, not cleanup.

The brief's own closing test — whether a newcomer can answer its thirteen questions quickly — passes
today for most of them from `docs/product/README.md` and `AGENTS.md` alone. The two that still
require reading several documents are *what is historical only*, because sessions, research, concept
and completed briefs all sit in the active tree, and *which app does a supervisor use*, because the
answer is "both, and the mobile half is not built". Item 1 addresses the first. Only item 9
addresses the second.
