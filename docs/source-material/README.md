# Source Material Library

This folder holds the original source material used to inform Shutdown Tracker research, product decisions, design reviews, and Microsoft Project integration work.

The curated and authoritative documentation remains under `docs/product`, `docs/architecture`, `docs/adr`, and `docs/research`. Material in this source library is evidence and historical reference; it does not override current product or architecture decisions.

## Structure

- `inbox/` — temporary landing area for newly uploaded source bundles.
- `research/` — catalogue of research reports and deep-research source documents.
- `reference/` — catalogue of project history, design/review material, and real Microsoft Project reference schedules.
- `archive/` — immutable source bundles preserved as uploaded.

## Current source bundle

The current bundle is `archive/Shutdown_Tracker_Current_Project_Sources_2026-08-13.zip`.

It contains 22 source files split internally into `research/` and `reference/`, including three real Microsoft Project XML schedules under `reference/microsoft-project-xml/`.

The archive is retained as the canonical raw package because several Project XML files are large and highly compressible. This avoids duplicating tens of megabytes of raw schedule XML in Git while preserving the original files and checksums.

See the category catalogues for the file inventory and purpose of each source group.
