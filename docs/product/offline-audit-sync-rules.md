# Offline Audit and Sync Rules

Offline-capable mobile workflows must preserve user trust, auditability, and replay safety. Background Sync is progressive enhancement only, not the correctness backbone.

## Core Rules

- Queued is not submitted.
- A queued local event becomes submitted only after the server accepts it.
- The UI must show user-visible sync state.
- Every queued operation needs an idempotency key.
- Offline capture time and server received time must both be preserved.
- Server-side audit events must record offline sync lifecycle events.

## Required Offline Fields

| Field | Purpose |
| --- | --- |
| `offline_local_id` | Local identifier for the queued operation. |
| `idempotency_key` | Replay-safe operation key. |
| `local_capture_time` | Device time when the user captured the event. |
| `server_received_time` | Server time when the event was accepted. |
| `due_time` | Reporting or task due time, if relevant. |
| `late_reason` | User/system reason when the event arrives late. |
| `sync_state` | `queued`, `syncing`, `synced`, `failed`, or `conflict`. |
| `conflict_state` | Details of server conflict, if any. |
| `client_context` | App, device, connectivity, and app version context. |

## Sync States

| State | Meaning |
| --- | --- |
| `queued` | Captured locally and not accepted by the server. |
| `syncing` | Client is attempting to submit to the server. |
| `synced` | Server accepted the event and recorded authoritative state. |
| `failed` | Server rejected or the operation could not be completed. |
| `conflict` | Server accepted the request context but cannot apply it without resolution. |

## Audit Events

Offline queue lifecycle should produce audit events such as:

- `offline_event_queued`
- `offline_event_synced`
- `offline_event_failed`
- `offline_conflict_created`

Domain events, such as `critical_update_submitted` or `task_completed`, should be recorded when the server accepts the synced operation.

## Reporting Period Expiry While Offline

- If a reporting period expires while the user is offline, the queued update may still be submitted with its local capture time.
- The server should mark whether the update was captured before or after due time.
- Late arrival should be visible and auditable.
- Project policy decides whether late updates require review, correction, or exception approval.

## Evidence Upload States

Evidence may have separate metadata and binary upload states:

- Metadata queued locally.
- File pending upload.
- File upload in progress.
- File uploaded.
- Metadata synced.
- Evidence linked.
- Upload failed.
- Evidence superseded.

The UI must not imply evidence is available to reviewers until the server has accepted metadata and the file is available through object storage or a controlled retry path.

## Background Sync

Background Sync may improve convenience where browsers support it. It must not be required for correctness because support varies by browser, device policy, and operating environment. The app must also sync on foreground resume, explicit user retry, and ordinary network recovery.
