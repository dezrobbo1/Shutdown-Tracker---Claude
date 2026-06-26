# Offline Audit and Sync Rules

Offline-capable mobile workflows must preserve user trust, auditability, and replay safety. Background Sync is progressive enhancement only, not the correctness backbone.

## Core Rules

- Queued is not submitted.
- A queued local event becomes submitted only after the server accepts it.
- The UI must show user-visible sync state.
- Every queued operation needs an idempotency key.
- Offline capture time and server received time must both be preserved.
- Server-side audit events must record offline sync lifecycle events.
- Failed local work must remain visible and retryable.
- Field users must not be asked to resolve complex re-import or export conflicts on mobile.

## Required Offline Fields

| Field | Purpose |
| --- | --- |
| `offline_local_id` | Local identifier for the queued operation. |
| `idempotency_key` | Replay-safe operation key. |
| `local_capture_time` | Device time when the user captured the event. |
| `server_received_time` | Server time when the event was accepted. |
| `due_time` | Reporting or task due time, if relevant. |
| `late_reason` | User/system reason when the event arrives late. |
| `sync_state` | `local_draft`, `queued_on_device`, `sending`, `server_received`, `failed`, or `conflict`. |
| `conflict_state` | Details of server conflict, if any. |
| `client_context` | App, device, connectivity, and app version context. |

## Sync States

| State | Meaning | Required UI copy |
| --- | --- | --- |
| `local_draft` | Saved locally and not queued for server submission yet. | `Saved locally.` |
| `queued_on_device` | Captured locally and not accepted by the server. | `Queued on this device. Not yet sent.` |
| `sending` | Client is attempting to submit to the server. | `Sending.` |
| `server_received` | Server accepted the event and recorded authoritative state or review input. | `Server received.` |
| `failed` | Server rejected or the operation could not be completed. | `Could not send. Still saved on this device.` |
| `conflict` | Server accepted the request context but cannot apply it without resolution. | `Conflict needs review.` |

For task progress surfaces, also use:

```text
This progress update is not submitted until the server receives it.
Last synced at [time].
```

For communications/discussion surfaces only, use:

```text
Thread may be out of date. Last synced at [time].
```

Do not use `Thread may be out of date` for task progress screens.

## Task Progress Offline Rules

- A local progress update is not available for supervisor review until the server receives it.
- A supervisor cannot accept a local-only progress update from another user's device.
- A planner cannot approve an export candidate based on local-only progress.
- If a progress update is captured against an old snapshot and a re-import occurs before sync, mark the record as a conflict or stale candidate after server receipt.
- The server should preserve local capture time and server received time.
- Late arrival should be visible to supervisors/planners if due time matters.
- Duplicate submissions must be prevented with idempotency keys.

## Communications Offline Rules

- A local discussion comment is not visible to others until server received.
- A mention is not delivered until server received.
- A Needs Response item is not created until server accepted.
- Offline comments should show queued/failed/server received state on the entity thread.
- Failed comments remain saved on device and retryable.
- Discussion threads may show `Thread may be out of date. Last synced at [time].`

## Evidence Upload States

Evidence may have separate metadata and binary upload states:

- metadata saved locally;
- metadata queued locally;
- file pending upload;
- file upload in progress;
- file uploaded;
- metadata server received;
- evidence linked;
- upload failed;
- evidence superseded.

The UI must not imply evidence is available to reviewers until the server has accepted metadata and the file is available through object storage or a controlled retry path.

Required evidence copy examples:

```text
Evidence saved locally.
Evidence upload pending.
Evidence upload failed. Still saved on this device.
Evidence metadata server received.
```

## Audit Events

Offline queue lifecycle should produce audit events such as:

- `offline_event_queued`;
- `offline_event_synced`;
- `offline_event_failed`;
- `offline_conflict_created`;
- `offline_progress_server_received_later`;
- `offline_comment_server_received_later`.

Domain events, such as `task_progress_submitted`, `task_completed`, `critical_update_submitted`, or `discussion_comment_created`, should be recorded when the server accepts the synced operation.

## Reporting Period Expiry While Offline

- If a reporting period expires while the user is offline, the queued update may still be submitted with its local capture time.
- The server should mark whether the update was captured before or after due time.
- Late arrival should be visible and auditable.
- Project policy decides whether late updates require review, correction, or exception approval.
- Do not fake on-time submission by relying only on local capture time.

## Background Sync

Background Sync may improve convenience where browsers support it. It must not be required for correctness because support varies by browser, device policy, and operating environment.

The app must also sync on:

- foreground resume;
- explicit user retry;
- ordinary network recovery;
- manual retry-all from Sync screen.

## Visual Requirements

- Mobile My Work should show a compact sync banner, not large diagnostic tiles.
- Sync screen should show queued, failed, server received, and conflict items grouped by state.
- Console Today should show sync health and failed/queued counts without becoming a dashboard wall.
- All sync states must be text-visible and not colour-only.
- High-impact failed states should include a recovery action or next step.
