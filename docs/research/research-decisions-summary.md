# Research Decisions Summary

This summary consolidates the current research direction for Shutdown Tracker. It is a decision-oriented index, not a replacement for the underlying research packets or ADRs.

## Executive decision

Shutdown Tracker should become a reviewed live execution-control system, not a scheduler and not a generic chat app.

The core next product capability is **Task Progress Review and Export Approval**. This connects field execution truth to the existing Microsoft Project import/export foundation without allowing hidden schedule changes.

Core workflow:

```text
field progress update
-> supervisor review
-> planner review
-> export eligibility
-> export preview
-> MSPDI/XML artifact generated
-> planner manually opens/checks in Microsoft Project
-> planner controls whether master .mpp is saved
-> Shutdown Tracker records verification metadata and audit
```

The Task Progress Review visual shell already exists as static/synthetic frontend review work. The next coding step should be a frontend cleanup/anti-slop pass, not more feature surface.

## Product boundary decisions

| Area | Decision |
| --- | --- |
| Product identity | Live shutdown execution tracker |
| Microsoft Project role | Schedule authority and final master-file control point |
| Shutdown Tracker role | Execution truth, review, evidence, handover, export preparation, verification metadata, audit |
| Scheduling logic | Do not build CPM, float, critical path, resource levelling, date movement, dependency editing, baselines, schedule optimisation |
| Export format | MSPDI/XML, not native `.mpp` writing |
| Project file update | Planner-controlled manual open/check/save outside Shutdown Tracker automation |
| Import model | Immutable project snapshots, not live-linked Project documents |
| Re-import model | New snapshot plus task-lineage review, not in-place mutation |
| Audit model | Relational records plus append-only audit events for v1 |
| Offline model | IndexedDB queue and visible sync state; Background Sync as progressive enhancement only |
| Communications model | Entity-linked Discussion later; no generic chat first |
| UX model | Operational, narrow, anti-dashboard; no generated card wall |

## Current repo baseline to preserve

As of this consolidation, the repo has scaffolding and partial import/export review capability, but not live execution workflows.

Current implemented/scaffolded areas include:

- Spring Boot API scaffold.
- Spring Boot project-worker scaffold.
- PostgreSQL/Flyway migration structure.
- MPXJ import summary spike.
- Worker-backed MSPDI/XML export artifact spike.
- Source-file validation and upload orchestration.
- Import review endpoints.
- Task lineage review endpoints.
- Export preview endpoints.
- Export approval/rejection metadata endpoints.
- Worker-backed artifact generation endpoint.
- Project opened/verified metadata endpoints.
- React/Vite console scaffold.
- React/Vite mobile PWA scaffold.
- Shared TypeScript API client.
- Guarded source/import/export smoke script.
- Static/synthetic Task Progress Review frontend visual shell.
- Source-quality and decision-map documents.
- Product-source docs for task progress review, communications, frontend visual review, UX anti-slop, and status semantics.

Not yet implemented:

- Live task execution state.
- Task progress capture workflow.
- Task start/pause/resume/block/complete APIs.
- Supervisor review workflow.
- Planner task-progress review queue backend/API.
- Frontend write workflows.
- Mobile offline queue / IndexedDB sync engine.
- Evidence upload workflow.
- Handover workflow.
- Communications backend.
- Production object storage.
- Production queue/background consumers.
- Real Microsoft Project write-back.
- Automated Microsoft Project verification.

## Task Progress Review and Export Approval

This is the most important product capability because it provides the missing bridge between field execution and the existing export preview/MSPDI architecture.

| User | Shutdown situation | Decision supported | Error prevented |
| --- | --- | --- | --- |
| Field user | Work starts, stalls, progresses, or completes | What happened at the workfront | Free-text or delayed status drift |
| Supervisor | Field update needs operational validation | Whether the update is credible and complete | Bad field data entering planner queue |
| Planner | Reviewed progress may affect Microsoft Project | Which fields are safe to export | Hidden or unsafe Project write-back |
| Shutdown Control | Review queues, blockers, and export readiness need attention | What needs action now | Operational issues missed between meetings |

### State dimensions

Do not collapse all task state into one field. The product needs separate dimensions:

| Dimension | Example states |
| --- | --- |
| Execution state | Not started, ready, in progress, paused, blocked, completed |
| Progress review state | Draft, submitted, needs supervisor review, supervisor accepted, correction requested, rejected, superseded |
| Planner review state | Needs planner review, planner approved, planner rejected |
| Export state | Not eligible, eligible, export blocked, approved for export, in export preview, artifact generated, opened in Microsoft Project, verified |
| Sync state | Local draft, queued on device, sending, server received, failed, conflict |

### MVP progress field decisions

| Field | Product decision | Microsoft Project handoff impact |
| --- | --- | --- |
| Percent complete | MVP export candidate, leaf tasks only | Planner-approved export field |
| Actual start | MVP export candidate, leaf tasks only | Planner-approved export field |
| Actual finish | MVP export candidate, leaf tasks only | Planner-approved export field |
| Physical % complete | Internal/deferred unless site practice proves need | Not default MVP export field |
| Remaining duration | Defer | Higher schedule side-effect risk |
| Actual duration | Defer | Higher interaction with Project recalculation |
| Actual work / remaining work | Defer | Assignment/work-model complexity |
| Assignment actuals | Defer | Higher mapping and schedule-effect complexity |
| Summary-task actuals | Never directly export | Must remain calculated/rolled up in Microsoft Project |
| Planned dates | Never update from Shutdown Tracker | Schedule authority field |
| Dependencies, constraints, calendars, baselines, resources | Never update from Shutdown Tracker | Scheduler-owned fields |

## Communications decision

The communications layer should not start as generic chat.

- Build structured records first: progress updates, blockers/problems, actions, evidence, handover, export review, Project verification notes.
- Build entity-linked Discussion later where it supports these records.
- Do not build broad channels, DMs, or WhatsApp-style chat as source of truth.

### Communication rules

| Rule | Reason |
| --- | --- |
| A comment is not task progress | Progress needs fields, review state, audit, and export eligibility |
| A comment is not a blocker unless promoted | Work-stopping issues need owner, severity, due/review time |
| A comment is not an action unless promoted | Follow-up needs owner, due time, and close-out state |
| A comment is not handover unless flagged/promoted | Incoming shift needs curated records, not chat history |
| Evidence is not a chat attachment | Evidence needs metadata, file controls, chain-of-custody handling, audit |
| Export review comments do not update Microsoft Project | They support planner decision only |
| Project verification notes do not save or update the master `.mpp` | They record manual planner verification metadata only |

## Critical Watchlist / Critical Work Package decisions

| Area | Decision |
| --- | --- |
| Product role | Reporting/watchlist layer, not scheduling layer |
| Default source | Imported Microsoft Project summary task plus descendants |
| Additional source mode | Multi-summary group where one reporting stream crosses WBS boundaries |
| Manual grouping | Defer until pilot need is proven |
| Reporting policy | Configurable, versioned policy: none, ad hoc, fixed interval, fixed times, shift-based, event-triggered, custom |
| Critical Update | Immutable submission with optional multiple lines |
| Project handoff | Critical Updates do not update Project; only separately approved leaf-task progress/actual fields can export |
| Risk to manage | Critical-path creep, dashboard bloat, bespoke site workflow creep |

## UX decisions

| Area | Decision |
| --- | --- |
| Console top-level navigation | Today, Tasks, Problems, Evidence, Exports |
| Mobile top-level navigation | My Work, Today, Problems, Evidence, Sync |
| Console default | High-signal Today screen, not dashboard zoo |
| Mobile default | My Work |
| Problems model | Unified Problems area with typed records: blocker, delay, hold, permit, action, quality, access, material, crane/lift, safety |
| Evidence UX | Camera-first, linked to entity, visible upload state |
| Offline UX | Persistent sync indicators and per-item queue state |
| Import/export UX | Planner-grade diff preview with old value, new value, source, eligibility, exclusion reason, and verification state |
| Visual design | Restrained operational language, limited semantic colours, no badge soup |
| Exclusions | Gantt/scheduling views, dependency maps, dashboard builders, broad chat, AI copilots, hidden sync, direct Project automation |

## Architecture decisions

| Area | Decision |
| --- | --- |
| Backend pattern | Modular monolith first |
| Backend stack | Java/Spring Boot currently implemented; Kotlin remains optional future language preference but repo is Java |
| Project processing | API orchestrates; worker owns MPXJ parsing/export artifact generation |
| Database | PostgreSQL with migrations |
| File storage | Storage abstraction now; object storage for production source files, evidence, generated artifacts |
| Frontend | React + Vite console and mobile PWA |
| Offline | IndexedDB, service worker, Cache API, visible sync states, idempotency keys |
| Audit | Append-only audit events plus current-state projections |
| Realtime | SSE may be enough for status push later; WebSockets only if two-way live collaboration is required |
| Queue/background work | Current explicit worker handoff; future durable queue/outbox strategy |

## Build sequence decisions

Recommended build order from here:

1. Source consolidation docs. Complete.
2. Docs-only repo alignment for Task Progress Review, Communications Layer, visual review, and UX anti-slop. Complete through product-source docs.
3. Frontend visual shell cleanup / anti-slop pass.
4. Product/user review with planner, supervisor, field user, shutdown control.
5. Backend/API brief for progress submission, supervisor review, planner review, export candidates.
6. Backend/API implementation.
7. Mobile field progress submission and visible sync states.
8. Entity-linked Communications visual shell.

## What to build next

The next coding PR should be a frontend cleanup, not a new feature.

Scope:

- Restore console top-level nav to Today, Tasks, Problems, Evidence, Exports.
- Move Supervisor Review and Planner Review into Today/Tasks/Exports sections or saved views.
- Move Project Verification under Exports.
- Replace visible synthetic labels with sanitized realistic examples.
- Compact the mobile sync status area.
- Reduce mobile task-card chip density.
- Keep visual-only controls disabled.
- Preserve Project-boundary warnings.

## What to defer

| Deferred item | Reason |
| --- | --- |
| Generic chat, DMs, broad channels | Critical decisions can be buried in unstructured text |
| Push notifications beyond failure/escalation concepts | Alert fatigue and notification governance risk |
| Remaining duration / work write-back | Greater Project recalculation side-effect risk |
| Project Online / Dataverse connectors | Not aligned with desktop `.mpp` MVP; requires separate source review and ADR |
| Project add-in / VBA / COM automation | Governance/support/hidden-write risk |
| AI summaries/anomaly detection | Needs trustworthy structured data first; safety-critical review risk |
| Full event sourcing | Complexity not justified before core workflow is proven |
| Scheduler-style Gantt/dependency editing | Violates product boundary |

## What not to build

- Hidden `.mpp` save/update from Shutdown Tracker.
- Native `.mpp` writer based on MPXJ assumptions.
- CPM, float, critical path, recovery scheduling, or automatic date movement.
- Summary-task actual write-back.
- Resource levelling or assignment-rate write-back.
- Complete-by-comment workflow.
- WhatsApp-style chat as operational source of truth.
- Evidence as arbitrary chat attachment.
- Background Sync as the only sync correctness mechanism.
- Any UI that implies approval inside Shutdown Tracker has already updated Microsoft Project.

## Open decisions needing product review

| Question | Recommended default until decided |
| --- | --- |
| Is Physical % Complete used consistently by target sites? | Defer export; show as optional internal field only |
| Which tasks require mandatory evidence for completion? | Template/policy-driven; safety/quality close-out requires evidence by default |
| How much contractor visibility is allowed? | Conservative isolation by default |
| Should Critical WPs support arbitrary manual task grouping in MVP? | Defer; support summary and multi-summary first |
| Should handover be mostly generated, curated, or manually authored? | Generated from unresolved structured records plus curated notes |
| Should Teams integration appear early? | No; future notification mirror only, not source of truth |
| Should Project automation/add-in work start soon? | No; prove manual MSPDI/XML handoff first |

## Source quality summary

Use these source classes for hard repo decisions:

- Microsoft Project documentation.
- MPXJ documentation.
- HSE communications, handover, permit-to-work, workload, and alarm-management guidance.
- MDN browser/PWA documentation.
- WCAG / WAI accessibility guidance.
- OWASP logging and file-upload guidance.
- NIST chain of custody.
- PostgreSQL and Spring official documentation.
- OpenID Connect and Microsoft identity documentation.

Use these only as supporting/background sources:

- IBM system-of-record terminology.
- Azure Event Sourcing pattern.
- Eastwood Harris Physical % Complete paper.
- UX design system and React table/list performance articles.
- Synthetic-data background articles.

Avoid using these for hard decisions:

- Vendor marketing.
- Generic SaaS/productivity blogs.
- StackOverflow/Reddit/YouTube examples.
- GitHub Discussions or issues except narrow compatibility notes.
- Gantt library listicles.
