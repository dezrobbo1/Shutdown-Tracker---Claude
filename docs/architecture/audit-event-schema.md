# Audit Event Schema

## Purpose

Audit events provide a durable record of security, operational, import/export, approval, offline sync, communications, and reporting activity. They support accountability, incident review, export traceability, evidence handling, and regulatory or contract reporting needs.

## Immutable Audit-Event Rule

Audit events are append-only. An audit event must not be edited or deleted in ordinary application workflows. If an event is wrong or incomplete, create a later correction or supersession event that references the original event.

## Baseline Event Model

| Field | Purpose |
| --- | --- |
| `event_id` | Stable server-generated audit event identifier. |
| `project_id` | Project scope for the event. |
| `event_category` | Broad category such as `task`, `export`, or `permission`. |
| `event_type` | Specific event type such as `task_progress_submitted`. |
| `event_version` | Schema version for this event shape. |
| `occurred_at` | Time the user or system action occurred. |
| `server_received_at` | Time the server accepted the event. |
| `recorded_at` | Time the audit event was written. |
| `actor_user_id` | Authenticated user, if any. |
| `actor_display_name` | Display name at event time for review convenience. |
| `actor_role` | Effective project role at event time. |
| `actor_scope` | Project, package, area, contract, or watchlist scope. |
| `actor_type` | `user`, `service`, `system`, or `integration`. |
| `target_entity_type` | Entity being acted on, such as task, evidence, discussion comment, or export batch. |
| `target_entity_id` | Identifier of the entity being acted on. |
| `target_display_name` | Human-readable target label at event time. |
| `old_value_summary` | Compact summary of prior value or state. |
| `new_value_summary` | Compact summary of new value or state. |
| `reason` | User reason, correction reason, approval note, or system explanation. |
| `client_context` | Device, app, PWA state, IP class, user agent, and app version where appropriate. |
| `source_system` | `shutdown_tracker`, `mobile_pwa`, `master_console`, `project_worker`, or external source label. |
| `correlation_id` | Groups related events across a workflow. |
| `request_id` | Server request identifier. |
| `idempotency_key` | Idempotency key for replay-safe operations. |
| `offline_local_id` | Local queued event identifier from offline workflows. |
| `project_snapshot_id` | Imported project snapshot related to the event, if relevant. |
| `export_batch_id` | Export batch related to the event, if relevant. |
| `evidence_id` | Evidence record related to the event, if relevant. |
| `discussion_thread_id` | Discussion thread related to the event, if relevant. |
| `discussion_message_id` | Discussion/comment/message related to the event, if relevant. |
| `progress_submission_id` | Task progress submission related to the event, if relevant. |
| `progress_candidate_id` | Export candidate related to the event, if relevant. |

## JSON-Like Example

```json
{
  "event_id": "aud_01hzzexample",
  "event_version": 1,
  "project_id": "prj_123",
  "event_category": "task_progress",
  "event_type": "task_progress_submitted",
  "occurred_at": "2026-06-18T08:10:00Z",
  "server_received_at": "2026-06-18T08:12:22Z",
  "recorded_at": "2026-06-18T08:12:23Z",
  "actor": {
    "user_id": "usr_456",
    "display_name": "Field User",
    "role": "Field User",
    "scope": "package:P-100",
    "actor_type": "user"
  },
  "target": {
    "entity_type": "task",
    "entity_id": "task_789",
    "display_name": "Imported leaf task"
  },
  "old_value_summary": {
    "percent_complete": 80
  },
  "new_value_summary": {
    "percent_complete": 100,
    "progress_review_state": "submitted"
  },
  "reason": "Work completed in field",
  "client_context": {
    "app": "mobile_pwa",
    "app_version": "not-implemented-yet",
    "connectivity": "offline_then_synced"
  },
  "source_system": "mobile_pwa",
  "correlation_id": "corr_abc",
  "request_id": "req_def",
  "idempotency_key": "idem_ghi",
  "offline_local_id": "local_jkl",
  "project_snapshot_id": "snap_001",
  "export_batch_id": null,
  "evidence_id": null,
  "discussion_thread_id": null,
  "progress_submission_id": "prog_123"
}
```

## Event Categories

- `auth`
- `permission`
- `project`
- `import`
- `reimport`
- `task`
- `task_progress`
- `problem`
- `action`
- `evidence`
- `communication`
- `mention`
- `announcement`
- `critical_watchlist`
- `critical_update`
- `handover`
- `approval`
- `export`
- `project_verification`
- `offline_sync`
- `system`

## Required Event Types

### Project, Permission, and Import

- `project_created`
- `project_settings_changed`
- `user_role_changed`
- `permission_changed`
- `source_file_uploaded`
- `import_snapshot_created`
- `import_snapshot_accepted`
- `import_snapshot_rejected`
- `import_warning_reviewed`
- `reimport_lineage_matched`
- `reimport_lineage_link_created`
- `reimport_lineage_link_accepted`
- `reimport_lineage_link_rejected`

### Tasks and Execution

- `task_started`
- `task_paused`
- `task_resumed`
- `task_blocked`
- `task_completed`
- `task_completion_reversed`

### Task Progress Review

- `task_progress_submitted`
- `task_progress_supervisor_accepted`
- `task_progress_correction_requested`
- `task_progress_rejected`
- `task_progress_superseded`
- `planner_review_candidate_created`
- `planner_progress_approved_for_export`
- `planner_progress_rejected`
- `progress_export_candidate_blocked`
- `progress_export_candidate_superseded`
- `task_approved_for_export`

### Problems and Actions

- `problem_created`
- `problem_owner_assigned`
- `problem_escalated`
- `problem_closed`
- `problem_reopened`
- `action_created`
- `action_assigned`
- `action_completed`
- `action_verified`
- `action_reopened`

### Evidence, Critical Reporting, and Handover

- `evidence_uploaded`
- `evidence_linked`
- `evidence_unlinked`
- `evidence_superseded`
- `critical_watchlist_created`
- `critical_watchlist_archived`
- `critical_wp_source_added`
- `critical_wp_source_removed`
- `reporting_policy_changed`
- `reporting_period_generated`
- `critical_update_submitted`
- `critical_update_corrected`
- `critical_update_superseded`
- `handover_submitted`
- `handover_item_carried_over`
- `handover_signed_off`

### Communications

- `discussion_comment_created`
- `discussion_comment_edited`
- `discussion_comment_deleted_from_view`
- `discussion_comment_promoted_to_problem`
- `discussion_comment_promoted_to_action`
- `discussion_comment_flagged_for_handover`
- `discussion_comment_removed_from_handover`
- `mention_created`
- `response_requested`
- `response_resolved`
- `announcement_created`
- `announcement_acknowledged`
- `export_review_comment_created`
- `project_verification_note_created`

### Export, Project Verification, and Offline Sync

- `export_preview_created`
- `export_batch_approved`
- `export_batch_rejected`
- `export_file_generated`
- `export_file_opened_in_microsoft_project`
- `export_file_verified`
- `export_file_verification_failed`
- `export_batch_superseded`
- `offline_event_queued`
- `offline_event_synced`
- `offline_event_failed`
- `offline_conflict_created`
- `offline_progress_server_received_later`
- `offline_comment_server_received_later`

## Communications Audit Rules

- Edited comments must preserve prior content in audit or version history.
- Deleted comments should be deleted from ordinary view only; audit records remain.
- Promoting a comment to a blocker/action/handover item creates both a communication event and a target-entity event.
- Export review comments are part of export review history but do not update Microsoft Project.
- Project verification notes are part of manual verification history but do not save or update the master `.mpp`.

## Task Progress Audit Rules

- Field progress submission, supervisor review, planner review, export candidate creation, export preview inclusion, and Project verification are separate audit events.
- Supervisor review and planner export approval must not share one event type.
- Local capture time and server received time must both be preserved when offline updates sync later.
- Re-import conflicts must be visible in audit when they block export eligibility.

## Design Notes

- Audit event storage should be designed before domain tables are implemented.
- The baseline SQL migration now exists in [infra/migrations/V004__audit_events.sql](../../infra/migrations/V004__audit_events.sql).
- The API currently records local-profile audit rows for import snapshot accept/reject decisions, task lineage link create/accept/reject decisions, export preview creation, export batch approval/rejection, generated artifact metadata recording, and manual Project reopen/verification metadata recording. These writes use the existing `audit_events` table and do not add public audit query APIs yet.
- Offline events need both local capture time and server received time.
- Export audit events must identify the export batch and the imported project snapshot.
- Evidence audit events must identify evidence metadata even if the original file is stored in object storage.
- Permission changes must preserve actor, previous role/scope, new role/scope, and reason.
- Communications audit must preserve entity linkage and visibility scope.
- Progress review audit must preserve source update, supervisor review, planner decision, and export candidate identity.
