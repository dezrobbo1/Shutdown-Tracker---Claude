# Microsoft Project Progress Field Contract

Status: evidence-derived, 100%-complete case proven; partial-progress case pending.

## Source of evidence

The committed fixture pair under `fixtures/project-files/boiler/`:

- `boiler-before-no-progress.xml` — the BOILER WG110 trial extract with no progress recorded.
- `boiler-after-native-progress.xml` — the same schedule after progress was entered natively in
  Microsoft Project and saved. Task UIDs 43, 318, and 319 — the same three tasks used in the failed
  export trial recorded in the legacy source consolidation — carry native 100% completion.

Match tasks across the pair by task UID. Resource UIDs and GUIDs were renumbered between saves and
the assigned crew resource differs on some tasks; GUIDs are not stable across this pair.

## The transaction Project performs for a 100%-complete assigned task

The disproven console export wrote three task fields (`PercentComplete`, `ActualStart`,
`ActualFinish`) and nothing else. Project writes all of the following, and an export that writes
fewer produces a self-contradictory file that Project rejects or recalculates away.

### Task element

| Field | Value for 100% complete |
| --- | --- |
| `PercentComplete` | `100` |
| `PercentWorkComplete` | `100` |
| `ActualStart` | actual start datetime |
| `ActualFinish` | actual finish datetime |
| `ActualDuration` | equals planned `Duration` |
| `ActualWork` | equals planned `Work` |
| `RemainingDuration` | `PT0H0M0S` |
| `RemainingWork` | `PT0H0M0S` |
| `Stop` | equals `ActualFinish` |
| `Resume` | equals `ActualFinish` |

### Each Assignment element of the task

| Field | Value for 100% complete |
| --- | --- |
| `PercentWorkComplete` | `100` |
| `ActualStart` | assignment actual start |
| `ActualFinish` | assignment actual finish |
| `ActualWork` | equals assignment planned `Work` |
| `RemainingWork` | `PT0H0M0S` |
| `Stop` / `Resume` | equal assignment `ActualFinish` |

### Assignment TimephasedData

Planned/remaining work blocks (`Type=1`) convert to actual work blocks (`Type=2`) over the same
window with the same values. For the fully-complete case this is a type flip of the existing
blocks, not a re-spread.

## Legitimate recalculation set

On recalculation Project also changes fields the export must never write but the source-versus-
candidate delta classifier must accept as legitimate consequences of adopted progress:

- `StartSlack`, `FinishSlack`, `TotalSlack` collapse to `0` on completed tasks.
- `LateStart` / `LateFinish` move to the actual dates.
- Summary tasks (including UID 0) roll up partial `PercentComplete` / `PercentWorkComplete`,
  `ActualDuration`, `ActualWork`, and `RemainingWork` values.
- `Critical` flags can flip as slack collapses.

## Open evidence

Partial progress (a task at, say, 40%) is not yet evidenced. It requires one more native-entry
sample to show how Project splits timephased blocks into an actual-work prefix and a
remaining-work suffix, and how `Stop` / `Resume` behave on an in-progress task. Until then the
exporter must not emit partial progress for assigned tasks.
