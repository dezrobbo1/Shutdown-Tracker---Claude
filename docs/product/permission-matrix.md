# Permission Matrix

Permission levels:

- `yes`: allowed by default for the role within project scope.
- `no`: not allowed by default.
- `own only`: limited to records created by or assigned to the user.
- `assigned only`: limited to assigned work, package, area, contract, or inspection scope.
- `scoped`: allowed inside configured project, area, package, contract, category/responsibility, or watchlist scope.
- `read-only`: view only.
- `request only`: may request or propose, but not approve/finalize.
- `planner only`: reserved for Planner role by default.
- `admin only`: reserved for Admin role by default.

Roles: Admin, Planner, Shutdown Control, Coordinator, Supervisor, Field User, Contractor, Inspector, Viewer / Management.

Project-derived Operational Category membership is not a permission level. It may influence relevance and configured responsibility scope, but write/review/approval authority still requires role plus explicit project responsibility/assignment/delegation.

## Project, Import, and Operational Mapping

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create project | admin only | request only | request only | no | no | no | no | no | no |
| Edit project metadata | admin only | yes | scoped | request only | no | no | no | no | read-only |
| Configure project settings | admin only | scoped | request only | no | no | no | no | no | read-only |
| Upload Microsoft Project source file | scoped | planner only | request only | no | no | no | no | no | no |
| Accept/import Project snapshot | scoped | planner only | request only | no | no | no | no | no | no |
| Review import warnings / Source Catalogue | read-only | yes | yes | scoped | read-only | no | no | no | read-only |
| Reconcile re-import lineage | scoped | planner only | request only | no | no | no | no | no | no |
| Create/edit Operational Category | read-only | planner only | request only | no | no | no | no | no | read-only |
| Map category to Project source | read-only | planner only | request only | no | no | no | no | no | read-only |
| Configure hierarchy/category derivation | read-only | planner only | request only | no | no | no | no | no | read-only |
| Configure value alias / roll-up | read-only | planner only | request only | no | no | no | no | no | read-only |
| Create/version Import Profile | read-only | planner only | request only | no | no | no | no | no | read-only |
| Activate Import Profile / project mapping | scoped | planner only | request only | no | no | no | no | no | read-only |
| Confirm proposed remap after source change | read-only | planner only | request only | no | no | no | no | no | read-only |
| Override mapping validation warning | scoped | planner only | request only | no | no | no | no | no | read-only |
| Configure execution-readiness rule | scoped | planner only | request only | no | no | no | no | no | read-only |
| Use operational Scope / mapped filters | read-only | yes | yes | scoped | scoped | assigned only | assigned only | scoped | read-only |
| Create private Saved View | no | yes | yes | scoped | scoped | own only | own only | scoped | no |
| Create project-shared Saved View | scoped | yes | yes | request only | request only | no | no | request only | no |
| Configure category-based responsibility scope | scoped | yes | yes | request only | no | no | no | no | read-only |
| Configure temporary responsibility delegation | scoped | scoped | yes | scoped | scoped | no | no | no | read-only |
| Manage users | admin only | no | request only | no | no | no | no | no | no |
| Manage roles | admin only | no | request only | no | no | no | no | no | no |
| Manage permissions | admin only | no | request only | no | no | no | no | no | no |

## Tasks and Execution

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View imported tasks | read-only | yes | yes | scoped | scoped | assigned only | assigned only | scoped | read-only |
| View full WBS | read-only | yes | yes | scoped | scoped | no | no | scoped | read-only |
| View assigned/category-relevant tasks | read-only | yes | yes | scoped | yes | assigned only | assigned only | scoped | read-only |
| Start task | no | no | request only | scoped | scoped | assigned only | assigned only | no | no |
| Pause task | no | no | request only | scoped | scoped | assigned only | assigned only | no | no |
| Resume task | no | no | request only | scoped | scoped | assigned only | assigned only | no | no |
| Block task | no | request only | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Complete task | no | no | request only | scoped | scoped | assigned only | assigned only | no | no |
| Reverse completion | no | request only | yes | scoped | scoped | request only | request only | request only | no |
| Submit field update | no | no | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Submit structured progress | no | no | scoped | scoped | scoped | assigned only | assigned only | scoped | no |
| View supervisor review queue | read-only | read-only | yes | scoped | scoped | no | no | scoped | read-only |
| Supervisor accept/correct/reject progress | no | request only | scoped | scoped | yes | no | no | scoped | no |
| Request correction | scoped | yes | yes | yes | yes | own only | own only | scoped | no |
| Approve task completion | no | request only | scoped | scoped | yes | no | no | scoped | no |
| View planner progress review queue | read-only | yes | read-only | read-only | read-only | no | no | no | read-only |
| Planner approve/reject progress for export | no | planner only | request only | request only | request only | no | no | no | no |
| Mark approved for export | no | planner only | request only | request only | request only | no | no | no | no |

## Problems, Actions, Evidence, and Handover

Mapped categories may be inherited/resolved for filtering, scope, reporting, and historical context. That inherited classification does not expand record permissions.

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create problem | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Edit/close problem | scoped | scoped | yes | scoped | scoped | own only | own only | scoped | no |
| Assign/escalate problem | scoped | request only | yes | scoped | scoped | request only | request only | scoped | no |
| Create action | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Assign action | scoped | request only | yes | yes | scoped | no | no | request only | no |
| Complete action | scoped | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Verify/reopen action | scoped | scoped | yes | scoped | scoped | request only | request only | scoped | no |
| Upload/link evidence | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| View scoped evidence | read-only | yes | yes | yes | yes | assigned only | assigned only | yes | read-only |
| Download original evidence | scoped | yes | scoped | scoped | scoped | own only | own only | scoped | scoped |
| Mark evidence superseded | scoped | scoped | yes | scoped | scoped | request only | request only | scoped | no |
| Create/submit handover | no | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Carry over/sign off handover | scoped | scoped | yes | scoped | scoped | no | no | scoped | no |

## Communications Layer

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View entity-linked discussion | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | read-only |
| Create task/problem/action discussion comment | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | no |
| Mention named user | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | no |
| Mention role | scoped | scoped | yes | scoped | scoped | no | no | scoped | no |
| Mark/resolve needs response | scoped | scoped | yes | yes | scoped | own only | own only | scoped | no |
| Promote comment to blocker/action/handover | scoped | scoped | yes | scoped | scoped | request only | request only | scoped | no |
| Comment on export review | scoped | yes | request only | no | no | no | no | no | read-only |
| Add Project verification note | no | planner only | request only | no | no | no | no | no | read-only |
| Create announcement | admin only | request only | yes | request only | request only | no | no | request only | read-only |
| Delete/redact comment from ordinary view | admin only | request only | request only | no | no | no | no | no | no |
| View discussion audit history | scoped | scoped | scoped | scoped | scoped | no | no | scoped | read-only |

## Critical Watchlists / Critical WPs

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create/edit watchlist | scoped | yes | yes | request only | request only | no | no | no | no |
| Archive watchlist | scoped | yes | yes | request only | no | no | no | no | no |
| Select Critical WP from summary task | scoped | yes | yes | request only | request only | no | no | no | no |
| Select multi-summary Critical WP | scoped | yes | yes | request only | request only | no | no | no | no |
| Use mapped category/hierarchy to select or scope Critical Watch | read-only | yes | yes | request only | request only | no | no | no | read-only |
| Change Critical WP source | scoped | yes | yes | request only | no | no | no | no | no |
| Configure reporting policy | scoped | yes | yes | request only | no | no | no | no | read-only |
| Submit Critical Update | no | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Review Critical Update | read-only | yes | yes | scoped | scoped | no | no | scoped | read-only |
| Generate Critical Watch report | read-only | yes | yes | scoped | scoped | no | no | read-only | read-only |

## Export and Approval

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View export preview | read-only | yes | yes | read-only | read-only | no | no | no | read-only |
| View planner progress review | read-only | yes | read-only | read-only | read-only | no | no | no | read-only |
| Approve/reject export candidate | no | planner only | request only | no | no | no | no | no | no |
| Approve/reject export batch | no | planner only | request only | no | no | no | no | no | no |
| Generate MSPDI/XML export | no | planner only | no | no | no | no | no | no | no |
| Mark export manually opened/verified in Project | no | planner only | request only | no | no | no | no | no | read-only |
| Supersede export batch | no | planner only | request only | no | no | no | no | no | read-only |
| View export history | read-only | yes | yes | read-only | read-only | no | no | no | read-only |

## Audit and Security

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View audit log | admin only | scoped | scoped | scoped | scoped | no | no | scoped | read-only |
| Export audit log | admin only | request only | request only | no | no | no | no | no | no |
| View security events | admin only | no | request only | no | no | no | no | no | no |
| View permission changes | admin only | read-only | read-only | no | no | no | no | no | no |
| View mapping/profile audit history | scoped | yes | scoped | read-only | read-only | no | no | no | read-only |
| Update retention policy | admin only | request only | no | no | no | no | no | no | no |

## Boundary Rules

- Project-derived category membership never grants task-update, review, approval, export, or administration authority by itself.
- Field users and contractors cannot approve export batches.
- Planners own Microsoft Project export approval by default.
- Supervisors may approve task completion depending on project policy, but that approval is not the same as export approval.
- Planner approval marks progress as eligible for export preview; it does not update the master `.mpp`.
- Only approved leaf-task progress/actual fields may be export candidates.
- Summary task actuals, watchlists, operational categories, problems, actions, evidence, handover, Critical Updates, communication comments, Needs Response, and announcement states remain inside Shutdown Tracker.
- Operational Mapping must not calculate CPM/float, evaluate Project formulas, resource-level, move dates, or modify Project schedule logic.
- A comment is not task progress, a blocker, an action, evidence, or handover unless explicitly promoted or linked.
- Contractors should see only contractor-scoped discussions and evidence by default.
