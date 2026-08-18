# Approval, Candidate Schedule, and Adoption State Model

Shutdown Tracker separates execution state, review state, Project-input authority, candidate-schedule calculation, and master adoption.

## Why the separation matters

A field user can report a task complete while:

- the update is still awaiting supervisor review;
- no planner has approved Project input;
- no candidate schedule exists;
- Microsoft Project has not recalculated anything;
- the current master remains unchanged.

Likewise, a candidate schedule may be successfully produced but rejected by the planner.

## State dimensions

### Execution state

`not_started -> ready -> in_progress -> paused/blocked -> completed`

Corrections use explicit events/supersession rather than destructive history edits.

### Progress review state

`draft -> submitted -> supervisor_accepted | correction_requested | rejected | superseded`

Supervisor acceptance means operationally credible only.

### Planner input state

`needs_planner_review -> input_approved | input_rejected | clarification_requested | superseded`

Input approval authorises one exact execution candidate for the approved-input manifest.

### Approved-input manifest state

Suggested target states:

`draft -> sealed -> approved_for_candidate_calculation -> superseded`

The sealed manifest is immutable and includes source/hash/candidate/approval provenance.

### Candidate schedule state

Suggested target states:

`not_prepared -> calculation_pending -> candidate_produced -> delta_ready -> accepted | rejected | failed | superseded`

These target states describe the product lifecycle. They do not imply that every current branch already implements them.

### Master adoption state

`not_adopted -> adopted_manually -> superseded_by_later_master`

Adoption is a separate audit fact. Candidate acceptance does not imply adoption.

### Sync state

`local_draft -> queued_on_device -> sending -> server_received | failed | conflict`

Queued is not submitted.

## Existing export-integrity batches

The export-integrity implementation in this repository enforces the state machine below at the PostgreSQL boundary. These tables describe shipped behaviour, not a target, and must not be edited to match a target workflow before the code and migrations change.

### Export candidate states

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `not_eligible` | Candidate cannot be exported due to task type, field, policy, lineage, blocker, or evidence state. | `eligible`, `superseded` |
| `eligible` | Candidate appears to satisfy export rules but is not approved yet. | `approved_for_export`, `export_blocked`, `superseded` |
| `export_blocked` | Candidate is blocked by evidence, blocker, lineage, summary-task rule, or policy. | `eligible`, `rejected`, `superseded` |
| `approved_for_export` | Planner-approved candidate may be included in export preview. | `in_export_preview`, `superseded` |
| `in_export_preview` | Candidate is included in a draft export preview. | `exported`, `superseded` |
| `exported` | Candidate was included in a generated export artifact. | `superseded` |
| `rejected` | Candidate is not approved for export. | `superseded` |
| `superseded` | A later candidate replaces this one. | none |

### Export batch states

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `draft_preview` | Preview has been assembled but not submitted for approval. | `awaiting_approval`, `approved`, `rejected`, `failed`, `superseded` |
| `awaiting_approval` | Preview is ready for Planner approval. | `approved`, `rejected`, `failed`, `superseded` |
| `approved` | Export batch has been approved for file generation. | `generated`, `failed`, `superseded` |
| `rejected` | Export batch is not approved. | none |
| `generated` | MSPDI/XML export artifact has been generated. | `opened_in_microsoft_project`, `failed`, `superseded` |
| `opened_in_microsoft_project` | Planner has opened the artifact in Microsoft Project for manual verification. | `verified`, `failed`, `superseded` |
| `verified` | Planner has confirmed the artifact opened and behaved as expected in Microsoft Project. | none |
| `failed` | Generation or manual verification failed. | none |
| `superseded` | A later export batch replaces this batch for operational purposes. | none |

These states remain useful for authority and artifact provenance. Candidate-schedule work should either extend them carefully or introduce a separate candidate-schedule run entity rather than overloading `verified` to mean "planner accepted the recalculated schedule."

`verified` currently means the planner confirmed the generated MSPDI/XML artifact opened and behaved as expected. It does not mean a candidate schedule was recalculated, and it does not mean the master was adopted.

## Authority rules

- Field users and contractors do not approve Project input or candidate adoption.
- Supervisors validate execution truth.
- Planners approve Project inputs and candidate adoption by default.
- An approved input is bound to one exact project/snapshot/task/field/value/source/version/candidate/approval identity.
- A candidate schedule is bound to one immutable source schedule and one immutable approved-input manifest.
- A planner candidate decision is bound to one candidate hash and semantic delta.
- A later master adoption is a separate event.

## Candidate review requirements

A candidate review should show:

- source schedule identity/hash;
- candidate schedule identity/hash;
- approved-input manifest/hash;
- Project version/build used for calculation;
- approved inputs;
- Project-calculated consequences;
- unexplained changes;
- project finish movement;
- planner decision and notes.

## Provenance classification

Every source-versus-candidate difference should be classified as:

- `approved_input`;
- `project_calculated_consequence`;
- `unexpected_difference`.

Unchanged values need not be stored as delta rows but remain traceable to the source hash.

## Direct-input restrictions

Without an explicit policy change, Shutdown Tracker must not directly author:

- summary-task actuals;
- planned dates/durations;
- dependencies;
- constraints;
- calendars;
- baselines;
- WBS/outline structure;
- Project critical/slack values;
- resource levelling or schedule optimisation outputs.

Those values may change inside a Microsoft Project-calculated candidate and be shown to the planner.

## Immutability and corrections

- Source schedule files/snapshots are immutable.
- Execution candidates and approval events are append-only.
- Approved-input manifests are sealed and immutable.
- Candidate schedules and deltas are immutable artifacts once produced.
- Rejected and superseded candidates remain visible in history.
- Corrections create new candidates/manifests/runs rather than editing prior evidence.

As implemented for policy-1 export batches:

- Only the explicit state transitions listed above are permitted. The one-time draft line-set seal is the only permitted same-state mutation.
- Batch identity, preview creation, sealed membership, and every established approval, generation, artifact, Microsoft Project open, and verification fact are immutable.
- Rejected, failed, superseded, and verified policy-1 batches are terminal and immutable.
- Lifecycle metadata uses server-owned sections with caller data nested under `clientMetadata`; later transitions cannot replace earlier provenance.
- Generated export artifacts remain linked to the approval record that produced them, and a failed or superseded batch remains visible in export history.

## Required user-facing wording

Before candidate calculation:

```text
Approved for candidate calculation. Current master schedule unchanged.
```

After candidate produced:

```text
Candidate schedule produced by Microsoft Project. Review calculated impacts before adoption.
```

After candidate acceptance:

```text
Candidate accepted for planner use. Master adoption is still a separate action.
```

After manual adoption is recorded:

```text
Planner recorded this candidate as adopted into the next master schedule.
```
