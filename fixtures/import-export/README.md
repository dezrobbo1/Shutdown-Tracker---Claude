# Import/Export

This folder holds text-only import/export fixture metadata and expected outputs.

## Expected Structure

- `fixture-manifest.schema.json`: JSON schema for fixture manifests.
- `example-fixture-manifest.json`: safe synthetic manifest example.
- `expected-import-summary.example.json`: safe synthetic expected import summary example.
- `synthetic-basic-wbs/`: first approved synthetic MSPDI import fixture, manifest, and expected summary.
- Future expected parser outputs should live with their fixture folder and use clear names such as `expected-import-summary.json`.

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

## Round-Trip Notes

Manual Microsoft Project round-trip validation should use synthetic or fully sanitized data only. Record text-only notes for MSPDI/XML reopen checks. Do not commit generated exports from real schedules or screenshots of real schedules.

## Approved Synthetic Fixtures

`synthetic-basic-wbs` is a tiny hand-built MSPDI XML fixture with neutral names only. Its expected import summary now includes a structured `worker_response` block used by automated worker tests. This approval does not allow real Project XML, real schedules, generated exports, screenshots, or broad binary/project-file commits.
