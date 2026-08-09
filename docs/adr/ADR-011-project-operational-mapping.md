# ADR-011: Project Operational Mapping

Status: Draft

## Context

Real Microsoft Project schedules use different planning conventions for operational classification. A task may be classified by a custom task field such as `Text30 / Assigned Department`, by WBS or summary-task hierarchy, or indirectly through assigned resources and the standard Resource `Group` field. The same conceptual label cannot be assumed to live in the same Project field across sites, contractors, or shutdown templates.

Shutdown Tracker needs those Project-derived classifications for execution scope, filtering, grouping, Saved Views, Critical Watch, reporting, and responsibility configuration without hardcoding one customer's Project template or taking ownership of schedule calculation.

## Decision

Introduce a configurable **Project Operational Mapping** layer between immutable imported Project snapshots and Shutdown Tracker execution features.

Microsoft Project remains the owner of imported source facts, structure, resource/assignment relationships, and custom-field values. Shutdown Tracker owns only the explicit operational interpretation configured over those facts.

The initial mapping model supports:

1. direct imported task fields/custom fields;
2. WBS/hierarchy/selected summary-task ancestry;
3. task assignments resolved through the assigned resource's standard Project `Group` field.

Planner-configured Operational Categories may be single- or multi-valued and may feed operational Scope, filters, grouping, Saved Views, Critical Watch selection/scoping, reporting, and related operational-record context.

Original imported source values are immutable. Tracker display aliases and higher-level operational roll-ups are stored separately.

Mapping definitions live in versioned Import Profiles and are revalidated against every new immutable Project snapshot. Missing/changed sources, new values, hierarchy changes, and probable field moves are surfaced for review. Uncertain mappings are never silently remapped.

Project-derived category membership is not application authorisation. Visibility/relevance, operational responsibility, update permission, review permission, and export authority remain separate Tracker concepts.

## Consequences

- Shutdown Tracker can support different Microsoft Project templates without bespoke code for each site's field conventions.
- Resource-derived classifications must support multiple values because one task may have assignments from multiple Resource Groups.
- Formula-backed Project custom fields may be consumed as evaluated read-only classification values, but Shutdown Tracker does not implement the Project formula engine.
- Hierarchy-derived categories require explicit structural configuration rather than assuming one universal OutlineLevel meaning.
- Problems, Actions, Evidence, Handover, and related operational records can retain historical mapped-category context while current classification is resolved from the active snapshot.
- Mapping provenance must be retained so users can determine why a task belongs to a category.
- Mapping/profile/value-alias/responsibility changes require audit history.
- Operational Mapping does not grant permission to calculate CPM, critical path, float, resource levelling, schedule optimisation, date movement, Project formula evaluation, or automatic Project write-back.

## MVP boundary

MVP includes Source Catalogue discovery, Operational Categories, the three initial source modes, single/multi-value membership, aliases/roll-ups, global Scope, Saved Views, versioned Import Profiles, re-import mapping health, execution-readiness checks, provenance, and audit.

Complex expression/rules engines, advanced assignment custom-field derivation, automatic responsibility assignment, milestone event watch, baseline analysis, and advanced Project schedule-context analysis are deferred.
