# Manual Microsoft Project Round-Trip Evidence

Manual Microsoft Project round-trip evidence records a human review of a generated MSPDI/XML export artifact. It exists to prove controlled export artifacts can be opened and inspected in Microsoft Project without adding automation, screenshots, generated files, real schedules, or Project write-back to the repository.

## Current Status

No manual Microsoft Project round-trip has been executed or claimed by this document. This file defines the evidence format and acceptance rules for a future manual review using synthetic or fully sanitized data only.

The first preferred candidate is the synthetic export artifact described by:

- `fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json`

The generated MSPDI/XML artifact for that candidate must be created locally as temporary output and must not be committed.

## Controlled Handoff Lifecycle

1. Candidate created — master `.mpp` not updated.
2. Candidate approved — master `.mpp` not updated.
3. Export preview created — master `.mpp` not updated.
4. Export batch approved — master `.mpp` not updated.
5. MSPDI/XML artifact generated — master `.mpp` not updated.
6. Artifact opened in Microsoft Project — master `.mpp` not updated.
7. Artifact verified in Microsoft Project — master `.mpp` not updated.
8. Planner manually updates or saves the master `.mpp` — outside Shutdown Tracker automation.

This evidence document covers artifact generation, open, and verification only. It does not claim that step eight occurred.

## Evidence Boundaries

Allowed evidence:

- Text-only notes.
- Synthetic or fully sanitized fixture identifiers.
- Export batch ID, project ID, artifact URI/hash, and expected-output reference when they are synthetic or non-sensitive.
- Microsoft Project application version used for the manual open check.
- Pass/fail result with concise notes.
- Confirmation that no write-back or schedule recalculation was performed by Shutdown Tracker.

Prohibited evidence:

- Real customer, site, contractor, asset, work order, cost, location, vendor, person, or commercial data.
- Real MPP/XML/MSPDI/XER files.
- Generated export artifacts.
- Screenshots or screen recordings of real schedules.
- Native MPP output from Shutdown Tracker.
- Claims of automated Microsoft Project verification.

## Manual Check Procedure

1. Generate an MSPDI/XML export artifact from synthetic or fully sanitized data only.
2. Keep the generated artifact outside Git or under an ignored local folder such as `fixtures/import-export/_local/`.
3. Open the generated MSPDI/XML artifact manually in Microsoft Project.
4. Confirm the file opens without requiring Shutdown Tracker to automate Microsoft Project.
5. Confirm only approved leaf-task progress/actual fields are represented.
6. Confirm summary-task actuals are not exported.
7. Confirm task identity is traceable through the expected Microsoft Project task UID/ID values.
8. Use at least one synthetic actual-start or actual-finish input with a non-zero explicit offset, then confirm canonical offset-bearing inputs preserve the reviewed whole-second Microsoft Project local wall-clock values without an unintended timezone shift.
9. Confirm no CPM, critical path, float, resource levelling, recovery scheduling, automatic date movement, or Project write-back was run by Shutdown Tracker.
10. Record a text-only evidence note using the template below.
11. Delete or keep generated artifacts only in ignored local storage after the manual review.

## Evidence Note Template

```text
evidence_id:
review_date:
reviewer_role:
fixture_or_source:
synthetic_or_sanitized:
contains_real_project_data: false
expected_output_reference:
export_batch_id:
authoritative_candidate_ids:
captured_approval_event_ids:
project_id:
generated_artifact_uri:
generated_artifact_hash:
generated_artifact_committed: false
microsoft_project_application:
microsoft_project_version:
opened_in_microsoft_project: yes/no
open_result: pass/fail
fields_checked:
  - percent_complete
  - actual_start
  - actual_finish
leaf_task_only_check: pass/fail
summary_task_exclusion_check: pass/fail
task_identity_check: pass/fail
wall_clock_value_check: pass/fail
write_back_performed: no
schedule_calculation_performed_by_shutdown_tracker: no
issues_found:
decision: accepted/rejected/needs_follow_up
notes:
```

## First Planned Evidence Record

The first text-only evidence record should use:

- `evidence_id`: `synthetic-export-leaf-actuals-round-trip-001`
- `fixture_or_source`: `synthetic-basic-wbs`
- `expected_output_reference`: `fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json`
- `contains_real_project_data`: `false`
- `generated_artifact_committed`: `false`

Do not mark the record as passed until a human has opened the generated MSPDI/XML artifact in Microsoft Project and completed the checks above.

## Relationship to API State

The API already has metadata endpoints for:

- `opened_in_microsoft_project`
- `verified`

Those statuses record lifecycle metadata only. They do not automate Microsoft Project, parse the opened artifact, mutate imported task rows, calculate schedules, or write back to Microsoft Project.
