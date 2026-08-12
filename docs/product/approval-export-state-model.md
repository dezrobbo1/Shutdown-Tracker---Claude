# Approval and Export State Model

Shutdown Tracker is the live execution and reporting authority. Microsoft Project remains the schedule authority. Export to Microsoft Project is controlled, reviewed, approved, and batch-oriented.

This document separates execution, progress review, planner review, export, and sync states. Do not collapse them into one overloaded task status.

A task can be blocked, server received, awaiting planner review, and export blocked at the same time.

## State Dimensions

| Dimension | Purpose | Example |
| --- | --- | --- |
| Execution state | What is happening at the workfront | `blocked` |
| Progress review state | Whether a submitted progress update has been operationally reviewed | `supervisor_accepted` |
| Planner review state | Whether planner has approved export eligibility | `needs_planner_review` |
| Export state | Where an approved candidate or batch is in the Project handoff lifecycle | `in_export_preview` |
| Sync state | Whether a client event has reached the server | `queued_on_device` |

## Execution States

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `not_started` | Imported task has not started. | `ready`, `in_progress`, `blocked`, `superseded` |
| `ready` | Task is available to start or assigned for current work window. | `in_progress`, `blocked`, `superseded` |
| `in_progress` | Work has actively started. | `paused`, `blocked`, `completed`, `superseded` |
| `paused` | Work stopped temporarily without being formally blocked. | `in_progress`, `blocked`, `completed`, `superseded` |
| `blocked` | Work cannot proceed until a blocker/problem is resolved. | `in_progress`, `paused`, `completed`, `superseded` |
| `completed` | Field reports the task as done. | `awaiting_supervisor_review`, `in_progress`, `superseded` |
| `superseded` | A newer execution record now carries the active meaning. | none |

Execution state is operational. It is not automatically exportable to Microsoft Project.

## Progress Review States

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `draft` | Local or server-side draft that has not been submitted for review. | `submitted`, `superseded` |
| `submitted` | User has submitted a progress update. | `needs_supervisor_review`, `correction_requested`, `rejected`, `superseded` |
| `needs_supervisor_review` | Update needs operational validation. | `supervisor_accepted`, `correction_requested`, `rejected`, `superseded` |
| `supervisor_accepted` | Supervisor confirms the update is operationally credible. | `needs_planner_review`, `superseded` |
| `correction_requested` | Reviewer has requested clarification or correction. | `submitted`, `rejected`, `superseded` |
| `rejected` | Update is not accepted. | `superseded` |
| `superseded` | A newer correction or replacement record now carries the active meaning. | none |

Supervisor acceptance is not Microsoft Project export approval.

Required copy:

```text
Supervisor review confirms operational validity. It does not approve Microsoft Project export.
```

## Planner Review States

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `draft` | No planner review is required yet or candidate is not ready. | `needs_planner_review`, `superseded` |
| `needs_planner_review` | Supervisor-accepted progress candidate needs planner decision. | `planner_approved`, `planner_rejected`, `clarification_requested`, `superseded` |
| `clarification_requested` | Planner needs more information before deciding. | `needs_planner_review`, `planner_rejected`, `superseded` |
| `planner_approved` | Planner marks selected values eligible for export preview. | `approved_for_export`, `superseded` |
| `planner_rejected` | Planner decides the candidate should not be exported. | `superseded` |
| `superseded` | A later review/candidate replaces this decision. | none |

Required copy:

```text
Planner approval marks this progress as eligible for export preview. The master .mpp is not updated.
```

## Export Candidate States

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `not_eligible` | Candidate cannot be exported due to task type, field, policy, lineage, blocker, or evidence state. | `eligible`, `superseded` |
| `eligible` | Candidate appears to satisfy export rules but is not approved yet. | `approved_for_export`, `export_blocked`, `superseded` |
| `export_blocked` | Candidate is blocked by evidence, blocker, lineage, summary-task rule, or policy. | `eligible`, `rejected`, `superseded` |
| `approved_for_export` | Planner-approved candidate may be included in export preview. | `in_export_preview`, `superseded` |
| `in_export_preview` | Candidate is included in a draft export preview. | `exported`, `superseded` |
| `exported` | Candidate was included in a generated export artifact. | `superseded` |
| `rejected` | Candidate is not approved for export. | `superseded` |
| `superseded` | A later candidate replaces this one. | none |

## Export Batch States

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `draft_preview` | Preview has been assembled but not submitted for approval. | `awaiting_approval`, `superseded` |
| `awaiting_approval` | Preview is ready for Planner approval. | `approved`, `rejected`, `superseded` |
| `approved` | Export batch has been approved for file generation. | `generated`, `superseded` |
| `rejected` | Export batch is not approved. | `superseded` |
| `generated` | MSPDI/XML export artifact has been generated. | `opened_in_microsoft_project`, `failed`, `superseded` |
| `opened_in_microsoft_project` | Planner has opened the artifact in Microsoft Project for manual verification. | `verified`, `failed`, `superseded` |
| `verified` | Planner has confirmed the artifact opened and behaved as expected in Microsoft Project. | `superseded` |
| `failed` | Generation or manual verification failed. | `superseded` |
| `superseded` | A later export batch replaces this batch for operational purposes. | none |

Required copy sequence:

```text
Draft export preview — master .mpp not updated.
Export batch approved — master .mpp not updated.
MSPDI/XML artifact generated — master .mpp not updated.
Planner must manually open/check the artifact in Microsoft Project.
Verified in Microsoft Project — master .mpp update remains planner-controlled.
```

## Sync States

| State | Meaning | Required copy |
| --- | --- | --- |
| `local_draft` | Saved only on device as a draft. | `Saved locally.` |
| `queued_on_device` | Captured locally and not accepted by the server. | `Queued on this device. Not yet sent.` |
| `sending` | Client is attempting to submit. | `Sending.` |
| `server_received` | Server accepted the event and can make it available for review. | `Server received.` |
| `failed` | Server rejected or operation could not complete. | `Could not send. Still saved on this device.` |
| `conflict` | Server accepted context but cannot apply without review. | `Conflict needs review.` |

Queued is not submitted. A local progress update is not visible to supervisors or planners until the server receives it.

## Implemented Approval Surface

The API records approval decisions on source records through:

- `POST /api/projects/{projectId}/approvals`
- `GET /api/projects/{projectId}/approvals?sourceEntityType=&sourceEntityId=`

A decision carries `sourceEntityType`, `sourceEntityId`, and an `approvalState` of `draft`, `submitted`, `awaiting_review`, `correction_requested`, `approved_for_export`, or `rejected`. `superseded` and `exported` are system-owned and are rejected if requested directly.

Recording a decision supersedes the previous active decision for that source entity rather than editing it, so approval history stays append-only. The reviewer is the authenticated request actor; it is never read from the request body.

This is the gate export preview reads: a preview line is export-eligible only when the latest approval for its source entity is `approved_for_export` and the imported task is a leaf. Approving a source record is not export batch approval, which remains a separate decision on the assembled batch.

Two limits are deliberate and currently unenforced: role-based authority (Planner-only export approval) is not yet enforced, because project-scoped RBAC is not implemented; and there is no `task_update` table yet, so `sourceEntityId` refers to whatever operational record a caller nominates.

## Failure Recording

Terminal failures now move the owning record to `failed` instead of rolling back:

- A failed worker parse moves the import batch to `failed`, records the reason in `parse_summary`, and writes an `import_batch_parse_failed` audit event.
- A failed or mismatched artifact generation moves the export batch to `failed` with `failure_reason` set, and writes an `export_file_generation_failed` audit event.

Failure bookkeeping never masks the original error: if recording the failure itself fails, that exception is attached as suppressed and the original is rethrown.

## Approval Rules

- Field users cannot approve export batches.
- Contractors cannot approve export batches.
- Planners own Microsoft Project export approval by default.
- Supervisors may approve task completion depending on project policy.
- Supervisor completion approval is not the same as planner export approval.
- Shutdown Control may review, reject, request correction, and recommend export decisions, but final export approval is Planner-owned by default.
- Admins may administer permissions but should not be routine export approvers.

## Export Preview Requirements

Every export preview line must show:

- imported task identity;
- imported project snapshot identity;
- leaf-task indicator;
- old value;
- new value;
- source record;
- actor;
- timestamp;
- supervisor review state;
- planner review state;
- export eligibility status;
- reason or comment;
- exclusion reason where applicable;
- whether the value is eligible for Microsoft Project export.

## MVP Export Whitelist

Only these fields may be MVP export candidates, and only on imported leaf tasks:

- `percent_complete`;
- `actual_start`;
- `actual_finish`.

Deferred export fields:

- `physical_percent_complete`, unless site practice proves it is required;
- `remaining_duration`;
- `actual_duration`;
- `actual_work`;
- `remaining_work`;
- assignment actuals.

Never export from Shutdown Tracker:

- summary task actuals;
- planned start/finish;
- dependencies/predecessors;
- constraints;
- calendars;
- baselines;
- WBS/outline structure;
- resource rates, availability, allocation, or levelling data.

## Export Boundaries

- Only planner-approved leaf-task progress/actual fields can be export candidates.
- Summary task actuals must not be exported.
- Watchlists, problems, actions, evidence, handover, Critical Updates, communication comments, Needs Response states, and reporting period states remain inside Shutdown Tracker.
- Critical Work Package due/overdue state does not move Microsoft Project dates.
- Critical Updates do not directly update Microsoft Project.
- Export review comments do not update Microsoft Project.
- Project verification notes do not save the master `.mpp`.
- Exports use MSPDI/XML, not native MPP writing.

## Immutability and Corrections

- Export batches must be immutable once generated.
- Corrections create new records or superseding records, not destructive edits.
- Generated export artifacts should remain linked to the approval record that produced them.
- A failed or superseded export batch must remain visible in export history.
- Manual Microsoft Project verification should be recorded as an audit event.
- Text-only manual reopen evidence should follow [Manual Microsoft Project Round-Trip Evidence](../testing/manual-microsoft-project-round-trip-evidence.md) and must not include generated artifacts, real Project files, screenshots, or claims of automated Project write-back.

## Related product docs

- [Task Progress Review and Export Approval](task-progress-review-export-approval.md)
- [Communications Layer](communications-layer.md)
- [Offline Audit and Sync Rules](offline-audit-sync-rules.md)
- [Correction and Supersession Rules](correction-and-supersession-rules.md)
