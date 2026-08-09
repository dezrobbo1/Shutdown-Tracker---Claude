# Roles and Capabilities

This baseline defines default product roles before production domain tables, APIs, and UI flows are implemented. Roles are project-scoped unless explicitly stated otherwise.

Authentication identifies the user. Application roles, explicit responsibility/delegation, and project membership decide what the user can do. Project-derived Operational Category membership may influence relevance and responsibility configuration but never grants application authority by itself.

## Role Summary

| Role | Primary purpose | Default landing area | Progress review responsibility | Export authority | Operational Mapping responsibility | Evidence access | Audit sensitivity |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Admin | Manage project setup, users, roles, and security-sensitive configuration | Project setup | Configure access only | No routine export authority by default | Administer access/configuration; inspect mappings; no default planning interpretation authority | All evidence metadata; original files by permission | Highest |
| Planner | Own Project source intake, mapping/profile configuration, snapshot review, planner review, export preview, and controlled export approval | Exports or Today | Decide which reviewed leaf-task progress fields may enter export preview | Approve and generate export batches | Primary mapping/profile authority | Full project evidence needed for export review | Highest |
| Shutdown Control | Coordinate live execution, status review, problems, actions, handover, and reporting | Today | Monitor review queues and request correction/escalation | Review/request export changes; no final export approval by default | Broad use of Scope/categories; collaborate on operational reporting/responsibility configuration | Scoped to full project operations | High |
| Coordinator | Maintain day-to-day execution coordination, problems, actions, and handover flow | Today | Triage area/package review queues | Request only | Use configured categories/Scope/views; no mapping changes by default | Scoped to assigned area or project | High |
| Supervisor | Review field progress, completion, blockers, evidence, and handover | Today or Tasks | Accept/correct/reject field progress for assigned scope | No export batch approval | Use configured categories and responsibility scope; no mapping changes by default | Scoped to assigned work and supervised crews | High |
| Field User | Execute assigned work and submit progress, evidence, problems, and handover notes | My Work | Submit structured progress for assigned tasks | None | Consume mapped relevance only | Own and assigned-scope evidence | Medium |
| Contractor | Submit assigned work updates and evidence under isolation rules | My Work | Submit scoped progress/evidence | None | Consume mapped relevance only | Own contract scope only | Medium |
| Inspector | Review completion/evidence and record inspection outcomes | Today or Problems | Provide quality/evidence review input | None | Consume mapped inspection context only | Scoped to inspection scope | High |
| Viewer / Management | Read operational status, reports, and history | Today | Read review status only | None | Read configured scope/views only | Read-only, scoped | Medium |

## Admin

- Primary purpose: manage project setup, user access, roles, permissions, retention settings, and security-sensitive configuration.
- Can view: project settings, users, role assignments, permission changes, audit logs, evidence metadata, security events, communications audit, and Operational Mapping configuration where authorized.
- Can create/update: projects, user memberships, roles, permissions, retention policy, and configuration-access records.
- Operational Mapping: may administer who can configure mappings and may inspect mapping health/configuration; does not automatically own the planner's interpretation of Project fields, hierarchy, or Resource Groups.
- Must not be burdened with: routine field status entry, planner export review, task-progress decisions, or day-to-day task coordination.
- Export authority: none by default; may grant authority but should not be the routine export approver.
- Evidence access level: all metadata; original file download only when granted for administration or investigation.
- Audit sensitivity: highest. User, permission, security, retention, redaction, mapping-access, and configuration changes must be audited.

## Planner

- Primary purpose: own Microsoft Project source intake, Source Catalogue review, Import Profiles, Operational Categories, mapping validation, import acceptance, re-import lineage, planner progress review, export preview, export approval, MSPDI/XML artifact generation, and manual Project verification metadata.
- Can view: full WBS, imported source metadata, custom fields, hierarchy, resources/assignments, Resource Groups, import warnings, snapshots, mapping health, task lineage, reviewed progress candidates, export candidates, export history, and evidence needed to validate decisions.
- Can create/update: source file uploads, Import Profiles and versions, Operational Categories, source mappings, value aliases/roll-ups, hierarchy mappings, mapping confirmations, import acceptance, re-import reconciliation, planner progress decisions, export previews/approvals, MSPDI/XML generation, and manual verification status.
- Operational Mapping authority: primary role by default. Mapping changes that materially reclassify work must be auditable.
- Must not be burdened with: routine field evidence capture, frontline progress entry, or supervisor operational validation.
- Progress review responsibility: decide whether supervisor-accepted leaf-task values are safe for export preview.
- Export authority: primary role for export approval and generation.
- Critical WP reporting authority: configure Critical WP sources and reporting policies with Shutdown Control; mapped categories may assist selection/scoping but do not calculate critical path.
- Evidence access level: project-scoped access needed for export and schedule-impact review.
- Audit sensitivity: highest. Import/export, mapping/profile, planner review, verification, and schedule-boundary decisions must be audited.

## Shutdown Control

- Primary purpose: maintain live operational awareness and drive execution reporting.
- Can view: full project status, Today, Tasks, Problems, Actions, Evidence, Handover, Critical Updates, configured categories/Scope, review queues, and export previews.
- Can create/update: problems, actions, escalations, reporting periods, Critical Update reviews, correction requests, Needs Response items, controlled announcements, and approved operational responsibility/scope configuration.
- Operational Mapping: broad consumer of mapped categories and Saved Views. May collaborate with Planner on category use, Critical Watch, responsibility scopes, and shared operational views. Source-mapping/profile changes require explicitly granted authority rather than being assumed.
- Export authority: review and request changes; final export approval remains Planner-owned by default.
- Critical WP reporting authority: create/review watchlists, configure reporting policies with Planner, and generate Critical Watch reports.
- Evidence access level: broad project operations scope, subject to sensitive evidence restrictions.
- Audit sensitivity: high. Control-room state changes, responsibility changes, announcements, review escalation, and handover must be auditable.

## Coordinator

- Primary purpose: coordinate execution flow, problem/action follow-up, handover, and area-level reporting.
- Can view: assigned area tasks, configured operational categories/Scope, review queues, problems, actions, evidence, handover, and relevant Critical WPs.
- Can create/update: problems, actions, handover entries, assignments, escalations, correction requests, scoped Needs Response items, and private/scoped Saved Views where policy allows.
- Operational Mapping: use configured categories and Scope; no source-mapping or Import Profile changes by default.
- Export authority: request only.
- Evidence access level: scoped to assigned area, package, or coordination responsibility.
- Audit sensitivity: high for escalations, ownership changes, communications promotions, and handover.

## Supervisor

- Primary purpose: validate field progress, manage blockers, and review completion/evidence for assigned work.
- Can view: assigned work, team tasks, configured responsibility scope, scoped WBS/categories, scoped evidence, problems, actions, handover, relevant Critical Updates, and supervisor review queue.
- Can create/update: task state, field updates, supervisor progress review decisions, blockers, completion review, problems, actions, evidence links, handover entries, and private/scoped Saved Views where policy allows.
- Operational Mapping: category membership may determine relevance and feed an explicit Tracker-owned responsibility scope, but it does not itself grant update/review authority. No source-mapping/Profile changes by default.
- Progress review responsibility: accept, correct, reject, or request evidence for field progress within explicit assigned/responsibility scope.
- Export authority: cannot approve export batches or planner export candidates.
- Evidence access level: scoped to assigned work and supervised crews.
- Audit sensitivity: high for completion approval, blocker status, correction decisions, delegation, and progress review decisions.

## Field User

- Primary purpose: execute assigned work and record real field status.
- Can view: own assigned/delegated tasks, category-relevant work exposed by policy, relevant Today items, own problems/actions, scoped evidence, sync state, and handover prompts.
- Can create/update: task starts, pauses, resumes, blocks, completions, structured progress updates, problems, actions, evidence uploads, and handover notes only where explicit role/assignment/delegation permits.
- Operational Mapping: category/team membership may improve My Work relevance but does not automatically grant write or approval authority over every task in that category.
- Export authority: none.
- Evidence access level: own and assigned-scope evidence.
- Audit sensitivity: medium. Field submissions and offline sync lifecycle must be auditable.

## Contractor

- Primary purpose: submit assigned work updates and evidence with clear isolation from other contractors or areas.
- Can view: own assigned/delegated work, configured relevant category context, required instructions, own updates/evidence, sync state, and discussion within assigned scope.
- Can create/update: assigned task updates, scoped progress submissions, problems, evidence uploads, handover notes, and assigned Critical Updates where allowed.
- Operational Mapping: contractor/work-group classification may assist visibility and responsibility configuration but does not broaden authority beyond explicit contract/assignment scope.
- Export authority: none.
- Evidence access level: own contract scope only.
- Audit sensitivity: medium. Isolation, evidence access, and contractor comments must be auditable.

## Inspector

- Primary purpose: verify completion quality, evidence, and inspection outcomes.
- Can view: inspection scope, mapped operational context, related tasks, submitted evidence, problems, actions, handover details, and discussion where assigned.
- Can create/update: inspection notes, verification outcomes, action requests, evidence review state, and completion review input.
- Operational Mapping: read/use configured context; no mapping/profile administration by default.
- Export authority: none.
- Evidence access level: scoped to inspection responsibility.
- Audit sensitivity: high for verification outcomes and evidence review.

## Viewer / Management

- Primary purpose: read operational status, reports, Saved Views, and history without changing execution records.
- Can view: Today, scoped operational categories/views, reports, export history, Critical Watch reports, review queue summaries, and scoped audit summaries.
- Can create/update: none by default.
- Operational Mapping: read configured views/categories only.
- Export authority: none.
- Evidence access level: read-only and scoped; original downloads only if granted.
- Audit sensitivity: medium. Sensitive report/evidence access should be logged.

## Cross-role rules

- Project-derived classification is not application authorisation.
- Visibility/relevance, operational responsibility, task-update authority, supervisor-review authority, and planner/export authority are separate.
- Responsibility scopes and temporary delegation are Tracker-owned configuration and must be auditable.
- Supervisor review confirms operational validity. It does not approve Microsoft Project export.
- Planner approval marks progress as eligible for export preview. It does not update the master `.mpp`.
- Export batch approval does not update the master `.mpp`.
- Project verification records manual planner action only; saving/updating the master `.mpp` is outside Shutdown Tracker automation.
- Comments and Discussion must not replace structured progress, blockers, actions, evidence, or handover.
- Contractor visibility is conservative by default.
