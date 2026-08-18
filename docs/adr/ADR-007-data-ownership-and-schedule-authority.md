# ADR-007: Data Ownership and Schedule Authority

Status: Accepted

## Context

Shutdown Tracker must connect field execution truth to Microsoft Project without becoming an independent scheduler and without making the product so restrictive that approved execution facts can never update a reviewed schedule candidate.

The previous shorthand — “Shutdown Tracker must not move dates” — was ambiguous. It did not distinguish a date invented by Shutdown Tracker from a date recalculated by Microsoft Project after a reviewed execution input is applied.

## Decision

Adopt three explicit authority layers.

### 1. Execution-input authority — Shutdown Tracker

Shutdown Tracker may capture, review, approve, and audit explicit execution facts. Examples include task execution state, progress, actual start/finish claims, blockers, evidence, and handover. Only facts allowed by the active handoff policy may be sent to a candidate calculation.

### 2. Calculation authority — Microsoft Project

Microsoft Project owns schedule calculation. When approved inputs are applied to a disposable copy of the accepted schedule, Microsoft Project may recalculate planned dates, durations, summary roll-ups, work, assignment values, timephased data, slack, criticality, and other dependent values.

Shutdown Tracker must not independently calculate or invent those consequences. It may read, store, compare, and display them as **Microsoft Project-calculated consequences**.

### 3. Adoption authority — Planner

The planner reviews the candidate schedule and source-versus-candidate delta. The planner decides whether the candidate is rejected, retained for further review, or manually adopted as the next master schedule.

Shutdown Tracker must not automatically replace the accepted master schedule.

## Direct-input boundary

Without a separate product decision, Shutdown Tracker must not directly author:

- summary-task actuals;
- planned dates or durations;
- dependencies/predecessors;
- constraints;
- calendars;
- baselines;
- WBS/outline structure;
- resource levelling or allocation changes;
- Project Critical/slack values;
- Project formula results.

These fields may legitimately change **inside a Project-calculated candidate**. That is not the same as Shutdown Tracker directly writing them.

## Consequences

- Imported schedule snapshots remain immutable source facts.
- Candidate schedules are new artifacts with their own hashes and provenance.
- Planner-facing review may include a read-only schedule-impact view or Gantt-like comparison, provided it does not edit or calculate the schedule.
- A failed or rejected candidate must leave the accepted source/master unchanged.
- Any future Project automation must be explicit, planner-controlled, copy-based, auditable, and incapable of silent master overwrite.
