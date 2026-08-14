# Reference Source Catalogue

Reference material in the current archive preserves project history, design/review discussions, prototype notes, communications design material, and real Microsoft Project schedule examples.

The archive is intentionally preserved unchanged. The catalogue below distinguishes useful reference material from misleading, trivial, or superseded chat exports so those files are not accidentally treated as current design authority.

## Active historical/design references

- `Live project tracking app.txt` — historical pointer to the earlier Live Project Tracker summary; useful only for lineage to the pre-platform prototype.
- `Live_Project_Tracker_Chat_Summary.pdf` — historical project summary; useful for origin/chronology, not current architecture.
- `Project Management Software.txt` — historical discussion source; retain for product-origin context only.
- `Project Review.txt` — historical project-review conversation; retain for chronology and decision provenance.
- `Project review continuation.txt` — continuation of project/repository review; retain for chronology and implementation-review provenance.
- `Shutdown Tracker Communications Layer Visual Review Brief.pdf` — useful visual-review reference for communications UX; supporting source only.
- `Review zip file contents.txt` — contains the focused communications deep-research prompt; retain as prompt/provenance, not as research evidence itself. The resulting research/product docs are stronger sources.

## Archive-only / misleading-name artefacts

These remain inside the immutable ZIP but should not be used as active design sources:

- `AI Simulation Prototype Review.txt` — despite its filename, the content is a CI/export-integrity status note about PR #49. It is not an AI simulation or UI prototype review.
- `UI Design Render Request.txt` — despite its filename, the content is a backend/manual Microsoft Project export test runbook for PR #48. It is not a UI design brief or render request.
- `Network Diagram for Design.txt` — the retained content is only an acknowledgement (`Understood.`) and contains no substantive design information.
- `Shutdown Tracker Messaging Design.txt` — despite its filename, the content is primarily the PR #44 documentation-completion summary and frontend-cleanup direction. Current product docs under `docs/product` supersede it.

## Standalone HTML prototype lineage

A separate historical standalone HTML prototype family exists outside the current 2026-08-13 source bundle. The prototype history identifies files such as `shutdown_console_v34_1_qa_stable.html`, and a later File Library artefact `shutdown_console_v34_2_fake_mobile_login.html` is also available.

These HTML files are **historical functional/reference prototypes only**. They may be useful for workflow vocabulary, sample scenarios, and visual comparison, but they are not current production architecture, current UI authority, or a source for scheduling/write-back behavior. Current UI direction belongs in `docs/product`, especially the frontend visual-review, design-language/status, and UX anti-slop documents.

If a newer one-off HTML mockup from a recent UI-design conversation is recovered, classify it here as `historical visual reference` unless a current product document explicitly adopts it.

## Microsoft Project XML reference schedules

The archive contains three real reference schedules under `reference/microsoft-project-xml/`:

- `NEW_5874946-KILN-WG047K-KLN021_v3 - Tagging setup change - tagging hours update - resource level 3(1).xml`
- `NEW_6183209-CALCINER-WG050-CLW001(1).xml`
- `NEW_6477046-BOILER-WG110-BLB001(1).xml`

These schedules are high-value reference evidence for Project parsing, hierarchy, custom-field, resource-group, and operational-mapping research. They must not be treated as synthetic test fixtures or committed elsewhere as application test data.
