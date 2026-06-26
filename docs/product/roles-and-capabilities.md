# Roles and Capabilities

This baseline defines default product roles before production domain tables, APIs, and UI flows are implemented. Roles are project-scoped unless explicitly stated otherwise.

Authentication identifies the user. Application roles and project membership decide what the user can do.

## Role Summary

| Role | Primary purpose | Default landing area | Progress review responsibility | Export authority | Communications responsibility | Evidence access | Audit sensitivity |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Admin | Manage project setup, users, roles, and security-sensitive configuration | Project setup | Configure access only | No routine export authority by default | Configure moderation/retention, not routine discussion | All evidence metadata; original files by permission | Highest |
| Planner | Own schedule import, snapshot review, re-import lineage, planner progress review, export preview, and controlled export approval | Exports or Today | Decide which reviewed leaf-task progress fields may enter export preview | Approve and generate export batches | Export Review Comments and Project Verification Notes | Full project evidence needed for export review | Highest |
| Shutdown Control | Coordinate live execution, status review, problems, actions, handover, and reporting | Today | Monitor review queues and request correction/escalation | Review/request export changes; no final export approval by default | Needs Response, announcements, operational discussion oversight | Scoped to full project operations | High |
| Coordinator | Maintain day-to-day execution coordination, problems, actions, and handover flow | Today | Triage area/package review queues | Request only | Entity-linked discussion for coordination and handover | Scoped to assigned area or project | High |
| Supervisor | Review field progress, completion, blockers, evidence, and handover | Today or Tasks | Accept/correct/reject field progress for assigned scope | May approve task completion if policy allows; no export batch approval | Task/problem/action discussion for assigned work | Scoped to assigned work and supervised crews | High |
| Field User | Execute assigned work and submit progress, evidence, problems, and handover notes | My Work | Submit structured progress for assigned tasks | None | Assigned task/problem comments only; no general chat authority | Own and assigned-scope evidence | Medium |
| Contractor | Submit assigned work updates and evidence under isolation rules | My Work | Submit scoped progress/evidence | None | Own assigned-scope discussion only | Own contract scope only | Medium |
| Inspector | Review completion/evidence and record inspection outcomes | Today or Problems | Provide quality/evidence review input | None | Evidence/problem/action discussion where assigned | Scoped to inspection scope | High |
| Viewer / Management | Read operational status, reports, and history | Today | Read review status only | None | Read-only discussion/report visibility where scoped | Read-only, scoped | Medium |

## Admin

- Primary purpose: manage project setup, user access, roles, permissions, retention settings, and security-sensitive configuration.
- Can view: project settings, users, role assignments, permission changes, audit logs, evidence metadata, security events, and communications audit where authorized.
- Can create/update: projects, user memberships, roles, permissions, retention policy, and configuration records.
- Must not be burdened with: routine field status entry, planner export review, task-progress decisions, or day-to-day task coordination.
- Export authority: none by default; may grant authority but should not be the routine export approver.
- Communications authority: configure retention/moderation and redact from ordinary view by policy; should not use private-message style workflows.
- Evidence access level: all metadata; original file download only when granted for administration or investigation.
- Audit sensitivity: highest. User, permission, security, retention, redaction, and configuration changes must be audited.

## Planner

- Primary purpose: own Microsoft Project source intake, import acceptance, re-import lineage, planner progress review, export preview, export approval, MSPDI/XML artifact generation, and manual Project verification metadata.
- Can view: full WBS, import warnings, snapshots, task lineage, reviewed progress candidates, export candidates, export history, and evidence needed to validate export decisions.
- Can create/update: source file uploads, import acceptance, re-import reconciliation, planner progress decisions, export previews, export approvals, MSPDI/XML generation, and manual verification status.
- Must not be burdened with: routine field evidence capture, frontline progress entry, or supervisor operational validation.
- Progress review responsibility: decide whether supervisor-accepted leaf-task values are safe for export preview.
- Export authority: primary role for export approval and generation.
- Communications responsibility: export review comments and Project verification notes; comments do not update Project.
- Critical WP reporting authority: configure Critical WP sources and reporting policies with Shutdown Control.
- Evidence access level: project-scoped access needed for export and schedule-impact review.
- Audit sensitivity: highest. Import/export, planner review, verification, and schedule-boundary decisions must be audited.

## Shutdown Control

- Primary purpose: maintain live operational awareness and drive execution reporting.
- Can view: full project status, Today, Tasks, Problems, Actions, Evidence, Handover, Critical Updates, review queues, and export previews.
- Can create/update: problems, actions, escalations, reporting periods, Critical Update reviews, correction requests, Needs Response items, and controlled announcements.
- Must not be burdened with: low-level user administration or direct Microsoft Project export generation unless explicitly assigned.
- Progress review responsibility: monitor review queues, escalate stale supervisor/planner items, and request correction.
- Export authority: review and request changes; final export approval remains Planner-owned by default.
- Communications responsibility: control-room operational discussion oversight, Needs Response, and controlled announcements.
- Critical WP reporting authority: create/review watchlists, configure reporting policies with Planner, and generate Critical Watch reports.
- Evidence access level: broad project operations scope, subject to sensitive evidence restrictions.
- Audit sensitivity: high. Control-room state changes, announcements, review escalation, and handover must be auditable.

## Coordinator

- Primary purpose: coordinate execution flow, problem/action follow-up, handover, and area-level reporting.
- Can view: assigned area tasks, review queues, problems, actions, evidence, handover, and relevant Critical WPs.
- Can create/update: problems, actions, handover entries, assignments, escalations, correction requests, and scoped Needs Response items.
- Must not be burdened with: global configuration, export approval, or security administration.
- Progress review responsibility: triage area/package progress review queues and follow up missing evidence/blockers.
- Export authority: request only.
- Communications responsibility: entity-linked discussion on assigned area/task/problem/action/handover items.
- Critical WP reporting authority: submit or review Critical Updates where project policy grants it.
- Evidence access level: scoped to assigned area, package, or coordination responsibility.
- Audit sensitivity: high for escalations, ownership changes, communications promotions, and handover.

## Supervisor

- Primary purpose: validate field progress, manage blockers, and review completion/evidence for assigned work.
- Can view: assigned work, team tasks, scoped WBS, scoped evidence, problems, actions, handover, relevant Critical Updates, and supervisor review queue.
- Can create/update: task state, field updates, supervisor progress review decisions, blockers, completion review, problems, actions, evidence links, and handover entries.
- Must not be burdened with: import/export mechanics, global setup, broad audit administration, or planner-only export decisions.
- Progress review responsibility: accept, correct, reject, or request evidence for field progress.
- Export authority: may approve task completion depending on policy; cannot approve export batches or planner export candidates.
- Communications responsibility: assigned-scope task/problem/action discussion, Needs Response, and handover flags.
- Critical WP reporting authority: submit and review Critical Updates for assigned scope where policy allows.
- Evidence access level: scoped to assigned work and supervised crews.
- Audit sensitivity: high for completion approval, blocker status, correction decisions, and progress review decisions.

## Field User

- Primary purpose: execute assigned work and record real field status.
- Can view: own assigned tasks, relevant Today items, own problems/actions, scoped evidence, sync state, and handover prompts.
- Can create/update: task starts, pauses, resumes, blocks, completions, structured progress updates, problems, actions, evidence uploads, and handover notes.
- Must not be burdened with: full WBS management, export logic, permissions, planner review, or schedule-authority decisions.
- Progress review responsibility: submit structured progress; respond to correction/evidence requests.
- Export authority: none.
- Communications responsibility: assigned task/problem/action comments only; comments must not replace progress, blockers, or handover.
- Critical WP reporting authority: submit Critical Updates only when assigned or delegated by policy.
- Evidence access level: own and assigned-scope evidence.
- Audit sensitivity: medium. Field submissions and offline sync lifecycle must be auditable.

## Contractor

- Primary purpose: submit assigned work updates and evidence with clear isolation from other contractors or areas.
- Can view: own assigned work, required instructions, own updates, own evidence, own visible sync state, and discussion within assigned scope.
- Can create/update: assigned task updates, scoped progress submissions, problems, evidence uploads, handover notes, and assigned Critical Updates where allowed.
- Must not be burdened with: project-wide task visibility, export decisions, internal management reports, other contractors' data, or broad communications.
- Progress review responsibility: submit scoped progress and evidence; respond to correction/evidence requests.
- Export authority: none.
- Communications responsibility: own assigned-scope discussion only; no private side-channel as source of truth.
- Critical WP reporting authority: submit only when assigned and scoped.
- Evidence access level: own contract scope only.
- Audit sensitivity: medium. Isolation, evidence access, and contractor comments must be auditable.

## Inspector

- Primary purpose: verify completion quality, evidence, and inspection outcomes.
- Can view: inspection scope, related tasks, submitted evidence, problems, actions, handover details, and discussion where assigned.
- Can create/update: inspection notes, verification outcomes, action requests, evidence review state, and completion review input.
- Must not be burdened with: schedule import/export, full project administration, field progress entry outside inspection duties, or general chat.
- Progress review responsibility: provide quality/evidence review input where assigned.
- Export authority: none.
- Communications responsibility: evidence/problem/action notes where inspection input is required.
- Critical WP reporting authority: review where assigned; no default source/policy authority.
- Evidence access level: scoped to inspection responsibility.
- Audit sensitivity: high for verification outcomes and evidence review.

## Viewer / Management

- Primary purpose: read operational status, reports, and history without changing execution records.
- Can view: Today, reports, export history, Critical Watch reports, review queue summaries, and scoped audit summaries.
- Can create/update: none by default.
- Must not be burdened with: data entry, approval queues, configuration, or write controls.
- Progress review responsibility: read-only status only.
- Export authority: none.
- Communications responsibility: read-only unless a specific limited-comment/report role is later approved.
- Critical WP reporting authority: view reports only.
- Evidence access level: read-only and scoped; original downloads only if granted.
- Audit sensitivity: medium. Sensitive report, evidence, and discussion access should be logged.

## Cross-role rules

- Supervisor review confirms operational validity. It does not approve Microsoft Project export.
- Planner approval marks progress as eligible for export preview. It does not update the master `.mpp`.
- Export batch approval does not update the master `.mpp`.
- Project verification records manual planner action only; saving/updating the master `.mpp` is outside Shutdown Tracker automation.
- Comments and Discussion must not replace structured progress, blockers, actions, evidence, or handover.
- Contractor visibility is conservative by default.
