# Correction and Supersession Rules

Submitted operational records should generally be corrected by superseding records, not overwritten in place.

## Draft Edits

- Draft records may be edited in place by their owner or an authorized scoped reviewer.
- Draft edits should not imply submitted operational truth.
- Submitting a draft creates an auditable operational record.

## Submitted Update Correction

- Submitted field updates should not be silently overwritten.
- Corrections should create a new correction record or superseding record linked to the original.
- The correction must capture actor, timestamp, reason, old value summary, and new value summary.
- Reviewers may request correction instead of editing a user's submitted update directly.

## Critical Update Correction

- Critical Updates are immutable submitted reports.
- Corrections create `critical_update_corrected` or `critical_update_superseded` audit events.
- Corrected Critical Updates must preserve the original report and show which report is current.
- Field users and contractors may correct their own submitted update only where policy allows.
- Shutdown Control, Planner, Coordinator, Supervisor, or Inspector may correct/review only within scope.

## Task Completion Reversal

- Completion reversal is a distinct auditable action, not an edit to hide the original completion.
- Reversal should require a reason.
- Reversal may return a task to an appropriate prior state such as in progress, blocked, or awaiting review.
- Reversal does not automatically remove an already generated export artifact; export supersession rules apply.

## Problem Reopen

- Reopening a problem creates an audit event.
- The prior closed state, reopen actor, reason, and new owner/escalation state should be visible.
- Reopening does not erase closure history.

## Action Reopen

- Reopening an action creates an audit event.
- The prior completed/verified state, reopen actor, reason, and new due/owner state should be visible.
- Reopening does not erase completion or verification history.

## Evidence Unlink and Supersede

- Evidence should not be silently deleted from operational history.
- Unlinking removes an association but preserves evidence metadata and audit history.
- Superseding marks newer evidence as replacing older evidence for operational use.
- Original file deletion, if ever supported, requires separate retention and legal-hold rules.

## Export Batch Supersession

- Generated export batches are immutable.
- Superseding an export batch creates a new batch and links back to the superseded batch.
- Superseded batches remain visible in export history.
- A batch that was opened or verified in Microsoft Project must retain that verification history.

## Who Can Correct What

| Record type | Owner | Scoped reviewer | Planner | Admin |
| --- | --- | --- | --- | --- |
| Draft field update | yes | scoped | no | no |
| Submitted field update | correction only | request/correct scoped | no | no |
| Task completion | request only | reverse/approve scoped | export review only | no |
| Critical Update | correction only | review/correct scoped | scoped review | no |
| Problem/action | own only | reopen/correct scoped | scoped | no |
| Evidence link | own only | unlink/supersede scoped | scoped | admin override |
| Export batch | no | request only | supersede | no routine role |

## Must Never Be Silently Overwritten

- Submitted field updates.
- Critical Updates.
- Approved task completions.
- Export batch contents.
- Generated export artifacts.
- Audit events.
- Evidence metadata/history.
- Permission changes.
- Microsoft Project source file import records.
