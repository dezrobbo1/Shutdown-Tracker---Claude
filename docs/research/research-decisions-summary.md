# Research Decisions Summary

This file is a decision-oriented index. Accepted ADRs and current product documents take precedence over research summaries when wording conflicts.

## Executive decision

Shutdown Tracker is a live execution-control system. Microsoft Project remains the schedule calculation and master-file authority.

The clarified handoff model is:

```text
execution truth captured in Shutdown Tracker
-> supervisor validation
-> planner approves exact Project inputs
-> disposable candidate schedule
-> Microsoft Project recalculates
-> planner reviews source-versus-candidate impact
-> planner accepts/rejects candidate
-> optional manual adoption as next master
```

The research baseline explicitly supports approved actual/progress inputs while warning that Microsoft Project recalculates interdependent values. Therefore “do not build a scheduler” means Shutdown Tracker must not calculate those consequences itself; it does not mean Project must be prevented from recalculating a candidate.

## Core product decisions

| Area | Decision |
| --- | --- |
| Product identity | Live shutdown execution-control platform |
| Microsoft Project role | Schedule calculation and master-file authority |
| Shutdown Tracker role | Execution inputs, review, evidence, handover, operational mapping, candidate preparation, audit |
| Planner role | Approve inputs; review and adopt/reject candidate schedules |
| Scheduling logic | Do not calculate CPM, float, critical path, levelling, recovery or dependency consequences in Tracker |
| Candidate recalculation | Allowed in Microsoft Project on a disposable copy |
| Master update | Never silent; separate planner adoption decision |
| Interchange | MSPDI/XML primary open format; Project-native companion remains a possible reviewed future mechanism |
| Native `.mpp` writer | Do not build server-side |
| Import model | Immutable Project snapshots |
| Audit | Append-only high-value events and immutable candidate/artifact provenance |
| Offline | IndexedDB queue, visible sync state, idempotency; Background Sync only as enhancement |
| Communications | Entity-linked Discussion later; structured records first |
| UX | Operational and narrow; no dashboard/Gantt scheduling editor |

## Progress-field decisions

Do not confuse Microsoft Project field semantics:

- `% Complete` — duration progress;
- `Physical % Complete` — measured physical-scope progress;
- `% Work Complete` — assignment/resource Work progress.

Start/Pause/Resume/Block/Complete are Tracker execution events. They do not automatically map to a percentage field.

The initial candidate vocabulary may recognise common actual/progress facts, but each field also needs separate product-input policy, handoff-mechanism compatibility, and project/profile enablement.

A failed patch-shaped MSPDI diagnostic is evidence against that handoff mechanism, not permanent evidence that the business fact can never be used.

## Candidate-schedule impact

Microsoft Project may recalculate:

- planned dates;
- task/summary duration;
- summary roll-ups;
- actual/remaining duration;
- assignment work and progress;
- timephased data;
- slack and criticality.

Those changes are expected candidate consequences when Project produces them. Shutdown Tracker must not silently pre-compute or inject them as unapproved inputs.

## Operational Mapping

Planner-configurable source modes:

- task fields/custom fields;
- hierarchy/WBS/summary ancestry;
- assigned resource -> Resource.Group.

Real schedule research shows different hierarchy depths and potentially multi-valued Resource Groups, so mappings must be evidence-driven and revalidated per snapshot.

## Critical Watch

Critical Watch is an operational reporting construct. It may use imported hierarchy/categories but must not be equated with Project Critical/slack or a Tracker-calculated critical path.

## UX

Master Console top-level zones:

- Today
- Tasks
- Problems
- Evidence
- Exports

Field App top-level zones:

- My Work
- Today
- Problems
- Evidence
- Sync

A read-only source-versus-candidate schedule impact view is allowed for planner review. Editable schedule planning remains in Microsoft Project.

## Communications

Build structured execution records first. Entity-linked Discussion may support tasks, Problems, Actions, Evidence, Handover, and Project review. Generic chat must not become the operational source of truth.

## Next architecture questions

The most important unresolved implementation question is **how to apply the exact approved input manifest through Microsoft Project reliably**.

Candidate approaches:

1. complete-source MSPDI candidate generation;
2. planner-controlled Microsoft Project companion operating on a disposable copy;
3. manual planner input package as a fallback.

The authority, audit, and candidate-review model should remain the same regardless of mechanism.
