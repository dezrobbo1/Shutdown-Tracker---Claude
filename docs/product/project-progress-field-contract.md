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
- Summary tasks (including UID 0) roll up progress from their descendants. The complete
  observed field set on affected summary ancestors (round-trip pair, UIDs 0/1/34):
  `PercentComplete` and `PercentWorkComplete` (UID 0 can stay 0 when the rollup rounds down),
  `ActualStart`, `ActualDuration`, `ActualWork`, `RemainingDuration`, `RemainingWork`, and
  `Stop`/`Resume` (set to the rolled-up actual boundary). `ActualFinish` stays absent while any
  descendant is incomplete.
- Resource elements roll up progress from their assignments: `PercentWorkComplete`, `ActualWork`,
  and `RemainingWork` on each affected Resource move to reflect adopted actuals (observed in the
  round-trip verification: resource UID 4 moved from 0% / 0h / 374h to 4% / 16h / 358h).
- `Critical` flags can flip as slack collapses.

## The transaction Project performs for a partially complete assigned task

Evidenced by the three native partial samples in `boiler-progress-native-partial.xml` (UIDs 335
at 50%, 39 at 25%, 340 at 20%). Partial progress is a split, not a scale: actual work fills the
task's working time from the front, `Stop`/`Resume` marks the boundary, and the remainder stays
planned.

This transaction is evidenced only for the shape all three samples share: an auto-scheduled,
fixed-units, non-effort-driven task (`Manual=0`, `Type=0`, `EffortDriven=0`) with a single
assignment carrying uniformly distributed (flat-contour) work, an actual start equal to the
planned start, and an actual prefix that ends within its first working day. Fixed-work,
fixed-duration, manually scheduled, and effort-driven tasks are unevidenced and outside the
shape. For a contoured or otherwise uneven
timephased assignment, the first N% of duration need not contain N% of work — `ActualWork` must
come from the actual prefix blocks, not from scaling planned work — and none of the samples
shows that case. See Open evidence. Within the evidenced shape, all three samples show one
consistent transaction for a task at N%:

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

The `Stop`/`Resume` point observed in the samples is a working-time offset from `ActualStart`
through the task calendar — e.g. UID 340 at 20% of a 4h task starting 11:30 stops at 12:18 (48
working minutes in). Boundary note: this describes what Project calculated, not what Tracker may
calculate. Deriving `Stop`/`Resume` requires traversing the task calendar — a scheduling
computation that belongs to Microsoft Project, not Tracker. The exporter must not implement this
transaction until either a Project-native mechanism performs the working-time math or an
explicit, recorded boundary decision authorizes Tracker to reproduce it. Until then, partial
progress on assigned tasks stays refused (the existing guard).

### Each Assignment element of the task

Identical shape: `PercentWorkComplete` N, `ActualStart` set, no `ActualFinish`, `ActualWork` at
N% of the assignment's planned `Work`, `RemainingWork` the complement, `Stop`/`Resume` at the
same split point.

### Assignment TimephasedData

The original planned block splits at the `Stop` point: `Type=2` actual-work block(s) from
`ActualStart` to `Stop` holding the actual work, then `Type=1` remaining-work block(s) from
`Stop` to the assignment finish holding the remainder. Project splits the remaining `Type=1`
span across calendar days (UID 39 shows the 30h remainder split across multiple day blocks).
Every evidenced actual prefix ends within its first working day, so each sample happens to show
a single `Type=2` block; completed tasks on the same schedule show Project writing per-day
`Type=2` blocks (UID 39 completed: 16h+16h+8h), so a multi-day actual prefix almost certainly
splits per day — but that shape is unevidenced for partial progress. See Open evidence.

## The shape Project stores for a completed milestone

Final-state evidence from both native saves: milestone UID 40 is already complete in
`boiler-progress-native-partial.xml` and carries the identical values in
`boiler-mark-on-track.xml`, so it was completed by native entry in the earlier session — not by
the Mark on Track action (the tasks marked in that session were IDs 38–41, which are UIDs
335/39/340/38; UID 40 is ID 32). The fixtures therefore evidence the stored shape of a completed
milestone, but not which statusing function produced it, and UID 41 remaining at 0% cannot be
attributed to status-date protection. What the stored shape shows:

- Task: `PercentComplete` and `PercentWorkComplete` `100`; `ActualStart` = `ActualFinish` = the
  milestone datetime; `ActualDuration`, `ActualWork`, `RemainingDuration`, `RemainingWork` all
  `PT0H0M0S`; `Stop` = `Resume` = the milestone datetime.
- BOILER milestones carry a placeholder assignment with `ResourceUID` `-65535` (Project's
  unassigned sentinel) and no timephased data. In the stored completed state that placeholder
  holds `PercentWorkComplete` `100` with the actual dates and zero work — a completed-assignment
  shape with zero work and no timephased conversion. Which statusing action wrote it is not
  evidenced (see above); what is evidenced is that a completed milestone's placeholder carries
  these values, so an exporter completing a milestone must leave the placeholder in exactly this
  state.

Mark on Track (applied to IDs 38–41 = task UIDs 335, 39, 340, and 38, with the status date at
2026-09-05) completed all four tasks. For tasks completed from a no-progress state, the result is
field-for-field the 100%-complete transaction defined above, independently confirming the
completion contract via Project's own statusing function.

UID 39 is different and important: it went from 25% partial to complete, and Project did not
flip the existing block boundaries — it respread. The partial state held a 10h `Type=2` block
plus 16h and 14h `Type=1` blocks; the completed state holds per-day `Type=2` blocks of
16h+16h+8h. The exporter's current type-flip conversion (each `Type=1` block becomes `Type=2`
over the same window) would instead produce 10h+16h+14h boundaries. Completing an
already-partial task is therefore NOT covered by the proven completion transaction: it needs its
own supported normalization and its own Project round trip before the exporter may emit it. The
proven completion case remains completion from a no-progress source state.

## Project-save header rewrite (re-import warning)

Project saves can rewrite project-level header fields while all calendars, tasks, resources, and
assignments survive intact — and which fields get rewritten varies per save. The round-trip save
(`boiler-roundtrip-project-saved-task43.xml`) preserved the original time options; the two later
saves (`boiler-progress-native-partial.xml`, `boiler-mark-on-track.xml`) rewrote them with
application defaults. The complete observed set:

| Header field | Original | Round-trip save | Partial save | Mark-on-track save |
| --- | --- | --- | --- | --- |
| `StatusDate` | 2025-05-09T17:00 | unchanged | 2026-08-28T10:00 | 2026-09-05T10:00 |
| `MinutesPerDay` | 600 | unchanged | 480 | 480 |
| `DefaultStartTime` | 07:00 | unchanged | 08:00 | 08:00 |
| `WeekStartDay` | 1 (Mon) | unchanged | 0 (Sun) | 0 (Sun) |
| `NewTasksAreManual` | 0 | unchanged | 1 | 1 |
| `NewTasksEstimated` | 1 | unchanged | 0 | 0 |
| `DurationFormat` | 5 | unchanged | 7 | 7 |
| `SpreadPercentComplete` | 0 | unchanged | 1 | 1 |
| `SpreadActualCost` | 0 | unchanged | 1 | 1 |
| `Autolink` | 0 | unchanged | 1 | 1 |
| `MicrosoftProjectServerURL` | 1 | unchanged | 0 | 0 |
| `CurrencySymbolPosition` | 0 | unchanged | 1 | 1 |
| `CurrencySymbol` | `$` | unchanged | dropped | dropped |
| `AgileMode` | 2 | unchanged | 0 | 0 |
| `SprintLength` | 2 | unchanged | 0 | 0 |
| `UpdateManuallyScheduledTasksWhenEditingLinks` | 1 | unchanged | 0 | 0 |
| `SprintCreationThroughDate` | absent | absent | 1984-01-01T00:00 | 1984-01-01T00:00 |
| `GUID` | original GUID | new GUID | zeroed | zeroed |
| `StartDate`, `CalendarUID`, `CreationDate`, `CurrentDate` | present | unchanged (`CurrentDate` updated) | dropped | dropped |
| `BaselineCalendar` | present | unchanged | dropped | dropped |
| `Name` | original file name | new file name | dropped | new file name |
| `Title` | Project Plan Template | unchanged | new file name | new file name |
| `LastSaved` | 2026-08-27T15:23 | updated | updated | updated |

`StatusDate` is the one header change that is real statusing signal.

Not everything above is harmless, however. The dropped project-level `CalendarUID` is schedule
data loss with observable corruption downstream: with the project calendar link gone, every
summary task whose own `CalendarUID` is `-1` (calendar inherited) had its `Duration` and
`ManualDuration` recalculated to the elapsed wall-clock span. In the two later saves this hit 83
of 95 summary tasks — e.g. UID 1 keeps the same `Start`/`Finish` but its `Duration` changes from
`PT160H0M0S` to `PT627H30M0S`. Leaf-task durations were untouched (0 changed), and the calendar
definitions themselves survived byte-identically — but the inheritance link did not, and the
summary durations in those files are corrupt, not truth.

Consequences:

- The delta classifier must treat pure save metadata (the table's option and metadata rows) as
  normalizable, but must treat a dropped project `CalendarUID` — and the summary-duration
  recalculations that follow from it — as reportable schedule corruption to surface for planner
  review, never as an acceptable header delta.
- When re-importing a Project-saved file as a new accepted source, never derive durations or
  working-time math from header time options, and do not trust summary-task durations from a
  save whose project `CalendarUID` was dropped. Leaf-task absolute durations (`PT8H0M0S` style)
  and the calendar definitions survived every save; those are the reliable layer.
- `boiler-progress-native-partial.xml` and `boiler-mark-on-track.xml` remain valid evidence for
  the task/assignment/timephased progress transactions (leaf-level state, unaffected by the
  calendar loss), but their summary durations and header option values must not be used as
  expected values.

## Open evidence

The 100%-complete and milestone shapes are fully evidenced. The partial-progress transaction is
evidenced only for its narrow shape; the exporter must not emit partial progress outside it
until a native sample closes each gap:

- Uneven work contours: all partial samples have uniformly distributed work, so
  `ActualWork` = N% of plan is only proven for flat contours. A contoured assignment needs its
  own native sample before the scaling rule is trusted.
- Shifted actual starts: all partial samples have `ActualStart` equal to planned `Start`. How
  Project moves remaining timephased blocks and computes `Stop` when the actual start differs
  from plan is unevidenced — despite being a supported console input.
- Multi-day actual prefixes: every evidenced partial prefix ends within its first working day.
  Whether a longer prefix produces one `Type=2` block or per-day blocks is unevidenced for
  partial progress.
- Progress on tasks with multiple concurrent assignments where per-resource actuals differ (all
  evidenced samples either complete every assignment identically or carry a single real
  assignment).
- Resumed split tasks (`Stop` earlier than `Resume`).
- Which statusing function produces the milestone shape (only the stored final state is
  evidenced), and status-date protection of future tasks.
- Completing an already-partial task: Project respreads timephased actuals rather than flipping
  existing block boundaries (UID 39, 25% to complete: 10h+16h+14h became 16h+16h+8h), so this
  conversion needs its own normalization rule and round-trip proof.
- Task modes other than auto-scheduled fixed-units non-effort-driven (`Manual=0`, `Type=0`,
  `EffortDriven=0`) for partial progress.

Independent of missing samples, the partial-progress transaction also carries an unresolved
boundary question: deriving `Stop`/`Resume` requires working-time calendar math that belongs to
Microsoft Project, not Tracker (see the boundary note in the partial-progress section). Partial
progress stays refused until that is resolved, regardless of evidence coverage.

## Round-trip verification (2026-08-28)

The transaction above is proven by round trip for the fixture's shape: a task with a single
assignment carrying a single timephased block. A candidate generated by the merged exporter
(task UID 43 completed, `fixtures/project-files/boiler/boiler-roundtrip-candidate-task43.xml`)
was opened in Microsoft Project (build 16.0.20228.20188), recalculated, and saved. The
Project-saved result (`boiler-roundtrip-project-saved-task43.xml`) shows:

- Every exported task field survived recalculation and save unchanged: `PercentComplete` 100,
  `PercentWorkComplete` 100, the actual dates, `ActualDuration`/`ActualWork` at plan, remaining
  values zero, `Stop`/`Resume` at the actual finish.
- The exported assignment's fields survived unchanged, and its timephased block survived as
  `Type` 2 actual work over the exact exported window and value.
- The exported actual dates coincided with the task's scheduled `Start`/`Finish`, so this pair
  provides no evidence about scheduled-date movement; acceptance is evidenced by the exported
  progress surviving recalculation and save, and by Project rolling it up (below).
- The only other progress in the file is rollup the legitimate recalculation set predicts:
  summary ancestors ("Pre-Work" 2%, "Scaffold" 6%) and resource UID 4 (0% to 4% work complete).

Tasks with multiple assignments, or assignments with multiple timephased blocks, are not covered
by this proof and need their own round-trip sample before the exporter's behavior on them is
treated as verified.

### Save metadata and numeric-representation deltas

Beyond progress rollup, this save also changed pure metadata and numeric representation. The
delta classifier must normalize or accept these without treating them as schedule differences:

- Save metadata: top-level `Name` (replaced with the saved file name), `GUID` (Project issued a
  new project GUID), `LastSaved`, and `CurrentDate` (updated to the save session). These carry
  no schedule meaning and should be normalized away.
- Signed-zero representation: earned-value fields can flip representation without a value change
  (assignment UID 45: `BCWP` and `CV` `0.00` to `-0.00`). Numeric comparison must be by value,
  not by string.

This closes the verdict of the original round-trip trial, which disproved the three-field export
on this same schedule and task. The partial-progress gap that stood at the time of this
verification has since been closed by `boiler-progress-native-partial.xml` (see the
partial-progress transaction above).
