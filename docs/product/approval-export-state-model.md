# Approval and Export State Model

Shutdown Tracker is the live execution and reporting authority. Microsoft Project remains the schedule authority. Export to Microsoft Project is controlled, reviewed, approved, and batch-oriented.

## Field and Task Update States

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `draft` | Local or server-side draft that has not been submitted for review. | `submitted`, `superseded` |
| `submitted` | User has submitted the update. | `awaiting_review`, `correction_requested`, `rejected`, `superseded` |
| `awaiting_review` | Update is ready for supervisor/control/planner review. | `correction_requested`, `approved_for_export`, `rejected`, `superseded` |
| `correction_requested` | Reviewer has requested clarification or correction. | `submitted`, `rejected`, `superseded` |
| `approved_for_export` | Export-eligible value has passed review. | `exported`, `superseded` |
| `rejected` | Update is not accepted. | `superseded` |
| `superseded` | A newer correction or replacement record now carries the active meaning. | none |
| `exported` | Approved export candidate was included in a generated export batch. | `superseded` |

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
| `superseded` | A later export batch replaces this batch for operational purposes. | none |
| `failed` | Generation or manual verification failed. | `superseded` |

## Approval Rules

- Field users cannot approve export batches.
- Contractors cannot approve export batches.
- Planners own Microsoft Project export approval by default.
- Supervisors may approve task completion depending on project policy.
- Shutdown Control may review, reject, request correction, and recommend export decisions, but final export approval is Planner-owned by default.
- Admins may administer permissions but should not be routine export approvers.

## Export Preview Requirements

Every export preview line must show:

- Imported task identity.
- Imported project snapshot identity.
- Leaf-task indicator.
- Old value.
- New value.
- Source record.
- Actor.
- Timestamp.
- Review/approval status.
- Reason or comment.
- Whether the value is eligible for Microsoft Project export.

## Export Boundaries

- Only leaf-task progress/actual fields can be export candidates.
- Summary task actuals must not be exported.
- Watchlists, problems, actions, evidence, handover, Critical Updates, and reporting period states remain inside Shutdown Tracker.
- Critical Work Package due/overdue state does not move Microsoft Project dates.
- Critical Updates do not directly update Microsoft Project.
- Exports use MSPDI/XML, not native MPP writing.

## Immutability and Corrections

- Export batches must be immutable once generated.
- Corrections create new records or superseding records, not destructive edits.
- Generated export artifacts should remain linked to the approval record that produced them.
- A failed or superseded export batch must remain visible in export history.
- Manual Microsoft Project verification should be recorded as an audit event.
