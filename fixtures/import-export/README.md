# Import/Export

This folder holds text-only import/export fixture metadata and expected outputs.

## Expected Structure

- `fixture-manifest.schema.json`: JSON schema for fixture manifests.
- `example-fixture-manifest.json`: safe synthetic manifest example.
- `expected-import-summary.example.json`: safe synthetic expected import summary example.
- `synthetic-basic-wbs/`: first approved synthetic MSPDI import fixture, manifest, expected import summary, and expected export artifact summary.
- Future expected parser/export outputs should live with their fixture folder and use clear names such as `expected-import-summary.json` or `expected-export-artifact-summary.json`.

## Parser Summary Expectations

Expected import summaries should record deterministic counts and notes, including:

- Fixture ID and safe project name.
- Source format.
- Task, summary task, milestone, resource, assignment, calendar, custom field, dependency, and baseline counts when relevant.
- `worker_response` fields that mirror the shared worker parse summary response where practical.
- Warning and error counts.
- Notes that contain no real names, work orders, sites, assets, vendors, people, locations, costs, or commercial data.

## Export Preview Expectations

Expected export preview metadata should describe controlled export behavior only:

- Approved batch identity.
- Eligible leaf-task progress/actual fields.
- Excluded summary tasks and non-approved updates.
- Warning and error counts.
- No uncontrolled Project write-back, scheduler recalculation, automatic date movement, CPM, critical path, float, or resource levelling expectations.

## Candidate Schedule Expectations

A candidate is the accepted source schedule with the approved execution inputs applied to it, so an
expected-artifact fixture is meaningful only in relation to a real source file. Task UIDs, task
names and the project name must be the **source's**, not values invented for the export batch:
a fixture that names tasks the source does not contain describes an artifact that could only be
produced by building a schedule from nothing.

Expected candidate summaries should describe stable worker response and readback behavior only:

- The `source_file` the candidate is derived from.
- Safe project and task names, taken from that source.
- Leaf-task Microsoft Project UID/ID values that exist in that source.
- Allowed progress/actual fields.
- Expected updated-task, source-task and exported-field counts.
- The structure the candidate must carry through, in `expected_preserved_elements` — Microsoft
  Project needs it to recalculate.
- Structural file-size/hash checks, not committed generated XML artifacts.
- Notes confirming no schedule calculations by Shutdown Tracker and no Microsoft Project write-back.

Source fixtures must be schema-valid MSPDI. Element order inside `<Task>` is an `xsd:sequence`, and
a fixture that violates it may never open in Microsoft Project at all — which would leave any
expected-output test validating a document Project would reject.

## Round-Trip Notes

Manual Microsoft Project round-trip validation should use synthetic or fully sanitized data only. Record text-only notes for MSPDI/XML reopen checks using [Manual Microsoft Project Round-Trip Evidence](../../docs/testing/manual-microsoft-project-round-trip-evidence.md). Do not commit generated exports from real schedules, generated artifacts, or screenshots.

## Approved Synthetic Fixtures

`synthetic-basic-wbs` is a tiny hand-built MSPDI XML fixture with neutral names only. Its expected import summary includes a structured `worker_response` block used by automated worker tests. Its expected export artifact summary is text-only JSON used to verify temporary generated MSPDI/XML output. This approval does not allow real Project XML, real schedules, committed generated exports, screenshots, or broad binary/project-file commits.

The expected export artifact includes a synthetic `actual_start` with the non-zero explicit offset `+08:00`. Automated readback verifies that the reviewed Microsoft Project local wall-clock value is preserved; the human Microsoft Project reopen remains pending.
