# Design Language and Status Semantics

Shutdown Tracker should use a restrained operational design language.

The product is used during industrial shutdown execution. Visual certainty, state clarity, and low cognitive load are more important than visual novelty.

## Design principles

| Principle | Rule |
| --- | --- |
| Operational clarity | Every screen should help users decide what to do next |
| Restraint | Use fewer colours, fewer chips, fewer panels, and less decorative chrome |
| Consistency | The same state must look and read the same across console and mobile |
| Accessibility | Never rely on colour alone; maintain focus, text, and target-size rules |
| Role fit | Field screens are sparse; planner/control screens can be denser but still structured |
| Project boundary visibility | Export/review/verification screens must prevent `.mpp` misunderstanding |

## Typography and copy

Use:

- sentence case labels;
- short operational headings;
- plain verbs;
- concrete state language;
- system UI font stack;
- compact page headers.

Avoid:

- marketing-style hero headings;
- all-caps long labels;
- generic SaaS phrases;
- decorative adjectives;
- vague collaboration/productivity claims;
- repeating prototype labels on every card.

Examples:

| Avoid | Use |
| --- | --- |
| Execution review console | Execution review |
| Field shell | My work |
| Visual state only | Visual review shell. Static/synthetic data. No production write workflow. |
| Thread may be out of date | Last synced at [time] for task/progress surfaces |
| Real-time collaboration | Needs response / assigned action / blocker owner |

## Layout rules

### Console

- Use a compact top status strip for project/shift/import/export/sync context.
- Use Today for attention queues and exceptions.
- Use Tasks for browsing and task detail.
- Use Problems for blockers/actions/delays/holds.
- Use Evidence for file/photo review.
- Use Exports for planner review, export preview, and Project verification.
- Use tables/lists for comparison and review.
- Use cards only for summary/attention blocks or mobile-style surfaces.
- Use drawers or detail pages for deep record detail.
- Avoid horizontal overflow in default layouts.

### Mobile

- My Work is the default landing view.
- Show assigned work before diagnostics.
- Use a compact sync banner.
- Use cards for work items.
- Keep forms short.
- Keep primary actions thumb-friendly.
- Put evidence and problem shortcuts near task actions.
- Push history and review metadata into detail screens.

## Status classes

Use a small semantic palette. Do not create a new colour for every state.

| Class | Use | Examples |
| --- | --- | --- |
| Neutral | Context, unavailable, no issue, archived | Not started, No blocker, Context only |
| Info | Active but not urgent | In progress, Server received, Review context |
| Warning | Needs attention but not critical failure | Needs supervisor review, Needs planner review, Queued, Evidence pending, Paused |
| Critical | Work blocked, failed, unsafe, conflict, export blocked | Blocked, Failed, Conflict, Evidence missing, Export blocked, Re-import conflict |
| Success | Accepted, approved, complete, verified | Supervisor accepted, Planner approved, Verified, Completed |
| Restricted | Read-only or policy-blocked state | Summary task not export eligible, Contractor restricted, Planner only |

## Domain state mapping

### Execution state

| State | Class | Notes |
| --- | --- | --- |
| Not started | Neutral | Imported but not active |
| Ready | Info | Available to start |
| In progress | Info | Active work |
| Paused | Warning | Temporary stop; reason required |
| Blocked | Critical | Structured blocker required |
| Completed | Success | Field completion; may still need review/evidence |

### Progress review state

| State | Class | Notes |
| --- | --- | --- |
| Draft | Neutral | Not submitted |
| Submitted | Info | Server received, review pending |
| Needs supervisor review | Warning | Supervisor action required |
| Supervisor accepted | Success | Operationally valid, not export approval |
| Correction requested | Warning | User/supervisor action needed |
| Rejected | Critical | Not accepted |
| Superseded | Neutral | Replaced by newer record |

### Planner review state

| State | Class | Notes |
| --- | --- | --- |
| Needs planner review | Warning | Planner decision required |
| Planner approved | Success | Eligible for export preview only |
| Planner rejected | Critical | Not exportable |

### Export state

| State | Class | Notes |
| --- | --- | --- |
| Not eligible | Restricted | Policy or task type blocks export |
| Eligible | Info | Candidate may be reviewed |
| Export blocked | Critical | Blocker/evidence/lineage/policy issue |
| Approved for export | Success | Planner-approved candidate |
| In export preview | Info | Draft preview; `.mpp` not updated |
| Artifact generated | Info | MSPDI/XML generated; `.mpp` not updated |
| Opened in Microsoft Project | Warning | Manual check underway |
| Verified | Success | Verification metadata recorded |
| Rejected / superseded | Neutral/Critical | Use reason-specific class |

### Sync state

| State | Class | Required copy |
| --- | --- | --- |
| Local draft | Neutral | Saved locally. |
| Queued on device | Warning | Queued on this device. Not yet sent. |
| Sending | Info | Sending. |
| Server received | Success | Server received. |
| Failed | Critical | Could not send. Still saved on this device. |
| Conflict | Critical | Conflict needs review. |

## Project handoff status copy

Use exact Project-boundary copy where appropriate.

```text
Planner approval marks this progress as eligible for export preview. The master .mpp is not updated.
Draft export preview — master .mpp not updated.
Export batch approved — master .mpp not updated.
MSPDI/XML artifact generated — master .mpp not updated.
Planner must manually open/check the artifact in Microsoft Project.
Verified in Microsoft Project — master .mpp update remains planner-controlled.
Shutdown Tracker records verification metadata only.
```

## Chip usage rules

Chips should communicate state quickly but not become the whole interface.

Use chips for:

- execution state;
- review/export state;
- sync state;
- blocker/evidence state;
- restricted/export eligibility state.

Do not use chips for:

- long explanations;
- every available field;
- actions;
- ownership;
- task names;
- general notes.

Maximum default chips:

| Surface | Max default chips |
| --- | --- |
| Mobile task card | 2-3 |
| Console summary card | 2 |
| Planner review row | 2-4 because review/export comparison is the task |
| Task detail | As needed, grouped by state dimension |

## Component guidance

| Component | Use | Avoid |
| --- | --- | --- |
| StatusChip | Short state label | Long text, colour-only state |
| BoundaryNotice | Project/export/offline warnings | Repeating on every low-risk panel |
| ReviewQueue | Needs action list | Generic dashboard card grid |
| ProgressComparisonTable | Old/new value comparison | Card-only export diff |
| SyncBanner | Compact mobile/console sync summary | Oversized top diagnostic tiles |
| TaskCard | Mobile assigned work | Full review/export lifecycle on every card |
| DetailDrawer | Console task/problem/evidence detail | Modal sprawl |
| DataTable | Planner/control review | Spreadsheet-like inline editing across many columns |

## Empty, loading, and error states

Every screen must show safe states.

| State | Required behaviour |
| --- | --- |
| Empty | Explain what is absent and what action, if any, is next |
| Loading | Do not hide previously visible critical state unless stale indicator is shown |
| Error | Show what failed, what remains saved, and how to retry |
| Offline | Show what is available and what cannot be submitted yet |
| Read-only | Say why write controls are unavailable |
| Visual-only | Say no production write workflow exists |

## Accessibility rules

- State is text first, colour second.
- Icons are supplemental, not sole indicators.
- All controls require visible focus.
- Mobile touch targets should be practical for field use.
- Status changes must be conveyed programmatically where relevant.
- Error messages must identify the field/action and recovery path.
- Do not use tiny icon-only controls for task state changes.
- Do not require drag-only interactions for critical actions.

## Review checklist

Before accepting a frontend visual PR, confirm:

- top-level navigation remains within approved IA;
- state labels follow this status model;
- mobile My Work shows work before diagnostics;
- console Today prioritizes attention and exceptions;
- planner/export screens use old/new/source comparison;
- Project handoff copy is visible;
- queued/failed/server-received states are explicit;
- no screen implies hidden `.mpp` update;
- no screen introduces scheduler visuals or chat;
- synthetic labels are sanitized and realistic;
- controls that are not wired are disabled or explicitly visual-only.
