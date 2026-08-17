# Manual Microsoft Project Round-Trip Evidence

Manual Microsoft Project round-trip evidence records a human review of a generated MSPDI/XML candidate schedule. It exists to prove candidates can be opened, recalculated and inspected in Microsoft Project without adding automation, screenshots, generated files, real schedules, or Project write-back to the repository.

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

## Important Distinction

The test is not "did no schedule field change?"

Microsoft Project is expected to recalculate dependent schedule state once a mechanism exists that gives it something to recalculate against. The durable questions are:

1. Were the exact approved inputs applied?
2. Did Microsoft Project produce a separate candidate schedule?
3. Did the accepted source remain unchanged?
4. Can the source-versus-candidate differences be classified and reviewed?
5. Can the planner reject the candidate without affecting the master?

Question two is now answerable: the candidate is a full schedule. Question four is not yet — nothing computes the semantic source-versus-candidate delta, so classification is a manual reading of the two files for now. See "Candidate-Schedule Evidence" below.

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
3. Open the generated MSPDI/XML candidate manually in Microsoft Project.
4. Confirm the file opens without requiring Shutdown Tracker to automate Microsoft Project.
5. Confirm the candidate opens as a complete schedule: the source's tasks, summary structure, WBS/outline ancestry, calendars and dependency links are all present.
6. Confirm only the approved leaf-task progress/actual values differ from the accepted source, and that no summary-task actual was authored by Shutdown Tracker. Microsoft Project recalculating dependent values is expected and is not a failure.
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
candidate_opens_as_complete_schedule: pass/fail
source_structure_preserved_check: pass/fail
only_approved_fields_modified_check: pass/fail
summary_task_exclusion_check: pass/fail
accepted_source_file_unchanged: pass/fail
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
- offset-bearing value: synthetic task A1 `actual_start` = `2026-01-05T07:00:00+08:00`
- `contains_real_project_data`: `false`
- `generated_artifact_committed`: `false`

Do not mark the record as passed until a human has opened the generated MSPDI/XML artifact in Microsoft Project and completed the checks above.

## Relationship to API State

The API already has metadata endpoints for:

- `opened_in_microsoft_project`
- `verified`

Those statuses record lifecycle metadata only. They do not automate Microsoft Project, parse the opened artifact, mutate imported task rows, calculate schedules, or write back to Microsoft Project.

`verified` means the planner confirmed the artifact opened and behaved as expected. It does not mean a candidate schedule was recalculated, and it does not mean the master was adopted.

## Candidate-Schedule Evidence

This section defines the evidence required of a candidate schedule. It **is** now satisfiable: the generated artifact is the accepted source schedule with the approved execution inputs applied to it, so calendars, dependencies, WBS ancestry, summary structure and resource assignments reach Microsoft Project intact and it has a real schedule to recalculate.

Candidate generation requires an MSPDI/XML-sourced snapshot. Microsoft Project can only be handed MSPDI/XML back, so deriving a candidate from a native `.mpp` would mean converting formats in both directions, which can silently drop links, calendars or constraints from a file that still looks like a schedule. `.mpp` upload, import and reporting are unaffected.

### Delta classification

Every material source-versus-candidate difference should be classified as:

- `approved_input` — exact planner-approved Shutdown Tracker fact;
- `project_calculated_consequence` — dependent value created or recalculated by Microsoft Project;
- `unexpected_difference` — unexplained change requiring investigation.

Do not treat a Project-calculated planned-date, duration, summary, work, or slack change as an automatic failure merely because Shutdown Tracker was not allowed to directly author that field.

### Current mechanism status

The manual diagnostics performed during export-handoff investigation showed that minimal field-isolated MSPDI patches do not reliably reproduce the same tracking transaction as entering the fact through Microsoft Project.

Those diagnostics were **mechanism evidence, not a permanent prohibition on the execution facts themselves** — and that reading has now been acted on. The patch mechanism was replaced rather than the fields being restricted further: the candidate is built by writing the approved values into the accepted source document, so nothing about the schedule is discarded on the way to Microsoft Project.

Automated evidence in place:

- the candidate parses as a complete schedule with the source's task count, calendars and dependency links intact;
- a source-versus-candidate comparison proves only approved `(task, field)` pairs differ, and generation fails closed otherwise;
- inserted elements land in MSPDI schema sequence order, verified against MPXJ's own schema binding.

**Still pending, and not substitutable by the above:** a planner opening a generated candidate in Microsoft Project, confirming it recalculates, and confirming the accepted source file is unchanged. No handoff mechanism should be marked production-ready until a synthetic candidate passes that procedure.
