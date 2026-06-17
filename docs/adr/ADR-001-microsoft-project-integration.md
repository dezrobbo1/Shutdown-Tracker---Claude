# ADR-001: Microsoft Project Integration

Status: Draft

## Context

Shutdown Tracker must import Microsoft Project schedule snapshots and later export approved updates back to Microsoft Project.

## Decision

Use MPXJ for Microsoft Project import/export work. Use MSPDI/XML for export back to Microsoft Project. Do not write native MPP files.

## Consequences

- Source files and generated exports should be stored as immutable objects.
- Parse warnings must be captured for review.
- Manual Microsoft Project round-trip testing is required.
- Native MPP writing is out of scope.
