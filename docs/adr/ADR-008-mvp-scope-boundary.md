# ADR-008: MVP Scope Boundary

Status: Accepted

## Context

The product must remain focused on execution control without accidentally forbidding the planner-review workflow that gives Project handoff its value.

## Decision

The MVP excludes:

- a Shutdown Tracker CPM/critical-path/float engine;
- resource levelling and recovery scheduling;
- editable dependency-map or Gantt scheduling UI;
- automatic schedule optimisation;
- hidden or unattended master-file write-back;
- server-side native `.mpp` writing;
- automatic Project formula evaluation;
- AI schedule prediction/optimisation;
- a generic chat clone;
- custom dashboard builders.

The MVP may include:

- planner-reviewed execution inputs;
- disposable candidate schedules calculated by Microsoft Project;
- read-only source-versus-candidate schedule impact views;
- Project-calculated consequence reporting;
- explicit planner accept/reject/adopt workflow;
- diagnostic or manual Project-native handoff steps while the production mechanism is being proven.

A planner-controlled Microsoft Project companion is a permissible future implementation option. It requires a focused ADR/implementation review before production use; it is not a blanket product prohibition.

## Consequences

- “No scheduler” means Shutdown Tracker does not calculate the schedule itself.
- It does not mean Microsoft Project is prevented from recalculating a disposable candidate.
- Read-only candidate-impact visualization is allowed; schedule editing remains in Microsoft Project.
- Scope-expanding direct write authority still requires explicit product and ADR review.
