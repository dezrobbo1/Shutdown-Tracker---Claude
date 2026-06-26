# Communications Layer

The communications layer is not a generic chat product.

Shutdown Tracker should eventually support entity-linked operational discussion where it improves execution review, blocker/action management, handover, export review, and Project verification. It should not become a WhatsApp-style thread, private messenger, or broad channel system.

## Product decision

Build structured operational records first. Add entity-linked Discussion later where it supports those records.

Recommended labels:

- Discussion;
- Operational Updates;
- Mentions;
- Needs Response;
- Announcements;
- Handover Notes;
- Export Review Comments;
- Project Verification Notes.

Avoid labels:

- Chat;
- Messenger;
- Direct Messages;
- Channels;
- Inbox, unless specifically used as `Needs Response` rather than general mail/chat.

## Product boundary

A communication record supports execution decisions. It is not the source of execution state by itself.

Rules:

- A comment is not task progress.
- A comment is not a blocker unless promoted into a blocker/problem.
- A comment is not an action unless promoted into an action.
- A comment is not evidence unless linked to an evidence record.
- A comment is not handover unless flagged or promoted into handover.
- Export review comments do not update Microsoft Project.
- Planner approval after a comment thread does not update the master `.mpp`.
- Project verification notes do not save or update the master `.mpp`.
- Important shutdown information must not disappear into unstructured chat.

## Why generic chat is risky

| Risk | Why it matters | Product control |
| --- | --- | --- |
| Critical decisions buried in text | Blockers, approvals, and handover items become hard to find | Promote to structured record |
| Duplicate WhatsApp plus app communication | Conflicting sources of truth | App records must be structured and auditable |
| Private side channels | Safety, handover, and accountability gaps | Do not build private DMs in MVP |
| Notification overload | Users stop noticing real issues | Use Needs Response and visual queues before push |
| Contractor visibility errors | Restricted information leaks | Role/scope-based visibility |
| Export misunderstanding | Planner comments mistaken for Project update | Persistent Project-boundary copy |
| Offline message confusion | Local-only note mistaken as submitted | Explicit sync states |

## Entity-linked Discussion model

Discussion should attach to an operational object.

| Entity | Purpose | Default stage | Report/audit behaviour |
| --- | --- | --- | --- |
| Task | Clarify progress, evidence gap, blocker context | Later visual shell before backend | Audit-visible if created; not export state |
| Problem / blocker | Discuss recovery, owner, close-out evidence | Later | Audit-visible; linked to problem history |
| Action | Status update, close-out clarification | Later | Audit-visible; action state remains structured |
| Evidence item | Review note or rejection reason | Later | Linked to evidence metadata/audit |
| Handover item | Shift-transfer clarification | Later | Included only if handover item remains open/flagged |
| Export preview line | Planner clarification about old/new value | Later | Export-review audit trail; does not update Project |
| Export batch | Approval/rejection rationale | Later | Export audit trail |
| Project verification step | Manual Project check notes | Later | Verification audit trail; no `.mpp` save implied |
| Critical WP / watchlist | Reporting clarification | Later | Reporting context; no scheduling effect |
| Area / shift / discipline | Context marker for filtering, not a free-form channel | Later | Scoped visibility only |

## Structured record promotion rules

The app should make the structured path easier than dumping important information into comments.

| Message content | Prompt user to create | Reason |
| --- | --- | --- |
| Scaffold not available | Blocker/problem | Work is physically stopped |
| Permit not issued | Blocker/problem | Permit readiness is safety/operational constraint |
| Isolation not complete | Blocker/problem | Work cannot proceed safely |
| Material missing | Blocker/problem | Recovery ownership needed |
| Crane delayed | Blocker/problem plus action | Lift dependency needs owner/time |
| John to follow up by 14:00 | Action | Needs owner and due time |
| Completion photo uploaded | Evidence record/link | Evidence must be metadata-controlled |
| Night shift to watch permit expiry | Handover item | Incoming shift needs explicit record |
| Why was this excluded from export? | Export Review Comment | Planner clarification attached to line/batch |
| Opened XML in Project and checked line 142 | Project Verification Note | Manual verification metadata |

## Communication surfaces

### Console

Entity-linked communication should appear inside existing surfaces:

| Surface | Communication placement |
| --- | --- |
| Today | Needs Response queue and controlled announcements |
| Tasks | Task Discussion inside Task Detail |
| Problems | Problem Discussion inside Problem Detail |
| Actions | Action Update Log inside Action Detail |
| Evidence | Evidence review notes inside Evidence Detail |
| Handover | Handover Notes and flagged items |
| Exports | Export Review Comments and Project Verification Notes |

Do not create a top-level `Chat` area.

### Mobile

Mobile communication should be low-typing and entity-first:

- task comment from Task Detail;
- quick reply to a mention/request;
- mark needs response;
- log blocker from a comment;
- create action from a comment;
- include in handover;
- queued/failed local state visible.

Do not create a dense channel list or private chat inbox.

## Needs Response

Needs Response is a work queue, not a mail inbox.

Items that may appear:

- direct mention on a task/problem/action/export line;
- response requested by supervisor/planner;
- assigned action comment requiring update;
- export review clarification assigned to planner/scheduler;
- handover acknowledgement required;
- failed offline comment/progress submission.

Items that should not appear:

- every comment;
- every status change;
- general channel chatter;
- dashboard updates;
- casual acknowledgements.

## Mentions

Mentions should be constrained:

| Mention type | Decision |
| --- | --- |
| Named user mention | Allowed when user can see the entity |
| Role mention such as `@planner` | Allowed only where role-scope resolution is clear |
| Broad `@everyone` | Do not build for MVP |
| Contractor mention | Scope-restricted and auditable |
| External user mention | Defer until auth/tenant model supports it |

Mentions should create an attention record, not a hidden approval or status change.

## Announcements

Announcements are controlled broadcast records.

Allowed uses:

- shutdown control notice;
- shift-wide safety notice;
- export/report availability notice;
- sync/deployment maintenance notice.

Rules:

- Only authorized roles create announcements.
- Announcements are not open discussion channels.
- Acknowledgement may be requested for critical notices.
- Announcements should not replace handover, blockers, or actions.

## Offline communication rules

Offline comments and replies are allowed only if the UI is explicit.

Required copy:

```text
Queued on this device. Not yet sent.
Could not send. Still saved on this device.
Server received.
Thread may be out of date. Last synced at [time].
```

Use `Thread may be out of date` for discussion threads. Use progress-specific wording for task progress updates.

## Permissions model

| Capability | Default authority |
| --- | --- |
| View task discussion | Same visibility as task |
| Create task discussion comment | Scoped users who can update/participate in task |
| Create problem/action discussion | Users with problem/action visibility |
| Mention user | Users who can see the entity and target user scope |
| Mention role | Supervisor, Coordinator, Shutdown Control, Planner by policy |
| Mark needs response | Supervisor, Coordinator, Shutdown Control, Planner; scoped Field User where assigned |
| Promote comment to blocker | Supervisor, Coordinator, Shutdown Control; Field/Contractor for assigned scope |
| Promote comment to action | Supervisor, Coordinator, Shutdown Control; others request only |
| Flag for handover | Supervisor, Coordinator, Shutdown Control; assigned Field User by policy |
| Comment on export review | Planner and Shutdown Control; others read/request by policy |
| Add Project verification note | Planner only by default |
| Create announcement | Shutdown Control/Admin by default |
| Delete/redact from view | Admin or controlled moderator role; audit retained |
| View audit history | Role/scoped permission only |

Be conservative with contractor visibility. Contractors should not see other contractors' comments unless explicitly permitted by project policy.

## Audit events

Minimum communications audit events:

- `discussion_comment_created`;
- `discussion_comment_edited`;
- `discussion_comment_deleted_from_view`;
- `discussion_comment_promoted_to_problem`;
- `discussion_comment_promoted_to_action`;
- `discussion_comment_flagged_for_handover`;
- `discussion_comment_removed_from_handover`;
- `mention_created`;
- `response_requested`;
- `response_resolved`;
- `announcement_created`;
- `announcement_acknowledged`;
- `export_review_comment_created`;
- `project_verification_note_created`;
- `offline_comment_server_received_later`.

Edited/deleted comments should retain audit history. Delete should mean deleted from ordinary view unless legal/security policy permits hard deletion.

## Visual review scope

A future communications visual shell may include:

- Task Discussion panel;
- Problem / Blocker thread;
- Action Update Log;
- Handover Communication Summary;
- Export Review Comments;
- Project Verification Notes;
- Needs Response queue;
- Mobile Task Discussion;
- Mobile queued comment state;
- Announcement banner mock;
- audit row examples.

These should be visual-only or read-only until production messaging data model and permissions are approved.

## What not to build

Do not build in MVP:

- generic chat;
- private DMs;
- broad channels;
- WhatsApp import/export as source of truth;
- arbitrary chat attachments;
- voice messages;
- read receipts on every message;
- push notification system for every comment;
- AI-generated handover/chat summary;
- external Teams/Slack integration as source of truth.

## Acceptance criteria

Future communications work is acceptable only if:

- users can tell which entity the discussion belongs to;
- users can tell whether a message is only a comment or a structured record;
- blockers/actions/handover/evidence remain structured objects;
- export comments do not imply Microsoft Project update;
- Project verification notes do not imply `.mpp` save;
- queued messages are visibly not sent;
- contractor visibility is scoped;
- no top-level Chat area appears in the MVP IA;
- the feature reduces operational ambiguity rather than adding another message stream.
