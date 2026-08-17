# Reference Source Catalogue

Reference material preserves project history, design/review discussions, prototype notes, communications material, and Microsoft Project schedule evidence. The raw files are held externally; this repository keeps only the catalogue and current sanitized design/reference artifacts.

## Historical/design references

Known external sources include:

- `Live project tracking app.txt`
- `Live_Project_Tracker_Chat_Summary.pdf`
- `Project Management Software.txt`
- `Project Review.txt`
- `Project review continuation.txt`
- `Shutdown Tracker Communications Layer Visual Review Brief.pdf`
- `Review zip file contents.txt`

These are chronology/provenance references. Current ADR/product documents take precedence.

## Misleading or archive-only historical names

The following known source names are not current authority:

- `AI Simulation Prototype Review.txt` — historical CI/export-integrity material, not a current AI simulation design source.
- `UI Design Render Request.txt` — historical backend/manual Project test guidance, not a current UI authority source.
- `Network Diagram for Design.txt` — no substantive design content.
- `Shutdown Tracker Messaging Design.txt` — historical product-doc completion summary; current communications docs supersede it.

## Standalone HTML prototype lineage

Historical standalone HTML prototypes may be useful for workflow vocabulary and visual comparison, but are not current production architecture or Project-handoff authority.

## Microsoft Project schedule evidence

Three real Project XML schedules were used to support operational-mapping research:

- `NEW_5874946-KILN-WG047K-KLN021_v3 - Tagging setup change - tagging hours update - resource level 3(1).xml`
- `NEW_6183209-CALCINER-WG050-CLW001(1).xml`
- `NEW_6477046-BOILER-WG110-BLB001(1).xml`

They are valuable evidence for hierarchy, custom fields, resource groups, assignments, and mapping variability. They are **not synthetic fixtures** and must remain outside the application repository.

Repository documents may record sanitized aggregate findings from those files, such as hierarchy depth and category coverage, without retaining the raw schedules.
