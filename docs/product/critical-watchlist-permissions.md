# Critical Watchlist Permissions

Critical Watchlists and Critical Work Packages are reporting constructs, not scheduling constructs.

Critical WPs do not calculate critical path. Critical WP due/overdue state does not move Microsoft Project dates. Critical Updates do not directly update Microsoft Project. Only separately approved leaf-task actual/progress fields may become export candidates.

## Source Rules

A Critical Work Package may be sourced from:

- One imported summary task plus all descendants.
- Multiple imported summary tasks where one reporting group spans schedule boundaries.

Arbitrary manual leaf-task grouping should be deferred unless required by pilot feedback.

## Reporting Policy Rules

Reporting policies are configurable and may include:

- none
- ad hoc
- fixed interval
- fixed times
- shift-based
- event-triggered
- custom

Four-hour reporting is a configurable template, not hardcoded behavior.

## Permissions

| Capability | Default authority | Notes |
| --- | --- | --- |
| Create watchlist | Planner, Shutdown Control | Admin may assist with setup; Coordinators may request. |
| Edit watchlist | Planner, Shutdown Control | Scoped Coordinators may edit if delegated. |
| Archive watchlist | Planner, Shutdown Control | Archive does not delete history. |
| Select summary-task Critical WP | Planner, Shutdown Control | Source must point to imported summary task plus descendants. |
| Select multi-summary Critical WP | Planner, Shutdown Control | In MVP scope where one reporting group spans schedule boundaries. |
| Change Critical WP source | Planner, Shutdown Control | Requires reason and audit event. |
| Change reporting policy | Planner, Shutdown Control | Mid-shutdown changes require reason and audit event. |
| Submit Critical Update | Shutdown Control, Coordinator, Supervisor, assigned Field User, assigned Contractor, Inspector | Depends on reporting policy and scope. |
| Correct submitted Critical Update | Submitter or scoped reviewer | Preserve original update and create correction/supersession. |
| Review Critical Update | Planner, Shutdown Control, Coordinator, Supervisor, Inspector | Scope-based. |
| Generate Critical Watch report | Planner, Shutdown Control, scoped Coordinator/Supervisor, Viewer / Management read-only | Report generation is not schedule calculation. |

## Required Audit Events

| Action | Required event type |
| --- | --- |
| Create watchlist | `critical_watchlist_created` |
| Add Critical WP source | `critical_wp_source_added` |
| Remove Critical WP source | `critical_wp_source_removed` |
| Change reporting policy | `reporting_policy_changed` |
| Generate reporting period | `reporting_period_generated` |
| Submit Critical Update | `critical_update_submitted` |
| Correct Critical Update | `critical_update_corrected` |
| Supersede Critical Update | `critical_update_superseded` |
| Archive watchlist | `critical_watchlist_archived` |

## Review and Export Boundary

- Critical Update review is a reporting review, not Microsoft Project export approval.
- Critical Watch reports remain inside Shutdown Tracker unless exported as reports.
- Critical WP source selection does not create dependencies, calculate float, or alter dates.
- A task update linked to a Critical WP may become an export candidate only if it is a leaf-task progress/actual field and passes the normal export approval workflow.
