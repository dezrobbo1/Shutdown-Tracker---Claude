# Source Quality Register

This register classifies the source material used to guide Shutdown Tracker product, architecture, UX, and implementation decisions.

The register is intentionally conservative. Hard product and architecture rules should be supported by official documentation, standards, or high-quality domain guidance. NotebookLM summaries, vendor pages, blog posts, forums, and GitHub issues may be useful during exploration, but they should not be used as the sole evidence for high-risk decisions.

## Tier definitions

| Tier | Meaning | Suitable use |
| --- | --- | --- |
| Tier A | Authoritative or primary source | Product boundary, ADRs, architecture constraints, security/audit/offline rules |
| Tier B | Credible supporting source | Implementation rationale, terminology, UX pattern support, background explanation |
| Tier C | Weak or contextual source | Market context, exploratory ideas, implementation caveats only |
| Internal synthesis | Project-generated research or review notes | Summarize and connect decisions, but verify hard claims against Tier A/B sources |

## Tier A sources

| Source | Supports | Use in repo? | Notes |
| --- | --- | --- | --- |
| Microsoft Support: Export or import data to another file format | Microsoft Project can import/export XML; XML is the full-project interchange route; import requires valid Project XML | Yes | Primary source for MSPDI/XML handoff and import/export boundary |
| Microsoft Learn: Project Elements and XML Structure | Project-level MSPDI/XML structure and fields | Yes | Primary source for snapshot/project import model |
| Microsoft Learn: Task Elements and XML Structure | Task-level MSPDI/XML fields including task identity, dates, progress, and hierarchy-relevant values | Yes | Primary source for imported task model and progress field whitelist |
| Microsoft Learn: Saving and Opening Projects in XML Format | Project XML open/import/merge behaviour and Project desktop workflow | Yes | Primary source for planner-controlled manual verification flow |
| Microsoft Support: Update work on a project | Project progress/actual update semantics | Yes | Use cautiously to justify progress-field handling; do not infer broad write-back permission |
| MPXJ supported formats | MPXJ read/write support by file format | Yes | Primary source for `MPP` read and `MSPDI` read/write capability |
| MPXJ FAQ | MPXJ does not currently write native `MPP`; recommends MSPDI for output | Yes | Primary source for no-native-MPP-output boundary |
| MPXJ getting started and field documentation | Java API usage and field access | Yes | Use for worker implementation and import/export spike design |
| HSE safety-critical communications | Communication during operations and maintenance; task-relevant communication | Yes | Primary source for structured operational communication over generic chat |
| HSE shift handover guidance | Handover needs written/verbal support, relevance, and incoming-shift information needs | Yes | Primary source for handover model and report content |
| HSE permit-to-work and human-factors guidance | Permit/isolation/access communication and human factors | Yes | Primary source for blockers, holds, and safety-sensitive workflow treatment |
| MDN IndexedDB API | Local structured data and browser storage support | Yes | Primary source for mobile offline queue storage |
| MDN Service Worker API | Offline shell and request interception model | Yes | Primary source for PWA offline architecture |
| MDN Cache API | Request/response caching for app shell and selected read-mostly responses | Yes | Primary source for cache strategy |
| MDN Background Synchronization API | Background Sync as limited-availability progressive enhancement | Yes | Primary source for not relying on Background Sync as correctness backbone |
| WCAG 2.2 | Accessibility, target size, focus, error handling, status messages | Yes | Primary source for visible status, non-colour-only state, and accessible UX requirements |
| OWASP Logging Cheat Sheet | Logging and audit event guidance | Yes | Primary source for audit event integrity and logging rules |
| OWASP File Upload Cheat Sheet | File upload controls and validation | Yes | Add before evidence upload implementation |
| NIST Chain of Custody definition | Evidence handling and custody concept | Yes | Primary source for evidence metadata/audit rationale |
| PostgreSQL documentation: JSON types, partitioning, NOTIFY/LISTEN | JSONB metadata, audit/event growth, lightweight signalling | Yes | Primary source for PostgreSQL feature decisions; do not treat NOTIFY as a durable queue |
| Spring Boot official docs and testing docs | Backend stack and test support | Yes | Primary source for Spring Boot implementation and test strategy |
| OpenID Connect Core 1.0 and Microsoft identity platform docs | Authentication and ID-token model | Yes | Primary source for auth direction |

## Tier B sources

| Source | Supports | Use in repo? | Notes |
| --- | --- | --- | --- |
| IBM: System of Record | Terminology for authoritative system framing | Limited | Useful language for Microsoft Project as schedule authority, not architecture proof |
| Azure Architecture Center: Event Sourcing pattern | Future audit/replay pattern consideration | Limited | Use as later/experimental option; v1 remains relational state plus append-only audit |
| Eastwood Harris: Physical Percent Complete | Physical progress practice in Microsoft Project contexts | Limited | Do not make Physical % Complete the default export field unless site practice proves it |
| Foojay: Service Layer Pattern in Java/Spring Boot | General service-layer rationale | Limited | Prefer official Spring docs for hard decisions |
| Baeldung: Spring Security IP range allowlisting | Review-environment access patterns | Limited | Useful only for low-risk review access discussion; not production auth design |
| Norbix: JSON Schema contract-first validation | Contract-thinking support | Limited | Useful for API/worker contract discussion if paired with internal tests |
| LogRocket: Rendering large lists in React | Large-list virtualization concept | Limited | Useful for UI performance rationale; do not mandate one library from this source |
| Simple Table: React tree data | Hierarchical table pattern support | Limited | UX pattern support only |
| Carbon Design System data table guidance | Table pattern guidance | Limited | Useful for console task-table design; not a hard dependency |
| GOV.UK notification and error summary patterns | Status, warnings, and error copy patterns | Limited | Good UX support source if added explicitly |
| K2View / IBM synthetic data generation material | Synthetic data concepts | Limited | Internal synthetic-data policy remains stricter and project-specific |

## Tier C sources and sources to avoid for hard claims

| Source | Supports | Use in repo? | Notes |
| --- | --- | --- | --- |
| GeeksForGeeks immutable architecture article | Generic immutability explanation | Avoid for hard claims | Replace with internal audit/supersession rules plus OWASP/PostgreSQL evidence |
| GitHub Discussions about scalable uploads | Exploratory implementation comments | Avoid for hard claims | Use official Spring/object-storage/OWASP sources instead |
| Temporal GitHub issues | Narrow implementation caveats | Avoid for hard claims | Use official Temporal docs only if Temporal is later selected |
| StackOverflow, Reddit, Medium, DEV.to, YouTube examples | Anecdotal implementation context | Avoid for hard claims | May reveal issues to test, not source architectural policy |
| Vendor marketing pages for STO/project tools | Market context | Avoid for architecture/product proof | Useful only for competitor/context scan |
| React Gantt library articles | UI library survey | Generally avoid | Risk of Gantt/scheduler drift; any Gantt-like surface needs separate ADR |
| Generic dashboard/BI articles | Visual inspiration | Avoid for MVP scope decisions | Product should stay operational, not BI/dashboard-heavy |

## Internal synthesis sources

| Source | Status | Use rule |
| --- | --- | --- |
| Shutdown Live-Tracking Platform Research | Strong internal synthesis | Good baseline for architecture, Microsoft Project boundary, UX split, stack direction, import/export flow |
| UX and Operational Interaction Design Research Packet | Strong internal synthesis | Good baseline for console/mobile IA, operational UX, offline honesty, accessibility, and MVP screen scope |
| Configurable Critical Work Package Reporting research | Strong internal synthesis | Good baseline for Critical Watchlist/Critical WP reporting model and no-scheduling boundary |
| Shutdown Tracker Functionality, Possibilities, and Next Product Direction | Strong internal synthesis | Current best source for Task Progress Review & Export Approval as the next major capability |
| Built-in Communications and Messaging Layer research | Supporting internal synthesis | Use to define entity-linked Discussion and defer generic chat, DMs, broad channels |
| Communications Layer Visual Review Brief research | Supporting internal synthesis | Use for future visual shell, exact copy, component inventory, and static/read-only/future-build labelling |
| Technical Implementation Blueprint: Shutdown Tracker | Working synthesis note | Useful, but correct over-strong claims before repo use: MPXJ/JAXB issue, Physical % Complete preference, queue assumptions, UID durability |
| Technical Development Report: Shutdown Tracker Architectural Framework | Working synthesis note | Useful, but demote weak cited sources and verify hard claims against Tier A/B sources |
| Live_Project_Tracker_Chat_Summary and chat summary reports | Historical context | Do not use as architecture proof; earlier project direction predates current boundary decisions |

## Corrections to apply when using NotebookLM material

| NotebookLM wording or implication | Repo-grade wording |
| --- | --- |
| MPXJ/JAXB conflict is the decisive architecture reason for a worker | A Spring Boot / JAXB compatibility issue has been reported, but the primary reason for worker isolation is Project-file processing containment: dependency, CPU, memory, file IO, failure, and support risk |
| Physical % Complete is the preferred STO metric | Physical % Complete may be supported where site practice is consistent; MVP export should default to leaf-task percent complete, actual start, and actual finish |
| API saves to temporary storage and queues a worker message | Current repo stores accepted files through storage abstractions, records metadata/hash, creates import batches, and uses explicit worker handoff; durable queue/background processing is future architecture |
| Task UID is the durable join key across all revisions | Task UID is strong within an imported snapshot; re-import continuity needs snapshot ID, UID, WBS/name/custom-field signals, and accepted task-lineage links |
| Audit trails use event sourcing | v1 should use relational domain records plus append-only audit events; full event sourcing is later/experimental |

## Source hygiene rules

- Prefer official documentation, standards, and regulator/human-factors guidance for hard claims.
- Remove `utm_source=chatgpt.com` and similar tracking parameters when copying URLs into repo docs.
- Do not cite NotebookLM or chat summaries as primary evidence for product or architecture policy.
- Do not use vendor marketing pages as proof of workflow value or technical feasibility.
- Treat GitHub issues as narrow compatibility warnings only.
- Any future direct Microsoft Project automation, Project add-in, Project Online, Dataverse, Teams, AI, or push-notification decision requires a separate source review and ADR.
