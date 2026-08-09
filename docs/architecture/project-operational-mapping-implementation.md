# Project Operational Mapping — Implementation Architecture

## Status

Architecture/design source for the implementation of [Project Operational Mapping](../product/project-operational-mapping.md) under [ADR-011](../adr/ADR-011-project-operational-mapping.md).

This document defines the intended backend/domain/API/migration shape before production implementation begins. It does not itself change database schema or runtime behaviour.

## Design goals

The implementation must:

- preserve imported Microsoft Project values as immutable snapshot facts;
- expose what was actually present in each imported snapshot;
- let planners define Tracker-owned operational meaning without rewriting Project data;
- support direct task-field, hierarchy/summary-ancestry, and assigned-resource `Group` mappings;
- support one or many category values per task;
- retain provenance for every resolved membership;
- survive Project re-import safely through explicit mapping-health validation;
- separate classification from application permissions;
- make Scope and Saved Views use the same underlying category/filter model;
- avoid adding schedule calculation, formula evaluation, or hidden write-back.

## Existing foundation

The current schema already provides the immutable source side of the boundary:

- `project_snapshots` identifies immutable Project snapshots;
- `imported_tasks` stores imported task structure and first-class task facts;
- `imported_resources` stores imported resource rows;
- `imported_assignments` stores task/resource assignment relationships;
- `imported_extended_attributes` stores imported custom/extended attributes;
- `task_lineage_links` stores reviewable task relationships across snapshots.

The current imported tables must remain snapshot facts. Project Operational Mapping must not turn them into mutable operational configuration.

The existing `raw_data` JSON columns may continue to preserve parser payload not yet promoted to first-class columns, but mapping-critical source facts should become explicitly queryable when implementation proves they are required for stable behaviour.

## Logical architecture

```text
Microsoft Project source file
        |
        v
project-worker parse
        |
        v
immutable Project snapshot facts
(tasks/resources/assignments/extended attributes)
        |
        +------------------------------+
        |                              |
        v                              v
Source Catalogue                mapping validator
        |                              |
        v                              v
Import Profile/version ------ mapping health
        |
        v
Operational Category definitions
        |
        v
category source mappings
        |
        v
snapshot-specific membership resolution
        |
        +-------------+---------------+----------------+
        |             |               |                |
        v             v               v                v
Scope/Saved Views   Critical Watch   reporting    responsibility context
        |
        v
Problems / Actions / Evidence / Handover historical category context
```

## Ownership boundaries

### Project worker

The project worker owns extraction of Microsoft Project file facts through MPXJ.

For Operational Mapping, the worker should eventually return enough normalized source metadata to support the Source Catalogue, including:

- task field/custom-field definitions and populated values;
- alias/type/formula/lookup metadata where available;
- task hierarchy/WBS/summary ancestry;
- resource `Group` values;
- assignment relationships needed to derive task-to-resource-group membership.

The worker must not interpret those values as Shutdown Tracker categories. It returns source facts only.

### API

The API owns:

- Source Catalogue projection/query;
- Import Profile and version lifecycle;
- Operational Category lifecycle;
- category source mapping configuration;
- category value aliases/roll-ups;
- mapping activation and validation decisions;
- resolved category membership persistence/orchestration;
- Scope and Saved View definitions;
- responsibility-scope/delegation configuration;
- audit events;
- authorization.

The API must not call MPXJ directly.

## Source Catalogue design

The Source Catalogue is a read model over one immutable Project snapshot. It should not be a second mutable source-of-truth copy of Project data.

For each discovered candidate source, expose at least:

- snapshot ID;
- entity type (`task`, `resource`, `assignment`, structural/hierarchy source);
- normalized source key;
- Project field identifier where applicable;
- alias/display name where present;
- source value type;
- formula-backed flag where known;
- lookup-controlled flag where known;
- populated entity count;
- total relevant entity count;
- distinct value count;
- representative values;
- current mapping references;
- mapping-health summary.

### Source identity

Do not use alias alone as source identity.

A direct-field source identity should preserve enough evidence to revalidate later, for example:

```text
entity type + Project field slot/type + field_id + alias + custom-field GUID/LTUID where present
```

Not every source will provide all identifiers. Re-import validation therefore uses a signature/evidence model rather than assuming one globally stable Project field ID.

### Discovery rule

Build the catalogue from both:

1. configured/imported field definitions; and
2. fields actually populated on imported entities.

This prevents useful unnamed/unconfigured custom fields from being omitted.

## Proposed domain model

Physical names below are implementation candidates. The first coding migration may refine exact names/columns, but the ownership and relationships are intended to remain stable.

### `import_profiles`

Reusable project-template mapping identity.

Suggested responsibilities:

- stable profile ID;
- name/description;
- owner/project or reusable scope;
- active/retired state;
- created/updated audit metadata.

A profile is mutable only through creating a new profile version for mapping semantics that affect classification.

### `import_profile_versions`

Immutable versioned configuration snapshot.

Suggested fields/concepts:

- profile/version identity;
- version number;
- lifecycle state (`draft`, `active`, `superseded`, `retired` as appropriate);
- created by/at;
- activation metadata;
- optional compatibility notes.

An active project snapshot should resolve against one explicit profile version, not “whatever the profile currently means”.

### `operational_categories`

Stable Tracker-owned category identity.

Examples: Assigned Department, Work Group, Area.

Suggested concepts:

- project/profile scope;
- display name and description;
- cardinality (`single`, `multi`);
- usage flags or policy object for filter/group/scope/saved-view/report/mobile/Critical-Watch eligibility;
- active/retired state.

Category identity should remain stable while source mapping changes are represented through versioned mapping definitions.

### `category_source_mappings`

Defines how one category is derived for one Import Profile version.

Required source modes for MVP:

- `DIRECT_TASK_FIELD`;
- `TASK_HIERARCHY`;
- `RESOURCE_GROUP_VIA_ASSIGNMENT`.

Suggested common concepts:

- mapping ID;
- category ID;
- import profile version ID;
- source mode;
- expected source signature JSON/object;
- derivation/configuration JSON/object constrained by source mode;
- expected cardinality;
- null/unmapped policy;
- created by/at.

The configuration should be typed in application code even if persisted partly as JSON. Avoid an unconstrained generic rules blob.

### `category_value_configs`

Tracker-owned configuration for source values.

Suggested concepts:

- category ID;
- canonical imported source value;
- optional display label;
- optional description;
- optional operational parent/roll-up identity;
- active/inactive-for-current-snapshot state should be derived rather than deleting history;
- created/changed audit metadata.

A uniqueness rule should prevent duplicate configuration for the same category + source value.

### `task_category_memberships`

Snapshot-specific resolved classification.

This is the key bridge between immutable Project facts and operational use.

Suggested concepts:

- project ID;
- project snapshot ID;
- imported task ID;
- operational category ID;
- source value;
- optional normalized value key;
- mapping/profile version that produced the membership;
- source mode;
- provenance payload/reference;
- resolved timestamp.

For a single-valued category, enforce at most one resolved value per task/category/snapshot. For a multi-valued category, allow multiple distinct source values.

Resolved membership must be regenerated for a new snapshot; never mutate a prior snapshot's membership rows to match the new Project file.

### `project_mapping_activations`

Binds a project/snapshot to the Import Profile version used operationally.

Suggested concepts:

- project ID;
- project snapshot ID;
- import profile version ID;
- activation state;
- activated by/at;
- mapping-health gate status;
- supersession relationship when replaced.

This makes the active interpretation explicit and auditable.

### `mapping_validation_events`

Immutable validation findings for a snapshot/profile-version pair.

Suggested concepts:

- mapping/category/source reference;
- snapshot/profile version;
- health state;
- finding code;
- evidence/details JSON;
- detected at;
- resolution state;
- reviewed/confirmed/overridden by and at;
- superseded finding reference where applicable.

Health values should align with the product contract:

- `HEALTHY`;
- `HEALTHY_NEW_VALUES`;
- `WARNING_SOURCE_CHANGED`;
- `CONFIRMATION_REQUIRED`;
- `BROKEN`;
- `ORPHANED_VALUES`;
- `PROFILE_MISMATCH`.

### `saved_views` and `saved_view_filters`

Saved Views should persist one shared filter model rather than embed bespoke query strings.

Suggested concepts:

- owner/project/visibility (`private`, `shared`, `role_default` where approved);
- scope definition;
- sort/group/column preferences;
- filter rows referencing category IDs or supported Tracker execution dimensions;
- stable operators limited to the MVP rule set.

Do not allow arbitrary SQL, scripting, or Project schedule expressions.

### `responsibility_scopes` and `user_delegations`

These remain Tracker-owned authorization/responsibility configuration.

A responsibility scope may reference category values, but membership in the category does not itself grant permission.

The permission engine must always evaluate role + project membership + explicit assignment/responsibility/delegation policy.

## Membership resolution

### Direct task field

Inputs:

- imported task;
- matching imported extended/custom attribute or modeled task field;
- active category source mapping.

Output:

- zero or one membership value for a single-valued source unless the source format itself explicitly supports multiple values and the product later approves parsing semantics.

Provenance should identify:

```text
snapshot -> imported task -> source field/signature -> imported value
```

No automatic token splitting or code interpretation.

### Hierarchy / summary ancestry

Inputs:

- imported task parent relationships, WBS/outline data;
- structural anchor/rule stored in the mapping definition.

The resolver should walk imported snapshot structure only. It must not calculate schedule dependencies.

Preferred MVP derivation is explicit selected structural anchors/ancestors rather than a universal fixed OutlineLevel rule.

Provenance should identify the structural anchor and ancestry that produced the value.

If the configured anchor disappears or moves ambiguously on re-import, mapping validation must require review rather than silently reselecting another branch.

### Resource Group via assignment

Inputs:

```text
imported task
-> imported assignments
-> imported resources
-> Resource.Group source fact
```

Output:

- zero, one, or many distinct group values for the task.

Duplicate assignments resolving to the same group produce one category membership value.

Null resource groups do not create empty-string membership rows.

Provenance should preserve enough identifiers to explain the task-assignment-resource path that produced each value.

## Import/activation lifecycle

Recommended lifecycle:

```text
source file stored
-> worker parses immutable snapshot
-> immutable source facts persisted
-> Source Catalogue built/read
-> selected Import Profile version validated against snapshot
-> validation findings persisted
-> planner reviews confirmation-required/broken/profile-mismatch findings
-> profile version activated for snapshot when policy gate passes
-> task category memberships resolved and persisted
-> Scope/Saved Views/operational surfaces become available
```

A Project snapshot can exist before an Operational Mapping profile is activated. Import acceptance and mapping activation should be related but not silently conflated.

If required mapping health fails project policy, the snapshot may remain imported/reviewable while operational activation is blocked.

## Re-import lifecycle

For a new snapshot:

1. preserve previous snapshot and memberships unchanged;
2. build the new Source Catalogue;
3. run the currently selected Import Profile version against the new snapshot;
4. compare expected source signatures/configuration;
5. identify new/disappeared values and structural/resource-group changes;
6. persist validation findings;
7. allow safe unchanged mappings to resolve automatically;
8. require planner confirmation for uncertain source moves or ambiguous hierarchy changes;
9. activate the profile version for the new snapshot only when the mapping-health policy allows;
10. resolve new snapshot memberships;
11. leave historical operational records linked to their historical category context.

Do not mutate prior snapshot membership rows during this process.

## Historical category context on operational records

The future implementation should support both historical and current classification.

Recommended pattern:

- operational record keeps its normal link to the relevant task/WP;
- at creation/material linkage, persist a compact snapshot of relevant category memberships or references to immutable membership rows;
- UI/reporting can display historical context from that snapshot;
- current context can be resolved through the task's active/current snapshot lineage where explicitly requested.

Avoid copying all category data into every record if a stable immutable membership reference provides the same audit result. The implementation PR should choose the smallest normalized representation that still answers both historical/current ownership questions.

## Scope query model

Global Scope is a query concern over resolved memberships plus Tracker execution dimensions.

MVP category predicate shape:

```text
category_id + operator + one_or_more_source_values
```

Supported category operators should initially be limited to:

- `IN` / selected values;
- `NOT_IN` where justified;
- `IS_UNMAPPED` / `IS_MAPPED` where required for readiness/triage.

Across different dimensions use AND. Within one category's selected values use OR/multi-select.

Scope must always be constrained by authorization first. A user's chosen Scope may narrow what they can see; it must never widen permission scope.

## Saved View query model

Saved Views reuse the same Scope/filter representation plus presentation preferences.

A Saved View must store identifiers, not generated SQL.

Changing a category display alias should not invalidate a Saved View because the view targets the category/source value identity rather than rendered label text.

If a configured source value disappears in a new snapshot, the view remains valid but may yield no current matches and should surface an inactive/orphaned-value indication where appropriate.

## API surface proposal

Exact routes may follow existing API conventions, but the capability boundary should resemble:

### Source Catalogue

- get catalogue for a snapshot;
- get source detail/distinct-value preview;
- get mapping-health summary.

### Import Profiles

- list/get profiles and versions;
- create draft profile/version;
- validate profile version against snapshot;
- activate confirmed profile version for snapshot;
- inspect validation findings;
- confirm/reject proposed remap where required.

### Operational Categories

- list/get categories;
- create/update/retire category configuration;
- configure source mapping in draft profile version;
- configure value alias/roll-up;
- preview resolved membership before activation.

### Scope/Saved Views

- query category values available within authorized project scope;
- create/update/delete private views;
- create/update shared views subject to permission;
- apply one saved view to supported operational surfaces.

Write endpoints must enforce role/permission rules from the product permission matrix and emit audit events.

## Audit event families

At minimum plan for:

```text
operational_category.created
operational_category.updated
operational_category.retired
category_source_mapping.created
category_source_mapping.changed
category_value_config.changed
import_profile.created
import_profile_version.created
import_profile_version.activated
mapping_validation.detected
mapping_remap.confirmed
mapping_validation.overridden
saved_view.shared_changed
responsibility_scope.changed
delegation.changed
```

Event payloads should include relevant project, snapshot, profile/version, category/mapping IDs, actor, and before/after values where applicable.

Do not log immutable imported source facts as though a user changed them; source import already has its own snapshot/import audit context.

## Authorization model

The implementation must preserve this order:

```text
authentication
-> project membership
-> role/capability
-> explicit assignment/responsibility/delegation
-> requested operational Scope/filter
```

Project-derived category membership can participate in responsibility rules only after explicit Tracker configuration.

A Scope or Saved View must never be used as an authorization primitive by itself.

## Transaction and consistency requirements

Configuration writes that materially affect classification should be transactionally coherent.

Examples:

- activating an Import Profile version and recording activation audit must not partially succeed;
- confirming a source remap and changing mapping health must not leave two active interpretations for the same snapshot/category;
- membership regeneration for a snapshot/profile activation should be replace-by-new-set within one controlled transaction or versioned generation boundary, never incremental in-place mutation that can expose a mixed generation.

For large schedules, membership resolution may later run asynchronously, but product state must distinguish `mapping activation requested`, `resolving`, `ready`, and `failed` from transport/job status. Do not expose partial category results as fully active.

## Indexing and performance expectations

The implementation migration should plan indexes for the common queries:

- memberships by project snapshot + category + source value;
- memberships by imported task + category;
- mappings by profile version + category;
- value config by category + source value;
- validation findings by project/snapshot/profile version + health state;
- saved-view filters by saved view;
- responsibility scope by project/category/value/user as required.

Distinct-value counts for large snapshots may be materialized/cached later, but the immutable imported rows remain source truth.

## Migration strategy

Do not modify V001-V006 or any already-applied migration.

The coding implementation should add new monotonically increasing Flyway migrations after the current migration head at that time.

Recommended migration increments, subject to the then-current migration number:

1. mapping configuration tables (`import_profiles`, versions, categories, mappings, value config, activation/validation);
2. resolved task-category membership and provenance constraints/indexes;
3. Saved Views / responsibility scope only when their backend slice is implemented.

Do not create all future tables in one speculative migration if the corresponding implementation and tests are not being delivered in that PR.

Each migration must support:

- clean install;
- upgrade from the immediately prior populated schema;
- referential integrity;
- uniqueness/cardinality rules;
- rollback/recovery strategy consistent with repository migration policy;
- no rewriting of immutable imported Project history.

## Implementation increments

### Increment 1 — Source Catalogue

Goal: prove the imported snapshot contains enough normalized evidence to support mapping.

Deliver:

- worker/API contract additions only where needed to expose field metadata and Resource `Group` facts;
- persisted/source-query support for those facts;
- read-only Source Catalogue API;
- coverage/distinct-value preview;
- tests using synthetic Project fixtures.

No editable Operational Categories yet.

### Increment 2 — Direct task-field mapping

Deliver:

- Import Profile/version foundation;
- Operational Category foundation;
- direct task-field source mapping;
- preview/validation;
- persisted task-category membership;
- provenance;
- mapping audit.

### Increment 3 — Hierarchy mapping

Deliver explicit structural-anchor mapping and re-import health behaviour.

### Increment 4 — Resource Group mapping

Deliver assignment-to-resource-group multi-value classification, deduplication, provenance, and coverage checks.

### Increment 5 — Scope and Saved Views

Reuse resolved memberships for operational filtering/grouping without changing authorization.

### Increment 6 — responsibility context and operational-record inheritance

Add explicit Tracker responsibility scopes/delegation and historical category context to Problems/Actions/Evidence/Handover as corresponding production domains are implemented.

The exact PR sequence may split these further; each coding PR should remain vertically testable and independently reviewable.

## Test strategy

Every implementation slice should include unit, persistence, API, and migration coverage appropriate to the change.

Required behavioural tests include:

- direct field preserves exact imported source value;
- alias change does not rewrite source value;
- category roll-up does not replace child membership;
- hierarchy mapping produces expected descendants for a synthetic tree;
- missing hierarchy anchor causes validation failure/confirmation rather than silent reassignment;
- Resource Group mapping returns all distinct groups for a multi-resource task;
- repeated assignments to the same group do not duplicate membership;
- category membership does not grant update/review/export permission;
- new source values produce `HEALTHY_NEW_VALUES` without breaking the mapping;
- likely field move produces `CONFIRMATION_REQUIRED` and remains inactive until confirmed;
- disappeared values retain historical value configuration;
- previous snapshot memberships remain unchanged after re-import;
- Saved View filtering cannot widen authorization scope;
- membership generation cannot expose mixed old/new active sets;
- audit events are produced for configuration/remap/activation changes.

Use only synthetic/sanitized fixtures committed under repository fixture policy.

## First coding PR exit criteria

The first implementation PR after this design should be limited to **Source Catalogue** and should demonstrate, with synthetic fixtures, that Shutdown Tracker can inspect and expose:

- available populated task custom fields and aliases/types where present;
- WBS/outline/summary structure;
- resources and standard Resource `Group` values;
- assignment relationships;
- coverage/distinct-value summaries;
- source provenance back to the immutable snapshot.

It should not yet add editable category mappings, Saved Views, responsibility rules, or frontend workflow expansion.

## Explicit non-goals

This architecture does not authorize:

- CPM, critical path, or float calculation;
- schedule-quality/recovery analysis;
- Project formula execution;
- resource levelling;
- predecessor/constraint/calendar/baseline modification;
- automatic date movement;
- Project `Critical`/slack-driven Critical Watch membership;
- hidden Project write-back;
- native `.mpp` writing;
- category-derived automatic application permissions;
- arbitrary scripting/rules engines.
