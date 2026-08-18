# Research Source Catalogue

The sources below are held externally and support architecture, Microsoft Project integration, UX/UI, communications, operational mapping, and product-direction decisions.

They are evidence/provenance sources, not current product authority. Accepted decisions belong in ADRs, product documents, architecture documents, or current research decision summaries.

## Primary supporting research

1. `Microsoft Project import and export architecture for a shutdown live-tracking application.pdf` — Project/MSPDI architecture and handoff boundary.
2. `Shutdown Live-Tracking Platform Research.pdf` — broad platform architecture and operational UX baseline.
3. `UX and Operational Interaction Design Research Packet for Shutdown Tracker.pdf` — console/mobile operational UX and offline interaction research.
4. `Planner-Configurable Operational Structures Derived from Microsoft Project XML.pdf` — operational-mapping research derived from the supplied schedule examples.
5. `Shutdown Tracker Functionality, Possibilities, and Next Product Direction.pdf` — task-progress, blocker/action, handover/evidence and Project-handoff research.
6. `Built-in Communications for Shutdown Tracker.pdf` — communications research supporting entity-linked discussion rather than generic chat.

## Supporting / overlapping research

- `UX and UI Research for Shutdown Tracker.pdf` — overlapping UX/UI support; prefer the Operational Interaction packet for current UX evidence.
- `deep-research-report.md` — broad synthesis; retain as supporting provenance rather than first-line authority.

## Current Project-handoff interpretation

The research supports a narrow direct-input boundary because Project tracking fields interact and Project recalculates dependent values. The accepted repository interpretation is now:

- Shutdown Tracker approves exact execution inputs;
- Microsoft Project may recalculate a disposable candidate schedule;
- the planner reviews and controls adoption.

Do not reinterpret older “no date movement” wording as a prohibition on Microsoft Project recalculating a separate candidate.
