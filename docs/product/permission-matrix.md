# Permission Matrix

Permission levels:

- `yes`: allowed by default for the role within project scope.
- `no`: not allowed by default.
- `own only`: limited to records created by or assigned to the user.
- `assigned only`: limited to assigned work, package, area, contract, or inspection scope.
- `scoped`: allowed inside configured project, area, package, contract, or watchlist scope.
- `read-only`: view only.
- `request only`: may request or propose, but not approve/finalize.
- `planner only`: reserved for Planner role by default.
- `admin only`: reserved for Admin role by default.

Roles: Admin, Planner, Shutdown Control, Coordinator, Supervisor, Field User, Contractor, Inspector, Viewer / Management.

## Project and Setup

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create project | admin only | request only | request only | no | no | no | no | no | no |
| Edit project metadata | admin only | yes | scoped | request only | no | no | no | no | read-only |
| Configure project settings | admin only | scoped | request only | no | no | no | no | no | read-only |
| Upload Microsoft Project source file | scoped | planner only | request only | no | no | no | no | no | no |
| Accept/import Project snapshot | scoped | planner only | request only | no | no | no | no | no | no |
| Review import warnings | read-only | yes | yes | scoped | read-only | no | no | no | read-only |
| Reconcile re-import lineage | scoped | planner only | request only | no | no | no | no | no | no |
| Manage users | admin only | no | request only | no | no | no | no | no | no |
| Manage roles | admin only | no | request only | no | no | no | no | no | no |
| Manage permissions | admin only | no | request only | no | no | no | no | no | no |

## Tasks and Execution

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View imported tasks | read-only | yes | yes | scoped | scoped | assigned only | assigned only | scoped | read-only |
| View full WBS | read-only | yes | yes | scoped | scoped | no | no | scoped | read-only |
| View assigned tasks | read-only | yes | yes | scoped | yes | assigned only | assigned only | scoped | read-only |
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

## Problems and Actions

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create problem | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Edit problem | scoped | scoped | yes | scoped | scoped | own only | own only | scoped | no |
| Assign problem owner | scoped | request only | yes | scoped | scoped | no | no | request only | no |
| Escalate problem | scoped | scoped | yes | yes | scoped | request only | request only | scoped | no |
| Close problem | scoped | scoped | yes | scoped | scoped | no | no | scoped | no |
| Create action | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Assign action | scoped | request only | yes | yes | scoped | no | no | request only | no |
| Complete action | scoped | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Verify/close action | scoped | scoped | yes | scoped | scoped | no | no | scoped | no |
| Reopen problem/action | scoped | scoped | yes | scoped | scoped | request only | request only | scoped | no |

## Evidence

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Upload evidence | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Link evidence | scoped | scoped | yes | scoped | scoped | own only | own only | scoped | no |
| Unlink evidence | scoped | request only | yes | scoped | scoped | own only | own only | scoped | no |
| View all evidence | scoped | yes | yes | scoped | scoped | no | no | scoped | read-only |
| View scoped evidence | read-only | yes | yes | yes | yes | assigned only | assigned only | yes | read-only |
| Delete evidence metadata | admin only | request only | request only | no | no | no | no | no | no |
| Mark evidence superseded | scoped | scoped | yes | scoped | scoped | request only | request only | scoped | no |
| Download original evidence | scoped | yes | scoped | scoped | scoped | own only | own only | scoped | scoped |
| View evidence audit history | scoped | yes | yes | scoped | scoped | no | no | scoped | read-only |

## Communications Layer

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View entity-linked discussion | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | read-only |
| Create task discussion comment | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | no |
| Create problem/action discussion comment | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | no |
| Mention named user | scoped | scoped | scoped | scoped | scoped | assigned only | assigned only | scoped | no |
| Mention role | scoped | scoped | yes | scoped | scoped | no | no | scoped | no |
| Mark needs response | scoped | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Resolve needs response | scoped | scoped | yes | yes | scoped | own only | own only | scoped | no |
| Promote comment to blocker | scoped | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Promote comment to action | scoped | scoped | yes | scoped | scoped | request only | request only | scoped | no |
| Flag comment for handover | scoped | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Comment on export review | scoped | yes | request only | no | no | no | no | no | read-only |
| Add Project verification note | no | planner only | request only | no | no | no | no | no | read-only |
| Create announcement | admin only | request only | yes | request only | request only | no | no | request only | read-only |
| Delete/redact comment from ordinary view | admin only | request only | request only | no | no | no | no | no | no |
| View discussion audit history | scoped | scoped | scoped | scoped | scoped | no | no | scoped | read-only |
| View contractor-restricted discussion | scoped | scoped | scoped | scoped | scoped | no | own contract only | scoped | no |

## Critical Watchlists / Critical WPs

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create watchlist | scoped | yes | yes | request only | request only | no | no | no | no |
| Edit watchlist | scoped | yes | yes | scoped | request only | no | no | no | no |
| Archive watchlist | scoped | yes | yes | request only | no | no | no | no | no |
| Select Critical WP from summary task | scoped | yes | yes | request only | request only | no | no | no | no |
| Select multi-summary Critical WP | scoped | yes | yes | request only | request only | no | no | no | no |
| Change Critical WP source | scoped | yes | yes | request only | no | no | no | no | no |
| Configure reporting policy | scoped | yes | yes | request only | no | no | no | no | read-only |
| Change reporting policy mid-shutdown | scoped | yes | yes | request only | no | no | no | no | read-only |
| Submit Critical Update | no | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Correct submitted Critical Update | no | scoped | yes | scoped | scoped | own only | own only | scoped | no |
| Review Critical Update | read-only | yes | yes | scoped | scoped | no | no | scoped | read-only |
| Generate Critical Watch report | read-only | yes | yes | scoped | scoped | no | no | read-only | read-only |

## Handover

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Create handover entry | no | scoped | yes | yes | scoped | assigned only | assigned only | scoped | no |
| Edit draft handover | no | scoped | yes | scoped | scoped | own only | own only | scoped | no |
| Submit handover | no | scoped | yes | scoped | scoped | assigned only | assigned only | scoped | no |
| Carry over handover item | scoped | scoped | yes | scoped | scoped | no | no | scoped | no |
| Sign off handover | no | scoped | yes | scoped | scoped | no | no | scoped | no |
| Generate handover report | read-only | yes | yes | scoped | scoped | no | no | read-only | read-only |

## Export and Approval

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View export preview | read-only | yes | yes | read-only | read-only | no | no | no | read-only |
| View planner progress review | read-only | yes | read-only | read-only | read-only | no | no | no | read-only |
| Approve export candidate | no | planner only | request only | no | no | no | no | no | no |
| Reject export candidate | no | planner only | request only | no | no | no | no | no | no |
| Approve export batch | no | planner only | request only | no | no | no | no | no | no |
| Reject export batch | no | planner only | request only | no | no | no | no | no | no |
| Generate MSPDI/XML export | no | planner only | no | no | no | no | no | no | no |
| Mark export as manually opened in Microsoft Project | no | planner only | request only | no | no | no | no | no | read-only |
| Mark export as manually verified in Microsoft Project | no | planner only | request only | no | no | no | no | no | read-only |
| Supersede export batch | no | planner only | request only | no | no | no | no | no | read-only |
| View export history | read-only | yes | yes | read-only | read-only | no | no | no | read-only |

## Audit and Security

| Capability | Admin | Planner | Shutdown Control | Coordinator | Supervisor | Field User | Contractor | Inspector | Viewer / Management |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| View audit log | admin only | scoped | scoped | scoped | scoped | no | no | scoped | read-only |
| Export audit log | admin only | request only | request only | no | no | no | no | no | no |
| View security events | admin only | no | request only | no | no | no | no | no | no |
| View permission changes | admin only | read-only | read-only | no | no | no | no | no | no |
| Update retention policy | admin only | request only | no | no | no | no | no | no | no |

## Boundary Rules

- Field users and contractors cannot approve export batches.
- Planners own Microsoft Project export approval by default.
- Supervisors may approve task completion depending on project policy, but that approval is not the same as export approval.
- Planner approval marks progress as eligible for export preview; it does not update the master `.mpp`.
- Only approved leaf-task progress/actual fields may be export candidates.
- Summary task actuals, watchlists, problems, actions, evidence, handover, Critical Updates, communication comments, Needs Response, and announcement states remain inside Shutdown Tracker.
- A comment is not task progress, a blocker, an action, evidence, or handover unless explicitly promoted or linked.
- Contractors should see only contractor-scoped discussions and evidence by default.
