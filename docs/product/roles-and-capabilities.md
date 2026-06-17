# Roles and Capabilities

This baseline defines default product roles before database tables, APIs, and UI flows are scaffolded. Roles are project-scoped unless explicitly stated otherwise.

Authentication identifies the user. Application roles and project membership decide what the user can do.

## Role Summary

| Role | Primary purpose | Default landing area | Export authority | Critical WP reporting authority | Evidence access | Audit sensitivity |
| --- | --- | --- | --- | --- | --- | --- |
| Admin | Manage project setup, users, roles, and security-sensitive configuration | Project setup | No routine export authority by default | Configure access, not routine reporting | All evidence metadata; original files by permission | Highest |
| Planner | Own schedule import, snapshot review, export preview, and controlled export approval | Exports | Approve and generate export batches | Configure sources and policies with control team | Full project evidence needed for export review | Highest |
| Shutdown Control | Coordinate live execution, status review, problems, actions, and reporting | Today | Review/request export changes; no final export approval by default | Review and generate reports | Scoped to full project operations | High |
| Coordinator | Maintain day-to-day execution coordination, problems, actions, and handover flow | Today | Request only | Submit/review depending on project policy | Scoped to assigned area or project | High |
| Supervisor | Review field progress, completion, blockers, evidence, and handover | Tasks | Approve task completion if policy allows; no export batch approval | Submit and review for assigned scope | Scoped to assigned area/work | High |
| Field User | Execute assigned work and submit field updates, evidence, problems, and handover notes | My Work | None | Submit Critical Updates if assigned | Own and assigned-scope evidence | Medium |
| Contractor | Submit assigned work updates and evidence under isolation rules | My Work | None | Submit Critical Updates only if assigned | Own contract scope only | Medium |
| Inspector | Review completion/evidence and record inspection outcomes | Today | None | Review where assigned | Scoped to inspection scope | High |
| Viewer / Management | Read operational status, reports, and history | Today | None | View reports only | Read-only, scoped | Medium |

## Admin

- Primary purpose: manage project setup, user access, roles, permissions, retention settings, and security-sensitive configuration.
- Can view: project settings, users, role assignments, permission changes, audit logs, evidence metadata, and security events.
- Can create/update: projects, user memberships, roles, permissions, retention policy, and configuration records.
- Must not be burdened with: routine field status entry, planner export review, or day-to-day task coordination.
- Export authority: none by default; may grant authority but should not be the routine export approver.
- Critical WP reporting authority: can grant access and archive stale structures, but routine configuration should sit with Planner and Shutdown Control.
- Evidence access level: all metadata; original file download only when granted for administration or investigation.
- Audit sensitivity: highest. User, permission, security, and retention changes must be audited.

## Planner

- Primary purpose: own Microsoft Project source intake, import acceptance, re-import lineage, export preview, and export approval.
- Can view: full WBS, import warnings, snapshots, export candidates, export history, and evidence needed to validate export decisions.
- Can create/update: source file uploads, import acceptance, re-import reconciliation, export previews, export approvals, MSPDI/XML generation, and manual verification status.
- Must not be burdened with: routine field evidence capture or frontline progress entry.
- Export authority: primary role for export approval and generation.
- Critical WP reporting authority: configure Critical WP sources and reporting policies with Shutdown Control.
- Evidence access level: project-scoped access needed for export and schedule-impact review.
- Audit sensitivity: highest. Import/export and schedule-boundary decisions must be audited.

## Shutdown Control

- Primary purpose: maintain live operational awareness and drive execution reporting.
- Can view: full project status, Today, Tasks, Problems, Actions, Evidence, Handover, Critical Updates, and export previews.
- Can create/update: problems, actions, escalations, reporting periods, Critical Update reviews, and correction requests.
- Must not be burdened with: low-level user administration or direct Microsoft Project export generation unless explicitly assigned.
- Export authority: review and request changes; final export approval remains Planner-owned by default.
- Critical WP reporting authority: create/review watchlists, configure reporting policies with Planner, and generate Critical Watch reports.
- Evidence access level: broad project operations scope, subject to sensitive evidence restrictions.
- Audit sensitivity: high. Control-room state changes and reviews must be auditable.

## Coordinator

- Primary purpose: coordinate execution flow, problem/action follow-up, handover, and area-level reporting.
- Can view: assigned area tasks, problems, actions, evidence, handover, and relevant Critical WPs.
- Can create/update: problems, actions, handover entries, assignments, escalations, and correction requests.
- Must not be burdened with: global configuration, export approval, or security administration.
- Export authority: request only.
- Critical WP reporting authority: submit or review Critical Updates where project policy grants it.
- Evidence access level: scoped to assigned area, package, or coordination responsibility.
- Audit sensitivity: high for escalations, ownership changes, and handover.

## Supervisor

- Primary purpose: validate field progress, manage blockers, and review completion/evidence for assigned work.
- Can view: assigned work, team tasks, scoped WBS, scoped evidence, problems, actions, handover, and relevant Critical Updates.
- Can create/update: task state, field updates, blockers, completion review, problems, actions, evidence links, and handover entries.
- Must not be burdened with: import/export mechanics, global setup, or broad audit administration.
- Export authority: may approve task completion depending on policy; cannot approve export batches.
- Critical WP reporting authority: submit and review Critical Updates for assigned scope where policy allows.
- Evidence access level: scoped to assigned work and supervised crews.
- Audit sensitivity: high for completion approval, blocker status, and correction decisions.

## Field User

- Primary purpose: execute assigned work and record real field status.
- Can view: own assigned tasks, relevant Today items, own problems/actions, scoped evidence, sync state, and handover prompts.
- Can create/update: task starts, pauses, resumes, blocks, completions, field updates, problems, actions, evidence uploads, and handover notes.
- Must not be burdened with: full WBS management, export logic, permissions, or schedule-authority decisions.
- Export authority: none.
- Critical WP reporting authority: submit Critical Updates only when assigned or delegated by policy.
- Evidence access level: own and assigned-scope evidence.
- Audit sensitivity: medium. Field submissions and offline sync lifecycle must be auditable.

## Contractor

- Primary purpose: submit assigned work updates and evidence with clear isolation from other contractors or areas.
- Can view: own assigned work, required instructions, own updates, own evidence, and visible sync state.
- Can create/update: assigned task updates, problems, evidence uploads, handover notes, and assigned Critical Updates where allowed.
- Must not be burdened with: project-wide task visibility, export decisions, internal management reports, or other contractors' data.
- Export authority: none.
- Critical WP reporting authority: submit only when assigned and scoped.
- Evidence access level: own contract scope only.
- Audit sensitivity: medium. Isolation and evidence access must be auditable.

## Inspector

- Primary purpose: verify completion quality, evidence, and inspection outcomes.
- Can view: inspection scope, related tasks, submitted evidence, problems, actions, and handover details.
- Can create/update: inspection notes, verification outcomes, action requests, evidence review state, and completion review input.
- Must not be burdened with: schedule import/export, full project administration, or field progress entry outside inspection duties.
- Export authority: none.
- Critical WP reporting authority: review where assigned; no default source/policy authority.
- Evidence access level: scoped to inspection responsibility.
- Audit sensitivity: high for verification outcomes and evidence review.

## Viewer / Management

- Primary purpose: read operational status, reports, and history without changing execution records.
- Can view: dashboards, reports, export history, Critical Watch reports, and scoped audit summaries.
- Can create/update: none by default.
- Must not be burdened with: data entry, approval queues, or configuration.
- Export authority: none.
- Critical WP reporting authority: view reports only.
- Evidence access level: read-only and scoped; original downloads only if granted.
- Audit sensitivity: medium. Sensitive report and evidence access should be logged.
