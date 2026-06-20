# Import/Export Fixture Strategy

## Purpose

Import/export fixtures exist to make Microsoft Project import and controlled export behavior testable without committing real shutdown schedules or commercial project data.

Fixtures must protect the product boundary: Shutdown Tracker is live execution and reporting software. Microsoft Project remains the schedule authority. Fixtures must not be used to introduce scheduler logic, CPM, critical-path calculation, float calculation, resource levelling, recovery scheduling, automatic date movement, live Project feeds, or uncontrolled Project write-back.

## Allowed Fixtures

- Synthetic Project files created only for testing.
- Sanitized Project files only after all real names, sites, assets, work orders, vendors, costs, people, locations, and commercial data are removed.
- Small safe hand-built MSPDI snippets later, after review and explicit approval.
- Expected parser/export output JSON generated from safe fixtures.
- Text-only fixture manifests.

The first approved synthetic MSPDI fixture is `fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml`. This approval is limited to that reviewed synthetic fixture path and does not permit real Project XML or broad Project-file commits.

## Prohibited Fixtures

- Real shutdown schedules.
- Real MPP, XML, MSPDI, XER, ZIP, PDF, DOCX, screenshots, or generated exports from site or customer schedules.
- Files with work orders, contractors, vendors, people, locations, assets, costs, or commercial data.
- Screenshots of real schedules.
- Exports from real schedules.

## Sanitisation Rules

Sanitization must remove or replace every real-world identifier before any fixture is considered for commit:

- Project, plant, site, unit, asset, equipment, location, vendor, contractor, and organization names.
- Work order numbers, purchase order numbers, cost codes, contract references, document numbers, and tag numbers.
- People names, usernames, email addresses, phone numbers, badge numbers, and crew identifiers.
- Costs, rates, budgets, commercial quantities, claims, delay liabilities, and commercial notes.
- Calendar exceptions, notes, custom fields, baselines, and text fields that could reveal real operations or contracts.

Sanitized files must be reviewed by a human and recorded in the fixture manifest before they are committed.

## Naming Convention

Use neutral, synthetic fixture identifiers. Current reserved names:

- `synthetic-basic-wbs`
- `synthetic-summary-descendants`
- `synthetic-custom-fields`
- `synthetic-assignments`
- `synthetic-reimport-lineage`
- `synthetic-export-leaf-actuals`

Fixture IDs should be lowercase kebab-case. Expected-output files should reuse the fixture ID, for example `synthetic-basic-wbs.expected-import-summary.json`.

## Expected-Output Convention

Expected outputs should be deterministic text files derived from safe fixtures:

- Import summaries should include source format, fixture ID, safe project name, count totals, warning counts, error counts, summary-only scope, and notes.
- Where the worker parse contract is available, expected import summaries should include a `worker_response` block that mirrors stable response fields such as source filename, detected format, project name, counts, warning/error counts, and expected notes.
- Where the worker export artifact contract is available, expected export artifact summaries should include stable project/task identity, allowed field values, count metadata, notes, and structural size/hash expectations. Generated MSPDI/XML artifacts must remain temporary and must not be committed.
- Parser expectations should focus on task, summary-task, resource, assignment, custom-field, calendar, lineage, and warning counts.
- Export preview expectations should describe eligible leaf-task progress/actual updates, excluded summary tasks, approval state, and warning counts.
- Expected outputs must not include real names, work orders, sites, assets, costs, people, vendors, locations, or commercial data.
- `fixtures/import-export/synthetic-basic-wbs/expected-import-summary.json` is the first approved expected summary for a synthetic MSPDI import test.
- `fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json` is the first approved expected summary for a synthetic MSPDI/XML export artifact test.

## Import/Export Test Levels

1. Manifest validation: verify every fixture declares whether it contains real project data and whether it is allowed to commit.
2. Parser summary tests: compare MPXJ parser counts, warnings, errors, and stable worker response fields against expected JSON.
3. Snapshot persistence tests: verify imported task/resource/assignment lineage after parsing is implemented.
4. Export preview tests: verify only approved, export-eligible leaf-task actual/progress fields are selected.
5. Export artifact tests: generate MSPDI/XML from safe data only and compare stable summary/readback fields against expected-output JSON.
6. Manual Microsoft Project round-trip tests: reopen generated MSPDI/XML in Microsoft Project and record review notes.

## Microsoft Project Round-Trip Rules

- Round-trip validation must use synthetic or fully sanitized data only.
- Native MPP writing is out of scope; export artifacts should use MSPDI/XML.
- Export tests must remain controlled, reviewed, approved, batch-oriented, and leaf-task-only for progress/actual fields.
- Manual reopen notes should be text-only and should not include screenshots of real schedules.
- Any mismatch should be recorded as expected-output text or issue notes, not by committing generated exports from real schedules.

## Local-Only Fixture Handling

- Keep unapproved MPP, XML, MSPDI, XER, ZIP, PDF, DOCX, screenshots, and generated exports outside Git.
- Use ignored `_local/` folders for local experiments only.
- Do not copy customer, site, contractor, or commercial project files into the repository.
- Delete local fixture experiments when they are no longer needed.
- The project-worker MPXJ import spike accepts a local path only through the explicit `shutdown-tracker.import-spike.path` property.
- Local spike inputs must stay outside Git unless a future PR explicitly approves a synthetic or fully sanitized fixture.

## Commit Review Checklist

Before committing any fixture metadata or future approved fixture file:

- Confirm `contains_real_project_data` is `false`.
- Confirm `allowed_commit` is `true`.
- Confirm synthetic or sanitization review details are recorded.
- Confirm no real names, sites, assets, work orders, vendors, costs, people, locations, or commercial data are present.
- Confirm no prohibited binary, archive, screenshot, generated export, or real Project file is staged.
- Confirm no parser persistence, domain endpoint, frontend code, scheduler logic, export generation, or uncontrolled Project write-back behavior is included with fixture-only changes.
