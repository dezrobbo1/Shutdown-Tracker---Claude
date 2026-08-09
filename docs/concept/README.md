# Concept and Architecture Pack v1.3 Summary

Shutdown Tracker is a live execution tracking platform for shutdown, turnaround, outage, and construction work. It uses Microsoft Project schedule snapshots as source material while keeping Microsoft Project as the schedule authority.

## Application Experiences and Delivery Channels

Shutdown Tracker has two task-appropriate application experiences backed by the same platform, project data, permissions, execution records, and audit model:

- **Master Console** — desktop-optimised for shutdown control, planners, coordinators, supervisors, package owners, and managers. It must be accessible through a desktop browser and may also be delivered as an installable desktop application without creating a separate product model.
- **Field App** — mobile-optimised for field supervisors, leading hands, contractors, inspectors, and execution crews. It must be accessible through a mobile browser/PWA and may also be delivered as an installable iOS/Android application without creating a separate product model.

Browser and installed delivery channels must remain interchangeable at the platform level: the same authenticated user, project, permissions, operational records, and workflow authority apply regardless of delivery channel. Device-specific capabilities may differ where justified, especially for offline storage, camera/evidence capture, notifications, and background sync.

The Master Console remains desktop-first even when browser-delivered; it is not required to collapse into the Field App on small screens. The Field App remains field/mobile-first rather than reproducing the complete control-room workspace.

## Master Console

The Master Console focuses on live execution tracking, operational reporting, problem/action follow-up, evidence review, handover visibility, planner/supervisor review, Project Operational Mapping, and controlled export approval.

Baseline zones:

- Today
- Tasks
- Problems
- Evidence
- Exports

## Field App

The Field App supports assigned work, task updates, work state changes, problems, actions, evidence capture, handover notes, and offline/sync visibility.

Baseline zones:

- My Work
- Today
- Problems
- Evidence
- Sync

Offline-capable field workflows are a core direction. Delivery technology must not create a separate field authority or data model.

## Microsoft Project Boundary

Microsoft Project remains the schedule authority. Shutdown Tracker imports immutable schedule snapshots and does not live-feed changes back into Microsoft Project. Approved progress and actuals may be exported in controlled batches using MSPDI/XML.

The product must not perform CPM, critical-path calculation, float calculation, resource levelling, recovery scheduling, schedule optimisation, automatic date movement, hidden schedule recalculation, automatic predecessor/constraint changes, or native `.mpp` writing.

## Project Operational Mapping

Microsoft Project supplies source facts, structure, and classifications. Shutdown Tracker may add a planner-configured operational interpretation without rewriting those imported source facts.

The MVP mapping layer should support three source modes:

1. direct imported task fields, including appropriate Project custom fields / ExtendedAttributes;
2. task hierarchy, WBS, and selected summary-task ancestry;
3. task assignments resolved through the assigned resource's standard Project `Group` field.

A planner can create a named **Operational Category** such as Assigned Department, Work Group, Area, Contractor, Day, or Work Package and map it to an available Project source. Category names are Tracker configuration; source values remain exactly as imported.

Operational Categories may be:

- single-valued or multi-valued;
- filterable and groupable;
- eligible for global operational Scope and Saved Views;
- available to Critical Watch, Problems, Actions, Evidence, Handover, reporting, and appropriate mobile relevance;
- optionally given friendly display aliases and higher-level operational roll-ups without changing the source value.

Resource-derived categories must support multiple values because a task may legitimately have assignments from more than one Resource Group.

Formula-backed or Project-calculated custom fields may be used as read-only classification/filter context where appropriate, but Shutdown Tracker must not reproduce the Microsoft Project formula engine or treat such values as permanent cross-snapshot identity without explicit justification.

### Import Profiles and re-import safety

Operational mappings belong to versioned **Import Profiles** so planning conventions can be reused across projects that share a Project template.

Every new Project snapshot must revalidate active mappings. The application must detect missing or changed fields, aliases/configuration changes, new/disappearing source values, hierarchy changes, and probable source-field moves. It must never silently remap an uncertain source.

Mapping health should distinguish healthy mappings, healthy mappings with new values, changed configuration, confirmation-required mappings, broken mappings, orphaned values, and profile mismatch.

### Scope and Saved Views

Configured categories feed a coherent operational **Scope** rather than isolated grid filters. Scope may reduce Today, Critical Watch, Tasks, Problems, Actions, Evidence, Handover, operational counts, and reports together where appropriate.

Saved Operational Views may persist scope, filters, grouping, sorting, visible columns, execution/review state, and time windows. Private, shared, and role-default views may be supported under permissions.

### Classification is not authorisation

Project-derived classification never grants application authority by itself.

A category such as `Work Group = CVM MECH` may make a task relevant or visible to a user and may be used to configure a Tracker-owned responsibility scope. It does not automatically grant task-update, supervisor-review, planner-review, approval, export, or administrative permission.

Visibility, responsibility, update permission, and approval permission remain separate concepts controlled by Shutdown Tracker roles, explicit responsibility/delegation, and project policy.

### Historical context and provenance

Problems, Actions, Evidence, Handover, and other operational records should retain enough category context to explain their historical operational ownership even after a later Project re-import changes current classification.

Resolved category membership must retain provenance sufficient to answer: **Why is this task in this category?**

## Critical Work Package Reporting

A Critical Watchlist is a named operational reporting list for one shutdown, area, or purpose. A Critical Work Package is a reporting object, not a scheduling object.

The default Critical Work Package source is a selected Microsoft Project summary task plus all descendants. The MVP should also support one Critical WP sourced from multiple summary tasks where a reporting group crosses summary boundaries. Configured Operational Categories and structural mappings may assist selection and scoping, but Critical Watch must never be derived from Shutdown Tracker CPM/float calculations.

Reporting policies must be configurable and generic. Supported policy types should include none, ad hoc, fixed interval, fixed times, shift-based, event-triggered, and custom. Four-hour reporting is one template, not a hardcoded system rule.

## MVP Scope

The MVP should focus on:

- immutable Microsoft Project snapshot import and controlled MSPDI/XML export;
- imported tasks, resources, assignments, hierarchy, and relevant custom-field metadata;
- Project source discovery / Source Catalogue;
- versioned Import Profiles;
- planner-configurable Operational Categories from direct task fields, hierarchy/summary ancestry, and assigned-resource `Group`;
- single- and multi-value task classification;
- optional value aliases and operational roll-ups while preserving original source values;
- global operational Scope;
- Saved Operational Views;
- mapping-health and re-import validation with no uncertain silent remapping;
- execution-readiness/data-quality checks for required operational classification;
- historical category context and mapping provenance;
- task execution state and structured progress;
- supervisor and planner review;
- Problems, Actions, Evidence, and Handover;
- Critical Watchlists, Critical Work Packages, and configurable reporting policies;
- approval/export batches and audit events;
- users, roles, permissions, responsibility/delegation foundations, and offline sync foundations;
- browser delivery for the Master Console and Field App, with installable desktop and mobile delivery channels supported by the product model rather than treated as separate products.

Deferred beyond the initial mapping MVP include advanced assignment-custom-field derivations, complex expression/rules engines, automatic responsibility assignment, milestone event watch, baseline analysis, and other advanced Project-context features.

Explicitly excluded: Gantt/CPM/dependency-map scheduling UI, scheduler/recovery engine, resource levelling, AI schedule prediction/optimisation, automatic schedule movement, Project formula evaluation, Project Critical/slack-driven Critical Watch logic, automatic permissions from category membership, hidden Project write-back, native `.mpp` writing, custom dashboard builder, and generic chat-clone behavior.
