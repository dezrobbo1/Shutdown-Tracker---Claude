# ADR-001: Microsoft Project Integration

Status: Accepted

## Context

Shutdown Tracker imports Microsoft Project schedule snapshots, captures live execution facts, and must return reviewed execution inputs to a planner without becoming a second scheduling engine or silently changing the accepted master schedule.

Microsoft Project recalculates interdependent tracking and schedule fields when progress/actual inputs are applied. A patch-shaped XML document that contains only one or two task fields is therefore not equivalent to a complete candidate schedule.

## Decision

- Use MPXJ for Project-file parsing and MSPDI/XML processing where appropriate.
- Use MSPDI/XML as the primary open interchange format.
- Do not implement a server-side native `.mpp` writer.
- Treat the accepted Project source file/snapshot as immutable.
- Treat approved execution facts as an **input manifest**, not as the complete calculated schedule state.
- Produce a **separate disposable candidate schedule** for planner review. Microsoft Project may recalculate that candidate from the approved inputs.
- Never silently overwrite or save the accepted master schedule.
- Record source hash, approved-input identity/hash, candidate hash, Project version, semantic delta, and planner decision.

A planner-controlled Microsoft Project companion or other Project-native application mechanism is not prohibited by this ADR. It requires a separately reviewed implementation design that proves it operates on a disposable copy, applies only approved inputs, cannot overwrite the source/master path, and produces auditable candidate/delta evidence.

## Consequences

- Manual Microsoft Project round-trip testing remains required for handoff milestones.
- The final candidate may contain Project-calculated changes to dates, durations, roll-ups, work, slack, criticality, and related fields. Those changes are not automatically errors; they must be classified as Project-calculated consequences and reviewed.
- Candidate review must distinguish approved Shutdown Tracker inputs from Project-calculated consequences and unexplained differences.
- The existing minimal/patch-shaped MSPDI writer may remain useful for input-manifest tests and diagnostics, but it must not be assumed to be a complete candidate-schedule generator.
- Native `.mpp` generation by Shutdown Tracker remains out of scope.
