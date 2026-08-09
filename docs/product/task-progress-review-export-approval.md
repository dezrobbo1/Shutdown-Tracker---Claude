# Task Progress Review and Export Approval

Task Progress Review and Export Approval is the next core Shutdown Tracker product capability.

It connects field execution truth to the existing Microsoft Project import/export foundation without turning Shutdown Tracker into a scheduler or an automatic Project write-back tool.

## Product decision

Shutdown Tracker should capture structured task progress, route it through supervisor validation and planner review, and only then allow selected leaf-task progress/actual fields to become export-preview candidates.

Core workflow:

```text
field progress update
-> supervisor review
-> planner review
-> export eligibility
-> export preview
-> MSPDI/XML artifact generated
-> planner manually opens/checks in Microsoft Project
-> planner controls whether master .mpp is saved
-> Shutdown Tracker records verification metadata and audit
```

## Product boundary

Microsoft Project remains the schedule authority and final master-file control point.

Shutdown Tracker owns:

- execution truth;
- task progress capture;
- supervisor review;
- planner review;
- export eligibility checks;
- export preview preparation;
- MSPDI/XML artifact metadata;
- manual Microsoft Project verification metadata;
- blockers, actions, evidence, handover, and audit.

Shutdown Tracker must not:

- calculate CPM;
- calculate float;
- calculate critical path;
- resource-level;
- optimise the schedule;
- automatically move dates;
- edit dependencies, constraints, calendars, baselines, WBS, resources, or planned dates;
- perform hidden write-back into Microsoft Project;
- imply that progress approval updates the master `.mpp`;
- imply that MSPDI/XML artifact generation updates the master `.mpp`;
- imply that Microsoft Project verification means the master `.mpp` was saved.

## Why this is the next major capability

The current repo already has partial import/export review infrastructure, export preview, export approval/generation metadata, Project opened/verified metadata, and worker-backed MSPDI/XML artifact handoff.

The missing product bridge is:

```text
field task update -> supervisor review -> authoritative candidate created -> planner candidate approval
```

The authoritative-candidate API supplies the export-integrity boundary, but the live field-update and supervisor-review source records do not exist yet. Until that product bridge is implemented, candidate creation must use explicit synthetic or otherwise authorized review provenance rather than reviewed live execution records.

## Users and responsibilities

| User | Responsibility in this workflow | Must not be burdened with |
| --- | --- | --- |
| Field User | Submit structured progress, blockers, comments, and evidence references for assigned work | Export eligibility, Project fields, WBS-wide review |
| Contractor | Submit scoped progress and evidence under contractor visibility rules | Other contractors' work or export decisions |
| Supervisor | Validate operational accuracy, evidence completeness, blockers, and completion claims | Final Microsoft Project export approval |
| Coordinator | Triage review queues, blockers, actions, and handover impact | Project file mechanics |
| Shutdown Control | Maintain live operational awareness and ensure review queues are moving | Routine field entry or Project save decisions |
| Planner | Decide which reviewed leaf-task progress fields are safe to send toward Microsoft Project | Raw field entry or frontline evidence capture |
| Inspector | Review quality/evidence outcomes where assigned | Schedule handoff decisions |
| Viewer / Management | Read progress status, exceptions, handover, and report summaries | Editable task/review/export controls |

## State dimensions

Do not collapse every task condition into one status. A task can be blocked, queued locally, awaiting planner review, and not export-eligible at the same time.

| Dimension | Purpose | Example states |
| --- | --- | --- |
| Execution state | What is happening at the workfront | Not started, Ready, In progress, Paused, Blocked, Completed |
| Progress review state | Whether the submitted progress has been checked operationally | Draft, Submitted, Needs supervisor review, Supervisor accepted, Correction requested, Rejected, Superseded |
| Planner review state | Whether a planner has reviewed export relevance and Project safety | Draft, Needs planner review, Planner approved, Planner rejected |
| Export state | Whether approved values are safe for export preview/handoff | Not eligible, Eligible, Export blocked, Approved for export, In export preview, Artifact generated, Opened in Microsoft Project, Verified, Rejected / superseded |
| Sync state | Whether a client-side event has reached the server | Local draft, Queued on device, Sending, Server received, Failed, Conflict |

## Execution state rules

| State | Meaning | Typical actor | Required fields | Export implication |
| --- | --- | --- | --- | --- |
| Not started | Imported task is not yet active | System / planner | none | No export by itself |
| Ready | Available for work today or assigned | Supervisor / system | assignment or readiness context | No export by itself |
| In progress | Work has genuinely started | Field user / supervisor | start time or progress note | May support actual start candidate after review |
| Paused | Temporarily stopped, not necessarily blocked | Field user / supervisor | pause reason | Internal operational state only |
| Blocked | Cannot continue until issue is resolved | Field user / supervisor | blocker type, severity, short description | Blocks export review until reviewed |
| Completed | Field says work is done | Field user | completion confirmation, evidence if required | May support percent complete / actual finish candidate after review |

## Progress submission fields

| Field | Field-entered? | Supervisor review? | Planner review? | Export-eligible? | Notes |
| --- | --- | --- | --- | --- | --- |
| Percent complete | Yes | Yes | Yes | Yes, leaf tasks only | MVP export candidate |
| Actual start | Yes | Yes | Yes | Yes, leaf tasks only | MVP export candidate |
| Actual finish | Yes | Yes | Yes | Yes, leaf tasks only | MVP export candidate |
| Physical percent complete | Optional | Yes | Yes | Deferred / internal first | Use only where site practice is consistent |
| Remaining duration | Rarely | Yes | Yes | Deferred | Higher Project recalculation side-effect risk |
| Actual duration | Rarely | Yes | Yes | Deferred | Prefer derived/controlled handling later |
| Actual work / remaining work | No for MVP | Yes if later enabled | Yes | Deferred | Assignment/work model complexity |
| Assignment actuals | No for MVP | Yes if later enabled | Yes | Deferred | Requires separate assignment-review model |
| Comment | Yes | Yes | Sometimes | No | Context only; not progress truth |
| Evidence reference | Yes | Yes | Yes for completion-critical work | No direct Project export | Supports review confidence |
| Blocker link | Yes | Yes | Yes if export affected | No direct Project export | May block candidate |

## MVP export whitelist

Only these fields may become MVP export candidates, and only for imported leaf tasks:

- percent complete;
- actual start;
- actual finish.

These are not MVP export candidates:

- physical percent complete, unless a later product decision enables it for a specific project/site;
- remaining duration;
- actual duration;
- actual work;
- remaining work;
- assignment actuals;
- summary-task actuals;
- planned dates;
- dependencies;
- constraints;
- calendars;
- baselines;
- WBS/outline structure;
- resource rates, availability, allocation, or levelling data.

## Leaf-task and summary-task rules

- Only leaf-task progress/actual fields may become export candidates.
- Summary tasks may appear as context, reporting groups, or Critical Work Package sources.
- Summary-task progress must not be exported directly.
- Let Microsoft Project roll up summary values.
- A summary-task progress submission should be corrected into child-task progress or kept as internal/reporting context.

UI copy:

```text
Summary task. Not eligible for direct progress export.
Leaf task. Eligible fields may be reviewed for export.
```

## Supervisor review workflow

Supervisor review confirms whether a field update is operationally credible. It does not approve Microsoft Project export.

Supervisor actions:

| Action | Meaning | Next state |
| --- | --- | --- |
| Accept | Update is operationally valid | Supervisor accepted |
| Correct | Supervisor changes or clarifies value/reason | Correction requested or superseded update |
| Reject | Update is not accepted | Rejected |
| Request evidence | Completion or claim lacks required proof | Evidence gap / export blocked |
| Link blocker | Work is constrained by structured issue | Blocked / export blocked |
| Include in handover | Incoming shift must know | Handover item linked |

Required copy:

```text
Supervisor review confirms operational validity. It does not approve Microsoft Project export.
```

## Planner review workflow

Planner review decides whether a reviewed progress value is safe to send toward Microsoft Project through export preview.

Planner review queue must show:

- imported project snapshot;
- imported task identity;
- task name/code;
- leaf/summary indicator;
- current imported Project value;
- proposed Shutdown Tracker value;
- source update;
- submitted by/at;
- supervisor review state;
- evidence state;
- blocker/action state;
- re-import/lineage conflict state;
- export eligibility result;
- exclusion reason if blocked;
- planner approve/reject/request-clarification decision.

Required copy:

```text
Planner approval marks this progress as eligible for export preview. The master .mpp is not updated.
```

## Authoritative export-candidate binding

Candidate creation and candidate approval are separate events. Creating a candidate captures one immutable fact against an accepted project snapshot; it does not imply planner approval or export eligibility. Every later approval, rejection, `correction_requested`, or supersession event identifies that exact candidate. Before `approved_for_export` is appended, the database locks and revalidates the accepted snapshot, task UID/ID/name/leaf state, captured baseline, canonical proposed value, source fingerprint, and candidate identity. Non-authorizing history remains appendable after drift. Current authority is the latest database-ordered event for the candidate, not the latest event for a reusable generic source identity.

The server creates the candidate from the requested accepted snapshot, imported task, recognized field, proposed value, and required source-version identifier, which becomes immutable as part of the candidate. It derives the captured baseline value and Microsoft Project task UID/ID/name/leaf state, canonicalizes the proposed value, and computes the source-event or payload fingerprint from canonical candidate content and provenance. The caller cannot supply the captured baseline, task identity, normalized value, fingerprint, or approval state.

Export-preview requests select authoritative candidate IDs only. The server loads each immutable candidate and separately resolves its exact latest database-ordered candidate-bound approval event. It derives task identity, field, old value, new value, and source identity from the candidate; none of those facts or the approval identity comes from the preview caller. An approval for one candidate cannot authorize another task, field, value, snapshot, project, or source version.

Before candidate approval, preview materialization, batch approval, artifact generation, and generated-metadata recording, the exact referenced snapshot must remain accepted and every candidate must match the current imported snapshot row. A changed old value, task UID, task ID, task name, leaf/summary state, approval identity/state, candidate payload, or source version blocks authorization and requires a fresh candidate or preview as applicable. Final generation holds locks in this order until generated metadata commits: export batch; project snapshot; candidates in stable ID order; imported tasks in stable ID order; then current candidate-bound approval rows in stable candidate/event order. Candidate approval takes the project advisory lock, active export batches in stable order, accepted snapshot, candidate, then imported task before inserting an authorizing event. This is a freshness check only; it does not calculate or reconcile schedule values.

## Candidate value normalization

- `percent_complete` is a whole number from 0 through 100. Equivalent inputs such as `75`, `75.0`, and `075` canonicalize to `75`; fractional values are rejected.
- Proposed `actual_start` and `actual_finish` values require ISO-8601 minute- or second-precision values with an explicit offset and canonicalize to whole seconds. Omitted seconds become `:00`. A fractional component is accepted only when it contains one through six digits and every digit is zero; that zero-valued fraction canonicalizes away. Non-zero fractions and fractions outside the one-to-six-digit input range are rejected because the MSPDI writer does not preserve them. Canonicalization preserves the reviewed Microsoft Project local wall-clock component and emits one canonical offset form; the worker uses that local component without converting it to UTC. Offset-free and invalid values are rejected.
- Captured imported `actual_start` and `actual_finish` baselines use a separate canonicalizer that preserves available microsecond precision for freshness comparison. Baseline values are not sent to the worker as proposed values.
- `physical_percent_complete` may remain readable as internal or historical context, but it is never export eligible and never enters the worker contract or MSPDI/XML artifact.
- For proposed export values, the API/database boundary and shared contract, worker, and XML checks enforce equivalent canonical rules. Imported actual baselines use the separate precision-preserving baseline canonicalizer.

## Export preview workflow

Export preview is a planner-facing comparison and approval surface. It must show old value, new value, source, task identity, field, eligibility, and exclusion reason.

Required sequence copy:

1. Candidate created — master `.mpp` not updated.
2. Candidate approved — master `.mpp` not updated.
3. Export preview created — master `.mpp` not updated.
4. Export batch approved — master `.mpp` not updated.
5. MSPDI/XML artifact generated — master `.mpp` not updated.
6. Artifact opened in Microsoft Project — master `.mpp` not updated.
7. Artifact verified in Microsoft Project — master `.mpp` not updated.
8. Planner manually updates or saves the master `.mpp` — outside Shutdown Tracker automation.

The manual Microsoft Project round-trip remains pending until a planner completes and records the synthetic artifact check.

## Microsoft Project verification workflow

Project verification records that a planner manually opened or checked the generated MSPDI/XML artifact in Microsoft Project. It does not automate Project and does not save the master `.mpp`.

Verification metadata should include:

- export batch ID;
- generated artifact ID/URI/hash;
- generated at/by;
- opened in Microsoft Project state;
- opened by/at;
- verified by/at;
- verification outcome;
- rejection/supersession state;
- notes;
- audit correlation ID.

Required copy:

```text
Shutdown Tracker records verification metadata only. Saving or updating the master .mpp is a manual planner-controlled step outside Shutdown Tracker automation.
```

## Re-import and stale candidate handling

Every Microsoft Project re-import creates a new immutable snapshot. A candidate remains bound to the snapshot and imported task identity it captured; it cannot be carried into a later snapshot. Continued export preparation requires a new candidate against the newly accepted snapshot and a new candidate-bound approval.

| Scenario | Behaviour |
| --- | --- |
| Same imported task confidently matched | Create a new candidate against the newly accepted snapshot and require a new candidate-bound approval; retain the prior candidate as history |
| Task renamed but matched | Require a new candidate and planner review against the new snapshot; do not reuse the prior candidate or approval |
| Task moved WBS/summary | Require a new candidate and planner review; summary-task actuals remain ineligible |
| Task deleted | Retain the prior candidate as orphaned history and keep it non-exportable |
| Task replaced | Require planner lineage review, then create and approve a new candidate if appropriate |
| Summary/leaf status changed | Require a new candidate and recheck field authority; summary-task actuals remain ineligible |
| Snapshot changed after mobile offline capture | Mark conflict and require review before export |

UI copy:

```text
Re-import conflict. Planner lineage review required.
```

## Blockers, actions, evidence, and handover integration

Progress review is not just a percent-complete screen. It must link to structured operational records.

| User input | Should become | Why |
| --- | --- | --- |
| Scaffold not available | Blocker/problem | Work cannot continue |
| Permit not issued | Blocker/problem and possible handover item | Permit-to-work is safety/operational readiness issue |
| Isolation not complete | Blocker/problem | Work cannot proceed safely |
| Material missing | Blocker/problem | Physical constraint |
| Crane/lift delayed | Blocker/problem plus action | Recovery ownership needed |
| Quality hold | Blocker/problem plus evidence request | Completion may not be releasable |
| John to follow up by 14:00 | Action | Needs owner and due time |
| Completion photo missing | Evidence gap | Blocks completion confidence |
| Night shift must watch permit expiry | Handover item | Incoming shift needs explicit record |

## Audit events

Minimum audit events for this workflow:

- `task_progress_submitted`;
- `task_progress_supervisor_accepted`;
- `task_progress_correction_requested`;
- `task_progress_rejected`;
- `task_progress_superseded`;
- `export_candidate_created`;
- `export_candidate_approved_for_export`;
- `export_candidate_rejected`;
- `export_candidate_correction_requested`;
- `export_candidate_superseded`;
- `export_preview_created`;
- `export_batch_approved`;
- `export_batch_rejected`;
- `export_file_generated`;
- `export_file_opened_in_microsoft_project`;
- `export_file_verified`.

## Offline rules for progress updates

- Queued is not submitted.
- A locally queued progress update is not visible to supervisors or planners until the server receives it.
- Store local capture time and server received time.
- Use idempotency keys for replay safety.
- Show per-item sync state.
- Failed progress updates must remain visible and retryable.

Required copy:

```text
Saved locally.
Queued on this device. Not yet sent.
Could not send. Still saved on this device.
Server received.
This progress update is not submitted until the server receives it.
Last synced at [time].
```

Use `Thread may be out of date` only for communications/discussion surfaces, not for task progress updates.

## Frontend visual review implications

The current Task Progress Review frontend shell is static/synthetic and not final product IA.

The next visual cleanup should:

- keep console top-level navigation to Today, Tasks, Problems, Evidence, Exports;
- treat Supervisor Review and Planner Review as saved views or sections under Today/Tasks/Exports;
- treat Verification as part of Exports;
- replace reviewer-facing `Synthetic Task A1` style labels with sanitized realistic shutdown examples;
- reduce card/chip density;
- keep write-like controls disabled until APIs exist;
- keep Project-boundary warnings visible.

## Non-goals

This feature must not build:

- production task execution APIs before the backend brief is approved;
- production mobile offline queue;
- generic comments-as-progress workflow;
- direct Microsoft Project write-back;
- scheduler logic;
- generic chat;
- AI progress decisions;
- assignment actuals or work export in MVP;
- broad route/nav expansion.

## User testing questions

Ask planners:

- If you approve this progress row, what do you believe happened to Microsoft Project?
- Which fields are you comfortable exporting: percent complete, actual start, actual finish, or something else?
- What would make you reject a candidate?
- What should happen after a re-import conflict?

Ask supervisors:

- What would make you reject or correct a field completion?
- When should evidence be mandatory?
- When should a progress update become a blocker or handover item?

Ask field users:

- What does `Queued on this device. Not yet sent.` mean?
- What is the fastest acceptable way to submit progress?
- When would you tap Block instead of adding a comment?

Ask shutdown control:

- Which review queues need attention now?
- What should appear on Today and what should stay in drill-down views?

## Acceptance criteria

This product brief is satisfied only if future implementation preserves these truths:

- field progress does not go straight to Microsoft Project;
- supervisor review is not planner export approval;
- planner approval only makes values eligible for export preview;
- export preview does not update the master `.mpp`;
- MSPDI/XML generation does not update the master `.mpp`;
- Project verification is manual and planner-controlled;
- only approved leaf-task percent complete, actual start, and actual finish are MVP export candidates;
- summary-task actuals are not directly exported;
- queued mobile progress is not submitted until the server receives it;
- blockers, actions, evidence, and handover remain structured records;
- no screen implies scheduling logic or hidden Project write-back.
