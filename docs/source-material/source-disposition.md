# Source Disposition Register

This register records how known external sources should be treated. It intentionally does not preserve the raw files in the application repository.

## Disposition meanings

| Disposition | Meaning |
| --- | --- |
| Primary supporting research | Strong supporting evidence for an accepted decision area; current ADR/product docs still take precedence |
| Supporting / overlapping | Useful provenance or unique detail, but prefer a later or more focused source |
| Historical reference | Useful for chronology, vocabulary, or decision provenance; not current authority |
| Archive only | Retain externally if needed; do not use as active source |
| External schedule evidence | Real Project schedule evidence used for research; never a synthetic fixture |

## Current disposition

| Source | Disposition | Notes |
| --- | --- | --- |
| `Microsoft Project import and export architecture for a shutdown live-tracking application.pdf` | Primary supporting research | Microsoft Project/MSPDI handoff and authority evidence |
| `Shutdown Live-Tracking Platform Research.pdf` | Primary supporting research | Broad platform architecture/UX baseline |
| `UX and Operational Interaction Design Research Packet for Shutdown Tracker.pdf` | Primary supporting research | Preferred operational UX packet |
| `Planner-Configurable Operational Structures Derived from Microsoft Project XML.pdf` | Primary supporting research | Operational-mapping evidence from supplied schedules |
| `Shutdown Tracker Functionality, Possibilities, and Next Product Direction.pdf` | Primary supporting research | Later product-direction packet |
| `Built-in Communications for Shutdown Tracker.pdf` | Primary supporting research | Communications research |
| `UX and UI Research for Shutdown Tracker.pdf` | Supporting / overlapping | Overlaps with stronger operational-interaction packet |
| `deep-research-report.md` | Supporting / overlapping | Broad synthesis |
| `Live project tracking app.txt` | Historical reference | Earlier prototype lineage |
| `Live_Project_Tracker_Chat_Summary.pdf` | Historical reference | Earlier project chronology |
| `Project Management Software.txt` | Historical reference | Product-origin discussion |
| `Project Review.txt` | Historical reference | Repository review chronology |
| `Project review continuation.txt` | Historical reference | Implementation/review chronology |
| `Shutdown Tracker Communications Layer Visual Review Brief.pdf` | Historical reference | Supporting communications visual-review material |
| `Review zip file contents.txt` | Historical reference | Prompt/provenance, not research evidence itself |
| `AI Simulation Prototype Review.txt` | Archive only | Misleading filename; historical status material |
| `UI Design Render Request.txt` | Archive only | Misleading filename; historical backend/test material |
| `Network Diagram for Design.txt` | Archive only | No substantive design content |
| `Shutdown Tracker Messaging Design.txt` | Archive only | Superseded by current product docs |
| `NEW_5874946-KILN-WG047K-KLN021_v3 - Tagging setup change - tagging hours update - resource level 3(1).xml` | External schedule evidence | Real schedule; keep outside Git |
| `NEW_6183209-CALCINER-WG050-CLW001(1).xml` | External schedule evidence | Real schedule; keep outside Git |
| `NEW_6477046-BOILER-WG110-BLB001(1).xml` | External schedule evidence | Real schedule; keep outside Git |

## Repository rule

Do not reintroduce raw source archives or real Project schedules into Git. Use external controlled storage and retain only sanitized summaries, non-sensitive manifests, source names, and hashes where required for provenance.
