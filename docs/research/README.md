# Research

## Packet 01: Microsoft Project Import/Export Architecture

Key decisions:

- Use MPXJ for Microsoft Project parsing and export support.
- Import immutable source files and preserve parse warnings.
- Store snapshots rather than treating imported schedules as live-linked documents.
- Export approved progress/actual fields through MSPDI/XML.
- Do not write native MPP files.

## Packet 02: End-to-End Application Architecture and Lifecycle

Key decisions:

- Start with a monorepo.
- Use a modular monolith first.
- Separate the API service from the project import/export worker.
- Use PostgreSQL for relational operational data.
- Use object storage for evidence, source files, and export artifacts.

## Packet 03: UX/UI and Operational Interaction Design

Key decisions:

- Provide separate Master Console and Mobile Field App experiences.
- Keep the Master Console optimized for coordination, review, approval, and reporting.
- Keep the mobile PWA optimized for assigned work, fast field updates, evidence capture, and visible sync state.
- Avoid schedule-authoring UI in the MVP.

## Packet 04: Configurable Critical Work Package Reporting

Key decisions:

- Treat Critical Work Packages as reporting objects.
- Treat Critical Watchlists as named operational reporting lists.
- Make reporting policies configurable and generic.
- Link Problems, Actions, Evidence, Handover entries, and Critical Updates to Critical Work Packages where needed.
- Do not hardcode company-specific or asset-specific reporting behavior.
