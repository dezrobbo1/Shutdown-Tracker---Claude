# Source Disposition Register

This register is the cleanup layer between the immutable raw source archive and the curated Shutdown Tracker documentation.

It does not delete or rewrite original source files. It records whether a source should be treated as primary supporting research, supporting/overlapping material, historical reference, archive-only noise, or a missing/external historical artefact.

## Disposition meanings

| Disposition | Meaning |
| --- | --- |
| Primary supporting research | Strong raw research source for an accepted decision area. Current product/ADR docs still take precedence. |
| Supporting / overlapping | Retain for provenance or unique details, but prefer a more focused or later source for normal use. |
| Historical reference | Useful for chronology, workflow vocabulary, or why a decision was made; not current authority. |
| Archive only | Preserved in the ZIP, but too trivial, misleadingly named, or superseded to use as an active source. |
| External historical artefact | Known from File Library/history but not included in the current archive. Preserve only if deliberately added later. |

## Current archive disposition

| Source | Disposition | Notes |
| --- | --- | --- |
| `Microsoft Project import and export architecture for a shutdown live-tracking application.pdf` | Primary supporting research | Microsoft Project/MSPDI handoff and authority boundary. |
| `Shutdown Live-Tracking Platform Research.pdf` | Primary supporting research | Broad platform architecture/UX baseline. |
| `UX and Operational Interaction Design Research Packet for Shutdown Tracker.pdf` | Primary supporting research | Preferred UX/interaction packet. |
| `Planner-Configurable Operational Structures Derived from Microsoft Project XML.pdf` | Primary supporting research | Preferred operational-mapping packet based on real XML. |
| `Shutdown Tracker Functionality, Possibilities, and Next Product Direction.pdf` | Primary supporting research | Later product-direction packet. |
| `Built-in Communications for Shutdown Tracker.pdf` | Primary supporting research | Preferred communications research packet. |
| `UX and UI Research for Shutdown Tracker.pdf` | Supporting / overlapping | Overlaps materially with the Operational Interaction packet. |
| `deep-research-report.md` | Supporting / overlapping | Broad synthesis; prefer focused packets for normal use. |
| `Live project tracking app.txt` | Historical reference | Pointer/summary of the earlier Live Project Tracker stage. |
| `Live_Project_Tracker_Chat_Summary.pdf` | Historical reference | Earlier product chronology. |
| `Project Management Software.txt` | Historical reference | Product-origin discussion. |
| `Project Review.txt` | Historical reference | Project/repository review chronology. |
| `Project review continuation.txt` | Historical reference | Continuation of implementation/review chronology. |
| `Shutdown Tracker Communications Layer Visual Review Brief.pdf` | Historical reference | Supporting communications visual-review material. |
| `Review zip file contents.txt` | Historical reference | Communications deep-research prompt/provenance, not evidence itself. |
| `AI Simulation Prototype Review.txt` | Archive only | Content is actually CI/export-integrity status for PR #49; filename is misleading. |
| `UI Design Render Request.txt` | Archive only | Content is actually PR #48 backend/manual Project export test guidance; not UI design. |
| `Network Diagram for Design.txt` | Archive only | Contains only an acknowledgement and no substantive design content. |
| `Shutdown Tracker Messaging Design.txt` | Archive only | Mostly PR #44 documentation-completion summary; current product docs supersede it. |
| `NEW_5874946-KILN-WG047K-KLN021_v3 - Tagging setup change - tagging hours update - resource level 3(1).xml` | Historical reference | High-value real Project schedule evidence; do not use as a synthetic fixture. |
| `NEW_6183209-CALCINER-WG050-CLW001(1).xml` | Historical reference | High-value real Project schedule evidence; do not use as a synthetic fixture. |
| `NEW_6477046-BOILER-WG110-BLB001(1).xml` | Historical reference | High-value real Project schedule evidence; do not use as a synthetic fixture. |

## Standalone HTML prototypes

The standalone Shutdown Console HTML family is deliberately outside the current raw source archive. Historical material identifies `shutdown_console_v34_1_qa_stable.html` as the frozen standalone baseline, and a later File Library artefact `shutdown_console_v34_2_fake_mobile_login.html` is also available.

Disposition: **External historical artefact**.

The standalone HTML is useful for:

- workflow vocabulary;
- historical feature discovery;
- sample operational scenarios;
- visual comparison when discussing UI direction.

It must not be used as:

- current production architecture;
- current frontend implementation authority;
- schedule/CPM/resource-levelling direction;
- current Project write-back behaviour;
- authentication/offline/data-model authority.

The consolidated concept architecture explicitly froze the standalone HTML as historical reference and moved current UI authority into the React/Vite product direction and current product/design documents.

## Cleanup result

No raw files were deleted from the immutable archive in this pass. The cleanup instead removes ambiguity by demoting misleading or redundant artefacts from the active catalogues and documenting which overlapping research packet should normally be preferred.
