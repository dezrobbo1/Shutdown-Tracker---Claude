# Microsoft Project Progress Field Contract

Status: evidence-derived; 100%-complete case proven by round trip; partial-progress and
milestone cases evidenced from native Project saves.

## Source of evidence

The committed fixtures under `fixtures/project-files/boiler/`:

- `boiler-before-no-progress.xml` — the BOILER WG110 trial extract with no progress recorded.
- `boiler-after-native-progress.xml` — the same schedule after progress was entered natively in
  Microsoft Project and saved. Task UIDs 43, 318, and 319 — the same three tasks used in the failed
  export trial recorded in the legacy source consolidation — carry native 100% completion.
- `boiler-roundtrip-candidate-task43.xml` / `boiler-roundtrip-project-saved-task43.xml` — the
  round-trip proof pair (see the verification record below).
- `boiler-progress-native-partial.xml` — a Project save carrying native partial progress on three
  leaf tasks (UIDs 335 at 50%, 39 at 25%, 340 at 20%), the evidence for the partial-progress
  transaction below.
- `boiler-mark-on-track.xml` — a Project save after the Mark on Track function was applied to
  tasks 38–41 with the status date at 2026-09-05, the evidence for the milestone transaction and
  independent confirmation of the 100%-complete transaction.

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

## The transaction Project performs for a partially complete assigned task

Evidenced by the three native partial samples in `boiler-progress-native-partial.xml` (UIDs 335
at 50%, 39 at 25%, 340 at 20%). Partial progress is a split, not a scale: actual work fills the
task's working time from the front, `Stop`/`Resume` marks the boundary, and the remainder stays
planned. All three samples show one consistent shape for a task at N%:

### Task element

| Field | Value at N% |
| --- | --- |
| `PercentComplete` | `N` |
| `PercentWorkComplete` | `N` |
| `ActualStart` | actual start datetime |
| `ActualFinish` | absent |
| `ActualDuration` | exactly N% of planned `Duration` |
| `ActualWork` | exactly N% of planned `Work` |
| `RemainingDuration` | the complement (plan minus actual) |
| `RemainingWork` | the complement (plan minus actual) |
| `Stop` | the working-time point where actual work ends |
| `Resume` | equals `Stop` |

The `Stop`/`Resume` point is computed in working time from `ActualStart` through the task
calendar — e.g. UID 340 at 20% of a 4h task starting 11:30 stops at 12:18 (48 working minutes
in).

### Each Assignment element of the task

Identical shape: `PercentWorkComplete` N, `ActualStart` set, no `ActualFinish`, `ActualWork` at
N% of the assignment's planned `Work`, `RemainingWork` the complement, `Stop`/`Resume` at the
same split point.

### Assignment TimephasedData

The original planned block splits at the `Stop` point: one `Type=2` actual-work block from
`ActualStart` to `Stop` holding the actual work, then `Type=1` remaining-work block(s) from
`Stop` to the assignment finish holding the remainder. Project splits the remaining `Type=1`
span across calendar days (UID 39 shows the 30h remainder split across multiple day blocks).

## The transaction Project performs for a completed milestone

Evidenced by `boiler-mark-on-track.xml` (UID 40, a zero-duration milestone completed by Mark on
Track; UID 41, a later milestone correctly left untouched at 0%).

- Task: `PercentComplete` and `PercentWorkComplete` `100`; `ActualStart` = `ActualFinish` = the
  milestone datetime; `ActualDuration`, `ActualWork`, `RemainingDuration`, `RemainingWork` all
  `PT0H0M0S`; `Stop` = `Resume` = the milestone datetime.
- BOILER milestones carry a placeholder assignment with `ResourceUID` `-65535` (Project's
  unassigned sentinel) and no timephased data. Mark on Track set that placeholder to
  `PercentWorkComplete` `100` with the actual dates and zero work — a completed-assignment
  transaction with zero work and no timephased conversion. An exporter completing a milestone
  must treat the placeholder the same way.

Mark on Track on regular tasks 38 and 39 produced field-for-field the same 100%-complete
transaction defined above (task 39's 40h of work converted to per-day `Type=2` blocks of
16h+16h+8h), independently confirming the contract via Project's own statusing function.

## Project-save header rewrite (re-import warning)

Both native Project saves rewrote project-level header fields with the application's own
defaults, while all calendars, tasks, resources, and assignments survived intact:

- `MinutesPerDay` reset from the file's 600 to Project's default 480; `DefaultStartTime` from
  07:00 to 08:00; `WeekStartDay` from Monday to Sunday; `NewTasksAreManual` from 0 to 1.
- `StartDate`, `CalendarUID`, `CreationDate`, `CurrentDate`, and `Name` were dropped from the
  header; `Title` was replaced with the file name.
- `StatusDate` reflects whatever the user set in Project — the one header change that is real
  statusing signal.

Consequence: when re-importing a Project-saved file as a new accepted source, never derive
durations or working-time math from header time options. Stored durations are absolute
(`PT8H0M0S` style) and the task calendars survived byte-identically; those are the truth. Header
deltas of this shape are legitimate save artifacts, not data loss, and the delta classifier must
accept them.

## Open evidence

None for the progress transactions themselves. Remaining unevidenced territory: progress on
tasks with multiple concurrent assignments where per-resource actuals differ (all evidenced
samples either complete every assignment identically or carry a single real assignment), and
resumed split tasks (`Stop` earlier than `Resume`).

## Round-trip verification (2026-08-28)

The transaction above is no longer only derived — it is proven. A candidate generated by the
merged exporter (task UID 43 completed, `fixtures/project-files/boiler/
boiler-roundtrip-candidate-task43.xml`) was opened in Microsoft Project (build 16.0.20228.20188),
recalculated, and saved. The Project-saved result
(`boiler-roundtrip-project-saved-task43.xml`) shows:

- Every exported task field survived unchanged: `PercentComplete` 100, `PercentWorkComplete` 100,
  the actual dates, `ActualDuration`/`ActualWork` at plan, remaining values zero, `Stop`/`Resume`
  at the actual finish.
- Every exported assignment field survived unchanged, and the timephased block survived as
  `Type` 2 actual work over the exact exported window and value.
- Project moved the task's scheduled `Start`/`Finish` onto the actual dates — acceptance, not
  rejection.
- The only other progress in the file is summary rollup on the task's ancestors ("Pre-Work" 2%,
  "Scaffold" 6%), exactly the legitimate recalculation set predicts.

This closes the verdict of the original round-trip trial, which disproved the three-field export
on this same schedule and task. The partial-progress gap that stood at the time of this
verification has since been closed by `boiler-progress-native-partial.xml` (see the
partial-progress transaction above).
