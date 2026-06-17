# ADR-010: Critical Work Package Reporting

Status: Draft

## Context

Operations teams need focused reporting for critical work without turning the app into a critical-path engine.

## Decision

Model Critical Watchlists as named reporting lists and Critical Work Packages as reporting objects. Default Critical Work Package source is a selected Microsoft Project summary task plus descendants. Reporting policies are configurable and generic.

## Consequences

- Critical Work Packages are not scheduling objects.
- Four-hour reporting is a configurable template, not a hardcoded behavior.
- Problems, Actions, Evidence, Handover entries, and Critical Updates may link to Critical Work Packages.
- Future grouping options must remain generic.
