# ADR-007: Data Ownership and Schedule Authority

Status: Draft

## Context

Shutdown Tracker must avoid becoming an implicit scheduling system.

## Decision

Microsoft Project remains the schedule authority. Shutdown Tracker is the live execution and reporting authority.

## Consequences

- Imported schedule data is snapshot data.
- Shutdown Tracker does not live-feed Microsoft Project.
- Shutdown Tracker must not calculate critical path or move schedule dates automatically.
