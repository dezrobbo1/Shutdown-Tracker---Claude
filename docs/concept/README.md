# Concept and Architecture Pack v1.3 Summary

Shutdown Tracker is a live execution-control platform for shutdown, turnaround, outage, and major-overhaul work. Microsoft Project remains the schedule calculation and master-file authority.

## Product authority model

The platform separates three responsibilities:

- **Shutdown Tracker** captures and approves execution inputs.
- **Microsoft Project** recalculates a disposable candidate schedule.
- **The planner** accepts, rejects, supersedes, or manually adopts the candidate.

Shutdown Tracker does not calculate CPM, critical path, float, recovery schedules, resource levelling, or dependency consequences itself. It does not silently overwrite the accepted master and does not provide server-side native `.mpp` writing.

Microsoft Project may change planned dates, durations, summary roll-ups, work, slack, criticality, and related values when approved execution inputs are applied. Those are candidate-schedule consequences to review, not hidden Shutdown Tracker-authored schedule values.

## Application experiences

### Master Console

Desktop-optimised for shutdown control, planners, coordinators, supervisors, package owners, and managers.

Baseline zones:

- Today
- Tasks
- Problems
- Evidence
- Exports

### Field App

Mobile-optimised for supervisors, leading hands, contractors, inspectors, and execution crews.

Baseline zones:

- My Work
- Today
- Problems
- Evidence
- Sync

Offline-capable field workflows are core direction. Delivery technology must not create a separate authority or data model.

## Candidate schedule handoff

The target handoff is:

```text
immutable accepted Project source
-> reviewed execution facts
-> planner-approved input manifest
-> disposable Project candidate
-> Microsoft Project recalculation
-> source-versus-candidate delta
-> planner candidate decision
-> optional manual master adoption
```

A read-only schedule-impact comparison is allowed in the Master Console. Editable schedule planning remains in Microsoft Project.

## Project Operational Mapping

Microsoft Project supplies source facts, structure, and classifications. Shutdown Tracker adds planner-configured operational interpretation without rewriting those source facts.

Initial source modes:

1. direct imported task fields/custom fields;
2. task hierarchy/WBS/selected summary ancestry;
3. task assignments resolved through Resource `Group`.

Operational Categories may support filtering, grouping, Scope, Saved Views, Critical Watch, Problems, Actions, Evidence, Handover, reporting, and mobile relevance.

Resource-derived categories must support multiple values where a task has assignments from more than one Resource Group.

Formula-backed Project fields may be used as read-only classification context. Shutdown Tracker does not reproduce the Project formula engine.

Classification never grants application authority by itself.

## Critical Work Package reporting

A Critical Watchlist is an operational reporting list. A Critical Work Package is a reporting object, not a scheduling object.

Default source is a selected Project summary task plus descendants; multi-summary sources are also valid when reporting crosses WBS boundaries.

Project Critical/slack fields may be displayed as read-only Project context but do not automatically define Critical Watch membership.

## Execution model

The platform should support:

- Start, Pause, Resume, Block, Complete;
- structured progress;
- supervisor review;
- planner input review;
- Problems and Actions;
- Evidence and Handover;
- Critical Updates and reporting;
- candidate schedule review and audit.

Execution actions are not automatic aliases for Project fields.

## Progress methods

Projects may configure the business-appropriate progress method:

- `% Complete` for duration progress;
- `Physical % Complete` for measured physical scope where site practice supports it;
- `% Work Complete` only when resource/assignment Work is intentionally maintained;
- state-only tracking where a percentage is not meaningful.

Field recognition, reviewability, handoff support, and project enablement remain separate decisions.

## MVP direction

The MVP focuses on:

- immutable Project snapshot import;
- Project source discovery and Operational Mapping;
- execution state and structured progress;
- supervisor/planner review;
- Problems, Actions, Evidence, and Handover;
- Critical Watch reporting;
- approved-input/candidate-schedule handoff;
- users, roles, permissions, delegation, and offline foundations;
- browser/installable delivery for both application experiences.

Explicitly excluded:

- Shutdown Tracker CPM/float/critical-path calculation;
- editable Gantt/dependency scheduling UI;
- recovery/scheduler engine;
- resource levelling;
- AI schedule optimisation;
- hidden master write-back;
- server-side native `.mpp` writing;
- automatic permissions from Project categories;
- generic chat clone behaviour.

A read-only candidate-impact Gantt/timeline is not considered a scheduling UI and is permitted when it helps planner review.
