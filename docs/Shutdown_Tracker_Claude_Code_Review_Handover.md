<!--
Converted from Shutdown_Tracker_Claude_Code_Review_Handover.docx, which was committed to this
folder on 2026-08-22 and removed in the same change: CONTRIBUTING.md bars committed DOCX files,
and markdown is diffable so a later revision of the brief reviews as a change rather than a blob.
The original remains in git history. Text and table content are unchanged from the DOCX.

This is a dated input to a review, not current product authority. The review it commissioned, and
the corrections to it, are recorded in docs/sessions/2026-08-22-a-handover-that-had-aged.md.
-->


# Shutdown Tracker
Claude Code Review and Cleanup Handover
Review first. Do not mutate the repository until the review is complete and approved.
Repository: https://github.com/dezrobbo1/Shutdown-Tracker---Claude
Primary objective: Perform an independent, systematic cleanup and simplification review of the entire repository while preserving the safety-critical XML/Project authority boundary, audit, offline reliability, and deployment integrity.

# Primary Request
Perform an independent, systematic review of the ENTIRE repository for cleanup, simplification, stale material, duplicated authority, dead code, temporary scaffolding, and unnecessary complexity.
This is a REVIEW FIRST task.
Do not start deleting or refactoring simply because this handover says something looks removable. Verify every conclusion against the live repository, references, build configuration, tests, application wiring, Git history where relevant, and current product direction.
The aim is not "make the repository smaller at all costs." The aim is to remove noise, eliminate obsolete or misleading material, consolidate duplicated documentation authority, identify genuinely dead code, isolate temporary/demo/spike code, simplify oversized files and state models, preserve integrity controls, and leave one clear current product/architecture direction.

# 1. Important Product Context
Shutdown Tracker is a shutdown/turnaround/outage execution-control platform. It is NOT intended to replace Microsoft Project as the scheduling/calculation engine.

## 1.1 Authority Model
- Shutdown Tracker = execution-input authority
- captures actual execution facts
- Start / Pause / Resume / Finish
- progress
- delays/problems
- evidence
- handover
- supervisor/planner review
- approved inputs for Project
- Microsoft Project = schedule calculation authority
- receives an updated disposable candidate schedule
- recalculates dates, dependencies, assignments, work, summaries, float/critical path etc.
- those Project-calculated consequences are expected
- Planner = adoption authority
- reviews the recalculated candidate against the source/master schedule
- decides whether to reject, retain for analysis, or manually adopt/merge the candidate
- Shutdown Tracker must not silently overwrite the master .mpp
The old interpretation - "Shutdown Tracker is not a scheduler, therefore schedule facts must not change" - is NOT the intended product model.
The correct interpretation is: Shutdown Tracker must not invent the schedule calculation itself. Microsoft Project may recalculate the candidate schedule after approved execution facts are applied.
We have also decided to FINISH the current XML/MSPDI approach rather than abandon it. Therefore MSPDI/XML candidate generation, source preservation, Project identity, candidate differencing, Project round-trip testing and related integrity controls are CURRENT architecture, not legacy to be deleted.

# 2. Frontend Product Direction

## 2.1 Console / Desktop Application
Primary users: planner; shutdown manager; coordinator; control-room/control-team users.
Primary responsibilities:
- Today / upcoming work
- task oversight
- problems
- evidence
- import review
- Project mapping/configuration
- people/resource administration
- planner review
- export/candidate schedule workflow
- Project round-trip/adoption workflow
- Critical Path / Critical Work Pack reporting and oversight

## 2.2 Mobile PWA
Primary users: field worker; supervisor.
Field workflow:
- My Work
- task detail/dossier
- Start
- Pause
- Resume
- Finish
- progress
- problem/delay capture
- evidence/photo capture
- handover
- offline queue/sync
Supervisor workflow should also exist in mobile:
- crew/work allocation
- assign execution work
- review submitted progress
- approve/reject/request correction
- see field problems/evidence/handover
- manage work while away from the desktop console
Do NOT merge console and mobile into one application simply for repository simplification.

# 3. Resource / Assignment Clarification
Microsoft Project resources/assignments and Shutdown Tracker operational execution assignment are related but not necessarily identical.
Project imports may contain resource names/groups/assignments. Shutdown Tracker needs its own practical execution assignment capability for supervisors, workers, crews and work ownership.
Review whether current `assignment` architecture incorrectly conflates these concepts. Do not remove Project resource preservation because of this distinction.

# 4. Task / Operational UX Direction
The task should become the operational centre of the system. A task dossier/detail should eventually aggregate:
- Project task identity
- WBS/area/context
- assigned worker/crew
- supervisor
- execution state
- progress
- actual start/finish
- pause/block reason
- problems
- actions
- evidence
- comments/notes
- handover
- review state
- export status
- candidate schedule consequence where relevant
The Today surface should evolve into a rolling next-24-hour execution/control view, rather than being merely a static navigation dashboard.

# 5. Critical Work Terminology
The older "Critical Watchlist" model needs review.
Desired concepts are closer to:
- Microsoft Project Critical Path / critical tasks - imported/calculated schedule facts from Project.
- Critical Work Packs - planner-selected operational work packages that may need enhanced attention even if not currently on the calculated critical path.
- Critical Update / shift reporting - structured reporting for selected work packages/tasks during shutdown execution.
Do not create a second scheduling engine to calculate critical path.

# 6. Previous Repository Review - Important Findings
A previous live GitHub review found the repo to be a strong pre-production engineering baseline, but with important cleanup and architecture issues. VERIFY ALL OF THESE AGAINST LIVE GITHUB.
One important code-review finding was identified in the source-derived candidate schedule work: the XML authority verifier indexed repeated non-Task siblings using only their local XML element name and `putIfAbsent`.
if Task:
    key by Task UID
else:
    key by local element name
This means repeated siblings such as Resource, Assignment, Calendar, PredecessorLink, ExtendedAttribute and Baseline could collapse to the first instance during comparison. If that implementation still exists, later repeated siblings could escape the claimed "only approved execution inputs changed" proof.
This was considered a merge-blocking correctness/authority-boundary issue. The fix should use structurally complete identity/order-aware differencing and regression fixtures containing multiple repeated Project entities. Do not assume this remains unresolved. Verify current code and tests.

# 7. Current Cleanup Review Report
A complete recursive-tree review was performed against a recorded `main` snapshot. GitHub reported `truncated: false`, so the inventory was complete at that snapshot. VERIFY current live HEAD before relying on the recorded SHA.
The review concluded that the largest cleanup opportunities were:
- documentation/history noise
- oversized frontend files
- execution/export lifecycle complexity
- demo/spike/temporary tooling
The recommendation was NOT to aggressively delete XML integration, immutable snapshots, audit, offline queue/idempotency, Project identity, migrations or synthetic Project fixtures. Those provide clear integrity value.

# 8. Folder-by-Folder Recorded Disposition

## 8.1 Root

| Path | Disposition | Action / Reason |
|---|---|---|
| .github/ | KEEP | CI is small and relevant. |
| .dockerignore | KEEP | Normal repository hygiene. |
| .gitignore | KEEP | Normal repository hygiene. |
| LICENSE | KEEP | No cleanup value. |
| root package.json / package-lock.json / pom.xml | KEEP | Build authority. |
| README.md | SIMPLIFY | Reduce implementation chronology; retain product, apps, architecture, getting started and authoritative docs. |
| AGENTS.md | SIMPLIFY HARD | Recorded at about 15 KB. Keep safety constraints, workflow rules, authority model and validation expectations; move detailed product material to authoritative docs. |
| CONTRIBUTING.md | KEEP / SLIM | Remove duplicated validation guidance if another document owns it. |


# 9. Apps - Console

| Path / Component | Disposition | Action / Reason |
|---|---|---|
| apps/README.md | MERGE INTO ROOT -> DELETE | Small signpost; may not justify another documentation layer. |
| apps/console/README.md | KEEP / SIMPLIFY | Console-specific development/build/use information only. |
| consoleApi.ts, download.ts, formatting.ts, router.ts, session.ts, hooks | KEEP | Normal app infrastructure. |
| apps/console/src/raw.d.ts | KEEP | Intentional tiny Node typing shim for Vitest. |
| App.tsx | KEEP / SIMPLIFY | Focus console on planner/control responsibilities. |
| App.test.tsx | SPLIT | Recorded around 33 KB; preserve assertions but divide by journey/zone. |
| TodayZone.tsx | REWORK | Rolling next-24-hours operational/control view. |
| ExecutionZone.tsx | REVIEW RESPONSIBILITY | Field execution belongs mobile; console may retain oversight. |
| HandoverZone.tsx | REVIEW RESPONSIBILITY | Capture belongs mobile; planner/control oversight may remain. |
| ReviewQueueZone.tsx | SIMPLIFY | Planner/coordinator review stays desktop; supervisor review also mobile. |
| CriticalWatchZone.tsx | REMODEL | Use Project critical path, Critical Work Packs and structured shift reporting. |
| EvidenceZone.tsx | KEEP | Appropriate console oversight. |
| ProblemsZone.tsx | KEEP | Appropriate console oversight. |
| ImportReviewZone.tsx / MappingZone.tsx / PeopleZone.tsx | KEEP / SIMPLIFY | Planner/control responsibilities. |
| ExportZone.tsx | KEEP / DECOMPOSE | Recorded around 25 KB and carries too much responsibility. |


# 10. Mobile PWA

| Path / Component | Disposition | Action / Reason |
|---|---|---|
| apps/mobile-pwa/README.md | REWRITE | Explicitly define field and supervisor modes. |
| src/App.tsx | SPLIT | Recorded around 50 KB; one of clearest frontend monoliths. |
| src/App.test.tsx | SPLIT | Divide into field/supervisor journeys. |
| fieldSession.ts | KEEP / RENAME LATER | Mobile is no longer field-user-only. |
| indexedDbQueueStore.ts | KEEP | Offline integrity. |
| offlineQueue.ts | KEEP | Offline integrity. |
| useFieldQueue.ts | KEEP / GENERALISE | Broaden naming as supervisor offline actions are added. |
| styles.css | KEEP / SIMPLIFY | Use shared design tokens as authority. |
| public/manifest.webmanifest / pwa.svg | KEEP | Normal PWA assets. |


# 11. Documentation - Largest Cleanup Target

## 11.1 ADRs
Keep ADR-001 through ADR-011. ADRs are historical decisions and should not be rewritten merely because later decisions supersede them. Instead make status clear, mark superseded ADRs, and add new ADRs for changed direction. Keep `docs/adr/README.md` as a concise decision index.

## 11.2 Architecture
- `docs/architecture/README.md` - make this the single current technical architecture overview; shrink duplicate detail.
- `audit-event-schema.md` - KEEP.
- `object-storage-provider-strategy.md` - possible ARCHIVE/PAUSE until production object-storage implementation resumes.
- `project-operational-mapping-implementation.md` - recorded around 24 KB; SIMPLIFY, do not automatically delete.
- `worker-handoff-queue-strategy.md` - KEEP / SIMPLIFY depending on current implementation parity.

## 11.3 Concept / Design / Active Goal
- `docs/concept/README.md` - strong MERGE -> DELETE candidate so it does not compete with product authority.
- `docs/design/prototypes/design-c/` - keep as historical visual provenance/reference; label clearly as NOT runtime authority.
- `docs/goals/ACTIVE.md` - HIGH PRIORITY. Replace if stale/misleading relative to current product direction.

# 12. Product Documentation Consolidation

| File | Disposition | Action / Reason |
|---|---|---|
| approval-export-state-model.md | CONSOLIDATE | Overlap with task-progress/review/export authority. |
| task-progress-review-export-approval.md | CONSOLIDATE | Unify or clearly divide authority. |
| correction-and-supersession-rules.md | KEEP | Distinct rule set. |
| offline-audit-sync-rules.md | KEEP | Distinct offline integrity rules. |
| project-candidate-schedule-handoff.md | KEEP | XML candidate workflow is current. |
| project-operational-mapping.md | KEEP / SIMPLIFY | Current operational mapping direction. |
| communications-layer.md | ARCHIVE / PAUSE | Unless communications is current implementation work. |
| critical-watchlist-permissions.md | SUPERSEDE / REWRITE | Align to Critical Path / Critical Work Pack / shift reporting. |
| roles-and-capabilities.md | REWRITE | Simpler CONTROL / SUPERVISOR / FIELD / optional VIEWER model. |
| permission-matrix.md | CONSOLIDATE | Avoid two hand-maintained permission authorities. |
| field-identity-and-assigned-work.md | REWRITE | Separate Project resource/assignment from operational execution assignment. |
| frontend-visual-review-scope.md | ARCHIVE | Likely completed implementation brief. |
| design-language-and-status-semantics.md | KEEP / SIMPLIFY | Current design authority. |
| ux-anti-slop-rules.md | KEEP / CONSOLIDATE | Retain principles, reduce duplicate design authority. |


# 13. Research
Research should remain provenance, not current authority. Review README.md, research-decisions-summary.md, research-index.md, source-map.md and source-quality-register.md for duplication. Do not delete useful reasoning/provenance. State clearly that research does not override accepted ADRs, current product contract or current architecture.

# 14. Sessions
This was identified as one of the largest active-documentation noise sources. Recorded repo had approximately 17 dated session reports plus README.md.
Recommendation: move them to `docs/archive/sessions/` and label them: HISTORICAL DEVELOPMENT RECORD. NOT CURRENT PRODUCT OR ARCHITECTURE AUTHORITY. Preserve useful archaeology; remove them from the active authority chain.

# 15. Source Material
Do NOT delete `docs/source-material` wholesale.
Inspection showed its README intentionally establishes a public-repository safety/provenance rule: real Microsoft Project schedules, customer/site files, uploaded research bundles and raw operational files must not be committed to this public app repository.
KEEP `docs/source-material/README.md` and `source-disposition.md`. Potentially consolidate/delete empty scaffolding READMEs in inbox, research and reference if they contain no unique ongoing value. Retain the rule: DO NOT REINTRODUCE REAL OPERATIONAL PROJECT FILES INTO GIT.

# 16. Testing Documentation
Generally KEEP. Especially retain manual Microsoft Project round-trip evidence, fixture strategy, product walkthrough and validation evidence. `seeded-review-demo-data-strategy.md` may become archive material once temporary review-demo provisioning is removed.

# 17. Fixtures
Synthetic MSPDI/XML fixtures are valuable and should stay, including `synthetic-basic-wbs` and `synthetic-shutdown-areas`. They protect different Project integration cases including WBS, tasks, resources, assignments, operational mapping and candidate export expectations.
Potential cleanup candidates: `example-fixture-manifest.json` and `expected-import-summary.example.json`. VERIFY consumers/references first. If they are scaffolding superseded by real fixtures, DELETE.

# 18. Infra / Database
Critical rule: DO NOT DELETE OR REWRITE APPLIED MIGRATIONS.
Recorded migration chain is V001 through V014. Even if the schema design could now be cleaner, migrations are database history. Cleanup/change happens through new migrations.
Recent work also introduced/strengthened migration apply tooling, migration ledger, drift detection and validation. Treat this as important safety infrastructure, not noise. Simplification opportunity is primarily duplicated README material.

# 19. Packages
- `packages/README.md` - likely merge into root repository structure -> delete.
- `packages/api-client` - KEEP, but split recorded ~57 KB `src/index.ts` and ~25 KB `index.test.ts` by domain while preserving API compatibility.
- `packages/design-tokens` - KEEP as shared design authority.
- `packages/project-import-contract` - KEEP.
- `packages/project-export-contract` - KEEP. XML/MSPDI remains current architecture.

# 20. Scripts
KEEP current migration tooling: apply-migrations.sh, backfill-migration-log.sh, check-schema-drift.sh and migration validators.
There is also a substantial export-integrity SQL/concurrency test suite. Do not casually delete it. Review whether any assertions are now exactly duplicated by Java/PostgreSQL integration tests. Long-term target: one clear test owns each invariant.
Keep `source-import-export-smoke.ps1` while the XML round trip is still being completed and manually validated.

# 21. Backend Package Review

| Package | Disposition | Reason |
|---|---|---|
| actor | KEEP transitional | Replace only when production authentication lands. |
| assignment | KEEP / REVIEW SEMANTICS | Do not conflate Project resource assignment with operational worker assignment. |
| audit | KEEP | Integrity critical. |
| candidate | KEEP | Current XML path. |
| criticalwatch | REMODEL / SIMPLIFY | Align terminology and reporting model. |
| execution | SIMPLIFY | Review state proliferation. |
| exportpreview | SIMPLIFY HARD | Largest runtime complexity hotspot. |
| identity | SIMPLIFY | Reduce roles/capabilities. |
| importbatch | KEEP | Current import pipeline. |
| importedproject | KEEP | Current snapshot entities. |
| importreview | KEEP | Planner/control responsibility. |
| mapping | KEEP / SIMPLIFY | Current operational mapping. |
| operations | KEEP | Problems/actions/evidence/handover; surface through task dossier. |
| project | KEEP core | Review bootstrap pieces are temporary candidates. |
| reviewdemo | DEPRECATE / REMOVE WHEN SAFE | Temporary review environment. |
| sourcefile | KEEP | Source provenance. |
| storage | KEEP | Safety and abstraction. |
| tasklineage | KEEP | Identity integrity. |


# 22. State-Machine Complexity
Recorded execution package includes separate states such as PlannerReviewState, ProgressExportState, ProgressReviewState and TaskExecutionState. Export/candidate workflow separately has candidate, preview, approval, batch and verification/handoff state.
The goal is NOT to collapse all audit distinctions into one status. The review question is: Which distinctions need to be independent mutable runtime state, and which are better represented as immutable events or safely derived state?
- impossible combinations
- duplicated state transitions
- status values whose only purpose is to mirror another entity
- state that can be derived safely
- controller/service complexity caused by state proliferation

# 23. Large Backend Hotspots
Recorded sizes included approximately `ExportPreviewService.java` ~44 KB and `JdbcExportPreviewRepository.java` ~45 KB. These are strong candidates for decomposition, not blind rewrite.
Potential cohesive boundaries include eligibility, candidate creation, stale-state validation, approval, artifact handoff, Project-open verification and adoption/provenance.

# 24. Temporary / Demo Backend
`reviewdemo/*` and `ReviewProjectBootstrap*` were identified as legitimate temporary review-environment infrastructure. They should have an explicit removal condition. Once proper authentication, user provisioning and project setup exist, remove them instead of allowing review/demo infrastructure to become permanent product architecture. Verify whether they are still required today.

# 25. Project Worker
KEEP MPXJ parsing, entity extraction, MSPDI candidate generation, candidate differencing, task schema ordering, worker handoff, worker auth and storage path confinement.
Potential genuine cleanup candidates: `ProjectExportSpikeRunner.java` and `ProjectImportSpikeRunner.java`. The export runner was inspected and is a Spring CommandLineRunner enabled by a specific export-spike property, with hard-coded synthetic tasks to exercise candidate generation. Review whether fixtures, tests and smoke scripts fully replace its purpose. If yes, MOVE TO TEST TOOLING or DELETE. Apply the same analysis to import spike runner.

# 26. Test Cleanup
Do NOT reduce test coverage as "cleanup." Split oversized tests by behaviour.
- ExportPreviewServiceTests.java - recorded ~89 KB
- ExportArtifactHandoffServiceTests.java - recorded ~33 KB
- CandidateScheduleRunServiceDatabaseTests.java - recorded ~25 KB
Better structure could include ExportPreviewEligibilityTests, ExportPreviewStalenessTests, ExportApprovalIntegrityTests, ExportBatchLifecycleTests, CandidateSourceIntegrityTests and CandidateProjectRoundTripTests.

# 27. High-Confidence Cleanup Pass
1.  Archive `docs/sessions/*` out of active authority.
2.  Replace stale `docs/goals/ACTIVE.md`.
3.  Collapse `docs/concept` into current product authority.
4.  Consolidate overlapping product role/permission/export docs.
5.  Archive completed visual-review/implementation briefs.
6.  Collapse empty source-material subfolder scaffolding while retaining provenance policy/register.
7.  Merge/delete trivial directory-level README signposts.
8.  Verify/remove fixture example scaffolding.
9.  Verify/move/remove `*SpikeRunner` components.
10.  Explicitly mark review-demo/bootstrap infrastructure temporary.

# 28. Protected Areas
A cleanup should NOT weaken or delete these without very strong evidence:
- V001-V014 migration history
- migration drift controls
- immutable Project/source hashing
- accepted source-file provenance
- MPXJ import
- MSPDI candidate generation
- candidate schedule differencing
- Project import/export contracts
- Task UID/ID lineage
- audit
- authorization
- offline idempotency
- IndexedDB/offline queue
- evidence integrity/storage confinement
- synthetic XML regression fixtures
- returned Project candidate/provenance
- manual Project round-trip evidence

# 29. Proposed Cleanup Sequence

| PR | Scope | Constraint |
|---|---|---|
| PR 1 | Documentation authority and historical-noise cleanup | No runtime changes. |
| PR 2 | Verified dead/demo/spike scaffolding cleanup | No intended product behaviour change. |
| PR 3 | Split frontend/client/test monoliths | Structural refactor only. |
| PR 4 | Align console/mobile responsibilities | First deliberate product-behaviour change. |
| PR 5 | Simplify roles and operational assignment model | Behaviour/data model change. |
| PR 6 | Simplify execution/export state machinery while retaining XML/audit integrity | High-risk internal simplification. |
| PR 7 | Align Critical Path / Critical Work Packs / shift reporting | Product model alignment. |
| PR 8 | Task dossier + rolling next-24-hour model | Core operational UX. |

Claude does not have to agree with this sequence. Review independently and improve it.

# 30. Claude Code Task - Review Before Any Deletion
1.  Read `AGENTS.md`.
2.  Read `docs/goals/ACTIVE.md`, but verify whether it is stale rather than blindly treating obsolete statements as product truth.
3.  Inspect the current root tree recursively.
4.  Verify current `main` HEAD.
5.  Inspect open branches and PRs that could conflict with cleanup.
6.  Determine what files are generated, runtime-required, test-only, historical, temporary, or externally authoritative.
7.  Search references before labelling code dead.
8.  Check Spring component discovery / conditional properties before deleting apparently unused Java classes.
9.  Check Maven and npm module dependencies.
10.  Check CI references.
11.  Check scripts and documentation links.
12.  Check fixture consumers.
13.  Check runtime configuration/property references.
14.  Check whether documentation is authoritative, historical, superseded or merely duplicated.
15.  Identify obvious monoliths and unnecessary abstraction layers.
16.  Identify duplication between code paths and between tests.
17.  Identify stale terminology and architecture assumptions.
18.  Do not change anything yet.

# 31. Required Output from Claude Code

## A. Executive summary
- overall repo health
- major sources of noise
- estimated cleanup risk

## B. Repository inventory
- for every top-level folder and significant subtree: KEEP / SIMPLIFY / CONSOLIDATE / MOVE-ARCHIVE / VERIFY THEN DELETE / DELETE, with reasoning

## C. Exact high-confidence deletion/archive candidates
- path
- current purpose
- evidence it is obsolete/unreferenced
- risk
- replacement if any

## D. Documentation authority map
- current product authority
- technical architecture authority
- ADR/history
- research/provenance
- archived development history

## E. Runtime complexity report
- oversized files
- overlapping abstractions
- state-machine duplication
- temporary production code
- confusing naming
- package-boundary issues

## F. Frontend responsibility report
- what belongs console
- what belongs mobile FIELD
- what belongs mobile SUPERVISOR
- what is shared

## G. Project/XML integrity report
- Project UID/ID
- source preservation
- candidate generation
- Project recalculation handoff
- candidate return/differencing
- planner adoption authority

## H. Proposed cleanup PR sequence
- exact scope
- expected files
- what must not change
- validation required
- whether manual Project validation is required

## I. Disagreements with this handover
- explicitly state any earlier recommendation that is wrong based on the live repository

# 32. Final Rule
The desired endpoint is a repository where a new engineer or coding agent can answer these questions quickly:
- What is Shutdown Tracker?
- What does Microsoft Project own?
- What does Shutdown Tracker own?
- What does the planner own?
- Which app does a field worker use?
- Which app does a supervisor use?
- Which app does a planner/control user use?
- What are the authoritative product documents?
- What is historical only?
- What files can safely be changed?
- What integrity rules must never be bypassed?
- How is Project XML round-tripped?
- How does the planner review/adopt a recalculated candidate?
If answering those requires reading dozens of overlapping documents and historical session notes, the cleanup is not complete.
