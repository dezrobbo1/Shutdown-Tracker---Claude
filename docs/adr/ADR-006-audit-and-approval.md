# ADR-006: Audit and Approval

Status: Draft

## Context

Execution updates, evidence, handover notes, and exports require traceability and controlled approval.

## Decision

Record audit events for important state changes and require approval batches for export-eligible updates.

## Consequences

- Audit schema must be designed early.
- Export previews should show exactly what will be sent back to Microsoft Project.
- Only approved leaf-task progress and actual fields may be eligible for export.
