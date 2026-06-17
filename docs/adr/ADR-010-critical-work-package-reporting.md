# ADR-010: Critical Work Package Reporting

Status: Draft

## Context

Operations teams need focused reporting for critical work without turning the app into a critical-path engine.

## Decision

Model Critical Watchlists as named reporting lists and Critical Work Packages as reporting objects. A Critical Work Package may be sourced from one imported summary task plus descendants, or from multiple imported summary tasks where one reporting group spans schedule boundaries. Arbitrary manual leaf-task grouping should be deferred unless required by pilot feedback. Reporting policies are configurable and generic.

## Consequences

- Critical Work Packages are not scheduling objects.
- Four-hour reporting is a configurable template, not a hardcoded behavior.
- Problems, Actions, Evidence, Handover entries, and Critical Updates may link to Critical Work Packages.
- Future grouping options must remain generic.
