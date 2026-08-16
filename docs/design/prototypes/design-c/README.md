# Design C review prototypes

This directory is reserved for **Design C UX/UI review artifacts** for Shutdown Tracker.

These files are design references only. They are **not production application code**, are not an approved implementation specification, and should not be copied directly into the production frontend without review.

## Status

Design C is adopted as **visual and density guidance only**.

Its zone names — Control, Work, Resolve, Project bridge — are **not** the product information architecture and are not built. The baseline zones come from the [concept pack](../../../concept/README.md) and are repeated in [ADR-009](../../../adr/ADR-009-ux-ui-architecture.md), the navigation freeze in [UX Anti-Slop Rules](../../../product/ux-anti-slop-rules.md), and [Frontend Visual Review Scope](../../../product/frontend-visual-review-scope.md):

```text
Master Console:  Today | Tasks | Problems | Evidence | Exports
Field App:       My Work | Today | Problems | Evidence | Sync
```

Both applications now implement those zones. Where Design C describes a surface that does not map to one, it belongs inside a zone as a section rather than as new top-level navigation.

What Design C usefully contributes, and what has been acted on, is its visual direction: flat operational surfaces, restraint in radius and shadow, limited card containment, hierarchy through typography and rules, and semantic colour reserved for operational state. Both stylesheets now declare their palette and radii as tokens in `:root`, so that direction can be applied by changing a palette rather than editing literals throughout.

The prototype files named below (`shutdown-tracker-console-v4.html`, `shutdown-tracker-mobile-v4.html`) are **not present in this repository** and do not appear in any branch or in its history. Treat the descriptions here as the only record of Design C until they are recovered.

## Application surfaces

Design C deliberately treats the product as two separate application surfaces that share the same backend/domain and selected design-system primitives:

- `shutdown-tracker-console-v4.html` — Master Console for control-room, supervisor, planner, review, operational mapping, export, and Microsoft Project verification workflows.
- `shutdown-tracker-mobile-v4.html` — Mobile Field App / PWA for field execution, problem reporting, evidence capture, and explicit offline/sync workflows.

The Mobile Field App is **not** a responsive collapse of the Master Console. The two applications have different jobs, navigation, information density, and interaction models.

## Current Design C direction

The current concept is based primarily on the stronger operational information architecture explored in Design B, while retaining selected readability and usability strengths from Design A.

Key concepts under review include:

- Console: Control / Work / Resolve / Evidence / Project bridge.
- Control: Shift Board, Urgency Ledger, Workstream Position, Decision Docket, Critical Watch.
- Work: dense Execution Register, operational scope, saved views, task detail and Project-derived classification provenance.
- Resolve: structured Problems and Actions rather than generic ticket/chat workflows.
- Evidence: evidence review, evidence gaps, queued uploads, failed uploads, and explicit server-confirmation state.
- Project bridge: active Project snapshot, import/mapping context, planner review, export preview, artifact state, and Microsoft Project verification.
- Mobile: Run / Report / Capture / Sync, Current Work → Up Next, action-first task execution, evidence capture, and explicit local queue state.

## Product boundaries that prototypes must preserve

Microsoft Project remains the schedule authority.

Shutdown Tracker must not imply that execution updates, supervisor review, planner review, export approval, MSPDI/XML artifact generation, or Microsoft Project verification automatically update or save the master `.mpp`.

The prototypes must also preserve the distinction between:

- Project-derived classification and Shutdown Tracker authority/permissions;
- Critical Watch and Microsoft Project critical-path status;
- locally queued mobile data and server-confirmed data;
- execution state and Project schedule state.

## Visual direction

The current Design C visual direction intentionally avoids generic AI/SaaS dashboard styling:

- flatter operational surfaces;
- minimal border radius;
- minimal decorative shadows;
- limited card containment;
- small rectangular state stamps rather than pill-heavy UI;
- hierarchy primarily through typography, alignment, spacing, and rules;
- restrained charcoal/steel shell and neutral working surface;
- semantic colour reserved for operational state;
- dense desktop presentation suitable for large shutdown task sets;
- simpler, action-first mobile presentation.

## Review status

Prototype content is exploratory. Review it against current product documentation, architecture decisions, authority boundaries, role/permission rules, operational-mapping direction, offline/sync rules, and implemented repository capability before adopting any part of it.

Do not treat prototype interactions as proof that corresponding backend functionality already exists.
