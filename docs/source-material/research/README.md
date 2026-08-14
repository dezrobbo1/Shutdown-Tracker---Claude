# Research Source Catalogue

Research source documents in the current archive support architecture, Microsoft Project integration, UX/UI, communications, operational mapping, and product-direction decisions.

The archive is preserved unchanged. This catalogue records how each source should now be treated so overlapping packets do not compete with current product/architecture documentation.

## Primary supporting research

1. `Microsoft Project import and export architecture for a shutdown live-tracking application.pdf` — primary supporting research for the Microsoft Project/MSPDI boundary.
2. `Shutdown Live-Tracking Platform Research.pdf` — broad platform architecture and operational UX research baseline.
3. `UX and Operational Interaction Design Research Packet for Shutdown Tracker.pdf` — primary UX/interaction research packet for console/mobile, attention, offline states, import/export review, accessibility, and field usability.
4. `Planner-Configurable Operational Structures Derived from Microsoft Project XML.pdf` — primary operational-mapping research derived from the three real XML schedules.
5. `Shutdown Tracker Functionality, Possibilities, and Next Product Direction.pdf` — later product-direction research covering task-progress review, blockers/actions, handover/evidence and controlled Project handoff.
6. `Built-in Communications for Shutdown Tracker.pdf` — primary communications research supporting entity-linked discussion rather than generic chat.

## Supporting / overlapping research

7. `UX and UI Research for Shutdown Tracker.pdf` — supporting UX/UI packet. It overlaps materially with `UX and Operational Interaction Design Research Packet for Shutdown Tracker.pdf`; use the Operational Interaction packet as the stronger default UX research source unless a unique point is needed from this document.
8. `deep-research-report.md` — broad supporting synthesis. It overlaps with the platform architecture and later product-direction packets; retain for provenance, but do not use it as the first source when a more focused packet covers the same decision.

## Authority rule

These research packets explain evidence and reasoning. Accepted conclusions should be reflected in `docs/research`, `docs/product`, `docs/architecture`, or an ADR before they are treated as current direction.

Where two research packets overlap, prefer the later or more focused packet, then verify that the accepted conclusion is represented in current authoritative documentation. Do not treat duplicated recommendations across packets as independent evidence.
