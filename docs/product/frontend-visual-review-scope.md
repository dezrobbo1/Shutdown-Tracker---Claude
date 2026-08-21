# Frontend Visual Review Scope

This document defines how to treat current and future frontend visual-review work.

It exists because the current Task Progress Review visual shell is useful for review, but it must not become the final product information architecture or a source of implied backend/API contracts.

## Current visual shell status

**This section described the apps before they were wired to the API and is retained for history.
It no longer describes the code.** Both applications now read and write through the shared API
client, the field app has a durable IndexedDB progress queue, and the console implements the
five baseline zones. See [Verified current frontend capability](#verified-current-frontend-capability)
below for the current position.

The original visual shell showed the intended workflow:

```text
field progress update
-> supervisor review
-> planner review
-> export eligibility
-> export preview
-> MSPDI/XML artifact boundary
-> manual Microsoft Project verification metadata
```

The visual shell is not production workflow.

It does not implement:

- production task execution APIs;
- supervisor review APIs;
- planner review APIs;
- frontend write workflows;
- IndexedDB offline queue;
- production evidence upload;
- production handover workflow;
- production messaging;
- generated artifact writes from the frontend;
- automated Microsoft Project verification;
- Project write-back.

## Build status labels

Every future visual surface must be labelled with one of these statuses in the brief or code comments, and the UI should avoid implying more capability than exists.

| Label | Meaning |
| --- | --- |
| Verified in repo | Already implemented or scaffolded in current code/docs |
| Static visual only | Mocked for product/UX review; no live data or writes |
| Read-only API-wired | Can fetch existing backend data but cannot mutate it |
| Future write workflow | Product concept only; not implemented |
| Future production | Requires backend, permissions, audit, tests, and review before shipping |

## Verified current frontend capability

| Area | Current status |
| --- | --- |
| Console shell | Five baseline zones, hash-routed, with addressable sections inside a zone |
| Mobile shell | Five baseline zones; progress capture opens from a task rather than being a tab |
| Console API client | Reads and writes through the shared client, carrying the actor headers |
| Import review | Upload a Project file, request its parse summary, then read and accept/reject — all wired. Uploading and parsing were previously possible only by `curl`, which no document recorded. Two things the panel states rather than hides: parsing needs the project worker connected, and is refused with the batch marked failed when it is not; and **nothing lists pending import batches**, so a reload between upload and parse means uploading again. |
| Operational mapping | Import profiles, categories, snapshot resolution, execution readiness, wired |
| Task progress | Submit, supervisor queue and review, planner queue and review, wired |
| Problems, actions, handover | Wired; reads are open/unacknowledged queues only |
| Export lifecycle | Candidate registration, preview, approve/reject, generate, open, verify, wired. The generated artifact is now downloadable through the interface, gated on `GENERATE_EXPORT_ARTIFACT`; it was previously shown as `exportFileUri`, a server filesystem path a browser cannot reach. |
| Evidence | Register, upload the file, download it, and read it back per task or across the project, wired in both apps. The project list is bounded and says when it was cut. Storage is the local filesystem implementation; production object storage is still open. Capture needs a connection in the field app and says so |
| Critical Watch | Watchlists, work packages and Critical Update reporting are reachable over HTTP and surfaced in the console. The field app files Critical Updates from Today, through the offline queue; composing a package stays a planning act and is console-only |
| Mobile offline queue | Durable IndexedDB queue for progress, Critical Updates and raised problems, with idempotency keys and visible sync state. Evidence binaries are not queued |
| Mobile problem raising | Queued like progress. V012 gives `problems` an idempotency key, so a retry returns the problem the first capture raised; an unsent problem is listed on the Problems screen with its sync state, not hidden until it lands |
| Role-aware controls | Write controls gated by capability in both apps, checked against the server's own enum. Both apps now switch **identity**, not role: the console's role selector is replaced by a picker over the seeded review identities, and the field app has one on Sync where it had none. The previous selector was misleading — it re-derived every capability flag while the server resolved the role from `project_memberships` and ignored the header, so it could only un-grey a control the server would refuse or grey out one it would allow. The field picker is refused while unsent reports are queued, because the queue would flush one person's work under another's actor. |

## Visual-only areas

Current visual-only areas include:

- Today Progress Review widget;
- Task Detail Progress panel;
- Supervisor Review Queue;
- Planner Progress Review Queue;
- Export Preview progress-candidates section;
- Project Verification visual;
- Problems/blockers link examples;
- Handover Summary progress section;
- Mobile My Work progress states;
- Mobile Task Progress flow;
- Mobile Sync Queue progress examples.

These are review artifacts. They must not be used as evidence that the product has live task-progress workflow.

## Information architecture guardrail

The intended Master Console top-level zones remain:

```text
Today
Tasks
Problems
Evidence
Exports
```

The intended Mobile Field App top-level zones remain:

```text
My Work
Today
Problems
Evidence
Sync
```

Do not add top-level console areas such as `Supervisor Review`, `Planner Review`, `Verification`, `Messages`, `Chat`, `Reports`, or `Dashboard` without a product decision and ADR or source-doc update.

Recommended placement:

| Surface | Placement |
| --- | --- |
| Supervisor Review Queue | Today attention queue or Tasks saved view |
| Planner Progress Review Queue | Exports, with Today summary/count |
| Project Verification | Exports |
| Critical Watch | Today, with drill-down under Tasks/Problems as needed |
| Needs Response | Today/top chrome/user menu; not top-level Chat |
| Announcements | Controlled banner, not open channel |

## Initial anti-slop cleanup pass

The initial frontend cleanup pass addresses the visual-shell debt without adding production behaviour:

- Supervisor Review, Planner Review, and Verification are review sections within the approved zones, not permanent top-level navigation.
- Reviewer-facing examples use sanitised operational shutdown language rather than `Synthetic Task A1` labels.
- The mobile three-card sync strip is a compact sync/status banner.
- Mobile task cards show only the minimum operational fields and status indicators.
- Console card/chip density is reduced.
- Write controls are disabled with a stated reason when the acting role lacks the capability, rather than hidden.
- Project-boundary warnings and explicit offline wording remain visible.

Remaining visual review scope includes Critical Watch, Critical Updates, and entity-linked Discussion. These remain static visual-only surfaces until the related product/API contracts are approved.

## Visual review copy

Use one global visual-shell statement rather than repeating prototype language on every panel:

```text
Visual review shell. Static/synthetic data. No production write workflow.
```

Keep high-risk boundary copy visible where users could misunderstand Project handoff:

```text
Planner approval marks this progress as eligible for export preview. The master .mpp is not updated.
MSPDI/XML artifact generated — master .mpp not updated.
Shutdown Tracker records verification metadata only.
```

## Synthetic data rules

Synthetic data should be obviously fake to developers but realistic enough for reviewers.

Avoid reviewer-facing names such as:

- `Synthetic Task A1`;
- `Synthetic Summary B`;
- `Sample Row 1`;
- `Demo User A`.

Prefer sanitized operational examples:

- `C2 Cyclone — remove access cover`;
- `D2 Stack — scaffold inspection`;
- `HV inlet — vacuum clean-out`;
- `Furnace bottom — install blanking plate`;
- `Permit isolation — await operations release`;
- `Crane lift — wait for lift plan sign-off`.

Synthetic metadata can keep fixture IDs internally.

## Console visual rules

- One job per screen.
- Today should show attention queues and exceptions, not every workflow surface.
- Use tables/lists for planner review and export diff surfaces.
- Use cards sparingly for attention summaries or mobile.
- Avoid horizontal overflow in default desktop view.
- Avoid displaying all state dimensions at once unless the screen is explicitly a state-model reference.
- Push detailed state/history into drawers or detail pages.
- Do not create a universal dashboard that displays tasks, evidence, handover, export, communication, audit, and analytics all at once.

## Mobile visual rules

- My Work should show actual work before sync diagnostics.
- Use a compact sync banner, not large status cards, at the top of My Work.
- Each task card should show only:
  - task name;
  - area/work package;
  - current state;
  - percent complete where relevant;
  - one blocker/evidence indicator;
  - one sync indicator;
  - one primary action.
- Everything else belongs in Task Detail or Sync.
- Keep primary actions thumb-friendly.
- Do not place planner/export concepts in field-user flows.

## Visual route/component expectations

Future frontend work should move toward this structure:

```text
Today
  ProgressReviewSummary
  NeedsAttentionQueue
  SyncHealthSummary
  CriticalWatchSummary

Tasks
  TaskExplorer
  TaskDetailProgressPanel
  SupervisorReviewSavedView

Problems
  ProblemsBoard
  ProblemDetail
  ActionRegister

Evidence
  EvidenceReviewList
  EvidenceDetail

Exports
  PlannerProgressReview
  ExportPreviewCandidates
  ProjectVerification
```

Mobile structure:

```text
My Work
  AssignedTaskCards
  CompactSyncBanner

Task Detail
  TaskSummary
  ProgressUpdateFlow
  BlockerShortcut
  EvidenceShortcut

Problems
  ProblemCapture
  OwnedProblems

Evidence
  EvidenceCapture
  EvidenceQueue

Sync
  QueuedItems
  FailedItems
  ServerReceivedItems
  ConflictItems
```

## Acceptance criteria for future visual PRs

A future frontend PR should be rejected or revised if:

- it adds new top-level nav without updating product docs;
- it adds more panels to the single console overview instead of scoped surfaces;
- it creates a generic dashboard/card wall;
- it uses synthetic labels in reviewer-facing screens;
- it hides offline/sync failure states;
- it implies a disabled button is a live workflow;
- it implies Project write-back;
- it adds scheduler-like visuals;
- it introduces chat-style messaging;
- it uses color as the only state signal;
- it increases mobile field-card density beyond the minimum viable task card.

## Next coding implication

Critical Watch and Critical Update reporting have since been built and wired in both applications,
so the next surface from that list is entity-linked Discussion — still a static review surface until
its product and API contracts are approved.

The remaining frontend gaps are listed under "Not production-complete yet" in the root `README.md`.
Offline problem raising is done: it needed a server-side idempotency key on problem creation before
it could be code, and V012 added one. Assignment-scoped work lists are done too, and needed a
product decision first rather than a screen: V013 links a Microsoft Project resource to a Shutdown
Tracker user explicitly, and the field app now shows the reader's own work. The decision, and the
four different kinds of empty work list it has to keep apart, are in
[Field Identity and Assigned Work](field-identity-and-assigned-work.md).

The console gained one section for it — Exports > People — because saying which resource is which
person is the same kind of act as Operational Mapping. The five console zones and five field zones
are unchanged.

The Exports zone now also shows what Microsoft Project calculated: a panel beside the export batch
lists the candidate schedules a planner has returned, and lets a planner return one. It is a panel
inside the existing zone rather than navigation, because it belongs to the batch it is about. What
it shows is deliberately thin — filename, who returned it and when, the source and candidate
hashes, and a state that reads "Returned — not yet compared". Nothing on the screen compares the
schedules or offers a decision, because nothing behind it does either, and a review surface that
implied otherwise would be the exact failure `ux-anti-slop-rules.md` exists to prevent.

This cleanup is not a redesign. It is a guardrail pass to keep the visual review shell from becoming an AI-generated dashboard wall.
