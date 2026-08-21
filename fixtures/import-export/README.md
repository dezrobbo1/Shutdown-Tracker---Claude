# Import/Export

This folder holds text-only import/export fixture metadata and expected outputs.

## Expected Structure

- `fixture-manifest.schema.json`: JSON schema for fixture manifests.
- `example-fixture-manifest.json`: safe synthetic manifest example.
- `expected-import-summary.example.json`: safe synthetic expected import summary example.
- `synthetic-basic-wbs/`: first approved synthetic MSPDI import fixture, manifest, expected import summary, and expected export artifact summary.
- `synthetic-shutdown-areas/`: larger approved synthetic MSPDI fixture shaped like a shutdown, with resource Groups, assignments and aliased task custom fields, plus its manifest, expected import summary, and expected operational mapping.
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

## Operational Mapping Expectations

Operational Mapping resolves categories from assigned-resource Groups, task custom fields and
summary ancestry. All three stay inert on a fixture that carries none of them, which is why a
fixture intended to exercise mapping records what it should resolve rather than leaving it to be
discovered. An `expected-operational-mapping.json` should describe text-only expectations:

- The source mode under test, and the `raw_data` key it reads.
- The distinct values the fixture should yield, and how many resources carry the attribute at all.
- The negative cases the fixture deliberately contains — a resource with no Group, a
  group-carrying resource with no assignment, a blank custom-field value — and what each proves.
- Any task expected to resolve to more than one value, since a single-value mapping would truncate
  it silently.
- The extended-attribute total, with the arithmetic behind it. An attribute defined without an
  `Alias` is dropped by the extractor, so the total is the assertion that the alias filter is real
  rather than assumed.

## Round-Trip Notes

Manual Microsoft Project round-trip validation should use synthetic or fully sanitized data only. Record text-only notes for MSPDI/XML reopen checks using [Manual Microsoft Project Round-Trip Evidence](../../docs/testing/manual-microsoft-project-round-trip-evidence.md). Do not commit generated exports from real schedules, generated artifacts, or screenshots.

## Approved Synthetic Fixtures

Fixtures are **discovered by the tests, not listed in them**: `SyntheticMspdiFixtureImportTests`
walks this folder for `fixture-manifest.json` files and asserts each fixture against the manifest
beside it, and against its own `expected-import-summary.json`. Adding a fixture therefore costs no
test edits, and a fixture added without them is still checked rather than silently uncovered.

Counts in a manifest must be **measured, not predicted**. `custom_fields` reports the parser's own
custom-field container, which does not count a definition carrying no `Alias` — so a fixture with
four extended-attribute definitions and one unaliased reports three. Write the fixture, read the
summary once, and record what it said.

`synthetic-basic-wbs` is a tiny hand-built MSPDI XML fixture with neutral names only. Its expected import summary includes a structured `worker_response` block used by automated worker tests. Its expected export artifact summary is text-only JSON used to verify temporary generated MSPDI/XML output. This approval does not allow real Project XML, real schedules, committed generated exports, screenshots, or broad binary/project-file commits.

`synthetic-shutdown-areas` is the larger hand-built fixture: 48 tasks across four areas, 12 summary
tasks, four completion milestones, eight resources of which seven carry a `Group`, 34 assignments,
and three aliased task custom fields plus one deliberately unaliased. It exists because the surfaces
that a person walking the product reaches first — Operational Mapping, Exports › People, and the
field app's My Work — all stay empty on a fixture with no resources and no assignments. It carries
no `PercentComplete`, `ActualStart` or `ActualFinish`, so every approved field in an export against
it is an insertion, which is what exercises the writer's element placement.

The expected export artifact includes a synthetic `actual_start` with the non-zero explicit offset `+08:00`. Automated readback verifies that the reviewed Microsoft Project local wall-clock value is preserved; the human Microsoft Project reopen remains pending.
