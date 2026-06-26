# Research Decision Source Map

This map links current Shutdown Tracker product and architecture decisions to the source evidence that should support them.

Use this file when updating product docs, ADRs, implementation briefs, or Codex prompts. Hard claims should cite Tier A sources from the source quality register where possible.

## Decision map

| Decision | Primary source evidence | Secondary support | Internal source of truth to update/use |
| --- | --- | --- | --- |
| Microsoft Project remains the schedule authority | Microsoft Project XML/import/export documentation; Microsoft Support Project file-format and update-work documentation | IBM System of Record terminology | Product boundary statement, README, ADRs |
| Shutdown Tracker is the live execution and reporting authority | HSE communications/handover guidance; UX research on operational dashboards; project-controls work packaging sources | NotebookLM system-of-action framing as terminology only | Product README, Task Progress Review brief |
| Do not calculate CPM, float, critical path, resource levelling, dependency logic, or schedule optimisation | Microsoft Project model/logic ownership; internal product boundary | UX research excluding Gantt/scheduler UI | README, architecture README, ADRs |
| Store imported schedules as immutable snapshots | Microsoft Project XML schema; internal import/export architecture; PostgreSQL relational/audit model | Azure Event Sourcing pattern as background only | Architecture README, import/export policy |
| Use task lineage review across re-imports | Microsoft Project XML task identity fields; latest functionality research on re-import risk | NotebookLM reports, corrected to avoid over-trusting UID alone | Task lineage docs, future re-import UX brief |
| Use MSPDI/XML as outbound handoff format | Microsoft Support Project XML import/export; MPXJ supported formats and FAQ | Microsoft Project desktop file-format docs | Import/export architecture, worker/export docs |
| Do not write native `.mpp` with MPXJ | MPXJ FAQ and supported formats | Internal architecture research | Export boundary docs, ADRs |
| Keep Project verification manual and planner-controlled | Microsoft Project XML open/import flow; XML merge/overwrite risk; manual round-trip evidence policy | Latest functionality research | Export preview docs, Project verification docs |
| Export only planner-approved leaf-task progress/actual fields | Microsoft Project task XML fields; Microsoft Support update-work docs; summary-task handling risk | Internal approval/export state model | Approval/export state model, task progress brief |
| MVP export whitelist is percent complete, actual start, actual finish | Microsoft Project task fields and update-work guidance | Eastwood Harris as optional Physical % Complete background | Task Progress Review brief, export rules |
| Physical % Complete is optional/deferred | Eastwood Harris as supporting context; product review required | Latest functionality research | Task Progress Review brief |
| Remaining duration, actual duration, actual work, assignment actuals are deferred | Microsoft Project recalculation/interdependent fields; latest functionality research | Internal export-risk model | Approval/export state model |
| Summary-task actuals, dependencies, constraints, calendars, baselines, resources remain read-only | Microsoft Project task/project schema and support docs; internal boundary | Critical WP research | Approval/export state model, README |
| Next major product capability is Task Progress Review & Export Approval | Latest functionality research; UX task-state/review/export research | Existing export preview/backend scaffold | New product feature brief |
| Field progress does not go straight to Microsoft Project | HSE communication/review principles; Microsoft Project handoff risk | Latest functionality research | Task Progress Review brief |
| Supervisor review confirms operational validity, not export approval | Roles/permissions research; HSE handover/communication guidance | Latest functionality research | Roles/capabilities, permission matrix, Task Progress Review brief |
| Planner review controls export eligibility and approval | Microsoft Project authority boundary; current role matrix | Export preview research | Permission matrix, approval/export state model |
| Use structured blockers/problems/actions instead of comments for work-stopping issues | HSE safety-critical communications; permit-to-work; workload/attention guidance | Communications research | Problems/actions docs, communications-layer doc |
| Scaffold/permit/isolation/material/access/crane/quality issues become structured blockers/actions | HSE permit-to-work and communications guidance; latest functionality research | Critical WP reporting research | Problems/actions taxonomy docs |
| Evidence is a first-class object, not a chat attachment | NIST Chain of Custody; OWASP File Upload Cheat Sheet | UX evidence capture research | Evidence docs, object-storage strategy |
| Handover is a structured record/report, not casual comment history | HSE shift handover guidance | UX research, latest functionality research | Handover docs, communications-layer doc |
| Critical Watchlists / Critical WPs are reporting constructs, not scheduling constructs | Microsoft Project boundary; Critical WP research | UX dashboard/attention research | Critical Watchlist permissions, product README |
| Critical WP default source is summary task plus descendants | Microsoft Project task hierarchy fields; MPXJ hierarchy traversal | Critical WP research | Critical Watchlist docs |
| Critical WP reporting policy is configurable | Critical WP research; HSE handover/communication needs | Internal UX research | Critical Watchlist docs |
| Do not build generic chat first | HSE communications; UX alert/load guidance; communications research | Latest functionality research | Communications-layer doc |
| Use entity-linked Discussion later | Communications research; HSE task-relevant communication | UX research | Communications-layer doc |
| A comment is not progress, blocker, action, evidence, or handover unless promoted/linked | Communications research; audit/supersession principles | Latest functionality research | Communications-layer doc, audit schema |
| Mentions and Needs Response are attention mechanisms, not a general inbox | Communications visual review research; alert fatigue guidance | UX research | Communications-layer doc |
| Announcements are controlled broadcast records, not broad open channels | Communications research; alert fatigue guidance | HSE communication guidance | Communications-layer doc |
| Console IA remains Today, Tasks, Problems, Evidence, Exports | UX research; operational-dashboard guidance | Product README | Product README, future frontend briefs |
| Mobile IA remains My Work, Today, Problems, Evidence, Sync | UX research; PWA guidance | Product README | Product README, future frontend briefs |
| Avoid Gantt/scheduling UI in MVP | UX research; product boundary | React Gantt articles only as avoid/defer context | Product README, ADRs |
| Use React + Vite for console/mobile PWA shells | React/Vite official docs; existing repo scaffold | UX/architecture research | Architecture README |
| Use mobile PWA before native apps | MDN PWA docs; Microsoft Edge PWA guidance | UX research | Architecture README, offline docs |
| Use IndexedDB for offline structured data and queue | MDN IndexedDB; Microsoft Edge PWA storage docs | UX research | Offline sync docs |
| Use service workers and Cache API for app shell/read-mostly caching | MDN Service Worker and Cache API docs | UX research | Offline sync docs |
| Treat Background Sync as progressive enhancement only | MDN Background Sync limited availability; Microsoft Edge background sync guidance | UX research | Offline sync docs |
| Queued is not submitted | WCAG status messaging; offline UX research | Internal offline-audit-sync rules | Offline sync docs |
| Store both local capture time and server received time | Offline/audit research; audit model | Current offline-audit-sync rules | Offline sync docs, audit schema |
| Use append-only audit events plus current-state projections for v1 | OWASP Logging Cheat Sheet; PostgreSQL model; latest functionality research | Azure Event Sourcing as later option | Audit schema, architecture README |
| Full event sourcing is later/experimental | Azure Event Sourcing pattern; implementation complexity assessment | Latest functionality research | ADR backlog |
| Use modular monolith first | Spring Boot docs; internal architecture research | Service-layer supporting articles | Architecture README |
| Keep worker-owned Project file processing | MPXJ JVM fit; import/export risk containment | MPXJ/JAXB issue only as compatibility warning | Architecture README, worker docs |
| Use object storage for source files, generated artifacts, and evidence | Object-storage strategy; OWASP file upload guidance | Architecture research | Object storage strategy |
| Use synthetic review/demo data only, disabled by default | Synthetic data guidance; internal security/data policy | Seeded review/demo data strategy | Testing docs |
| Do not use real Project/customer files as fixtures | Security/privacy boundary; internal fixture strategy | Synthetic data sources | Testing docs |
| AI summaries/anomaly detection are experimental only | HSE safety-critical communication/handover risk; latest functionality research | Azure/AI sources require future review | ADR backlog |
| Project Online / Dataverse / Graph integrations are future only | Microsoft Project/Graph/Project Online docs, to be re-verified before implementation | Latest functionality research | Integration ADR backlog |
| Direct Microsoft Project desktop automation/add-in/COM is not MVP | Microsoft Project handoff risk; governance and hidden-write risk | Latest functionality research | Integration ADR backlog |

## Internal decision clusters

### Core boundary

The core boundary is stable:

- Microsoft Project owns schedule logic, dates, dependencies, resource levelling, baselines, critical path, and final file save/update decisions.
- Shutdown Tracker owns execution truth, task progress capture, blockers, actions, evidence metadata, handover, planner review, export preview, generated MSPDI/XML artifact metadata, verification metadata, and audit.
- The product must not imply that any internal approval or discussion updates the master `.mpp`.

### Next major product capability

The next major product capability is Task Progress Review & Export Approval:

1. Field user or supervisor submits structured progress.
2. Supervisor validates operational accuracy.
3. Planner reviews export-safe fields and task identity.
4. Approved leaf-task candidates enter export preview.
5. Planner approves export batch.
6. Worker generates MSPDI/XML artifact.
7. Planner manually opens/checks in Microsoft Project.
8. Shutdown Tracker records verification metadata and audit.

### Communications position

The communications decision is not “build chat”. It is:

- no generic chat in MVP;
- no private-message workflow as execution source of truth;
- entity-linked Discussion later;
- structured blockers/actions/handover/evidence first;
- mentions and Needs Response as attention signals;
- export-review and Project-verification notes as audit-visible comments attached to the relevant entity.

### Source use rules

- Use Microsoft, MPXJ, HSE, MDN, WCAG, OWASP, NIST, PostgreSQL, Spring, OIDC as primary support where relevant.
- Use NotebookLM and deep-research packets as internal synthesis, not primary proof.
- Demote generic blogs, vendor marketing, forum posts, and GitHub issues unless they are narrow compatibility warnings.
