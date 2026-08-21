# 2026-08-21 — A fixture worth walking

## Scope

Phase 0's fourth item. The doors at both ends of the export chain were built in the previous slice;
this is something worth carrying through them. A second synthetic MSPDI fixture, shaped like a
shutdown, and the test changes that make it — and every later fixture — actually checked.

## What was found

**The only fixture declared `resources: 0, assignments: 0, custom_fields: 0`.** That is not a gap in
coverage so much as a gap in what can be walked: assigned-resource Groups are what Operational
Mapping resolves from, assignments are what the field app's My Work resolves through, and Exports ›
People has nothing to link without resources. All three stay empty no matter how well the import
path works.

**Nothing would have picked a new fixture up.** All eight consumers named `synthetic-basic-wbs`
directly, and `SyntheticMspdiFixtureImportTests` located the repository root by looking for that
exact file. A second fixture would have cost eight edits, and one added without them would have been
checked by nothing at all.

**Nothing checked element ordering in a committed fixture, either.** MSPDI declares the children of
`<Task>` as an `xsd:sequence`, and a fixture that violates it may never open in Microsoft Project —
which would leave every expected-output test validating a document Project would reject. The
existing fixture had never been checked.

## Decisions

**Hand-authored, not written by MPXJ.** Generating gives schema-correct ordering by construction,
but at the cost of hundreds of defaulted elements and writer-version markers in a file the fixture
policy requires a person to review, and of committed bytes that churn on every MPXJ upgrade.

**Ordering is guarded by assertion instead, which is strictly stronger.**
`MspdiFixtureElementOrderTests` checks every committed `*.mspdi.xml` against `MspdiTaskElementOrder`
— which reflects over MPXJ's own JAXB binding rather than transcribing 109 element names, so this
asserts against exactly the authority the export writer uses. It covers the existing fixture too,
and it fails loudly on an MPXJ upgrade rather than quietly producing different bytes.

**Fixtures are discovered, not listed.** The import tests now walk
`fixtures/import-export/*/fixture-manifest.json` and assert each fixture against the manifest beside
it. Adding a fixture costs no test edits, and one added without them is still checked. The manifest
and the expected-import-summary are also asserted against *each other*: two files describing one
fixture is one file too many unless something makes them agree.

**Every number was measured, not predicted.** This mattered. `custom_fields` is **3, not 4** — the
file defines four extended attributes and one carries no `Alias`, and the parser's custom-field
container does not count it. Predicting 4 would have produced a manifest that disagreed with the
parser on its first run.

**The `FieldID` integers were derived from MPXJ, not transcribed.** A wrong id makes
`getFieldType()` return null and the field vanishes silently — the exact failure this fixture exists
to catch. MPXJ's own writer was asked to emit a file with aliased Text1–Text4 and the ids read back
out of it: `188743731`, `188743734`, `188743737`, `188743740`.

**Negative cases are in the fixture on purpose**, so the guards are proved rather than assumed: one
resource with no `Group` at all; one group-carrying resource with no assignment, which a mapping
that read groups without joining assignments would wrongly include; two work tasks assigned across
two different groups, which a single-value mapping would truncate; and eight tasks carrying a value
in the unaliased field.

**No progress or actuals on any task.** Every approved field in an export against this fixture is
therefore an insertion, which is what exercises the writer's element placement — the thing a plain
append once defeated without failing a single test.

## Verified

| Check | Result |
| --- | --- |
| `mvn test` | **523 tests, 0 failures, 0 errors, 0 skipped** (448 API + 75 worker, up from 67) |
| `npm test` | 134 tests across the three workspaces, unchanged |
| `npm run build` | both applications built |
| `git diff --check` | clean |

Measured from the parser rather than asserted from intent: 48 tasks, 12 summary, 36 leaf, 8
resources, 34 assignments, 1 calendar, 3 custom fields. Extraction reports **112** extended
attributes — 48 Area, 40 Discipline, 24 Permit Type — and *not* the 120 it would be if the eight
unaliased values counted. Seven of eight resources carry a group, across four distinct values.

The ordering test was **run against a deliberately mis-ordered fixture first**, and named the fault
precisely: `task UID 4: <Summary> at position 28 follows <ExtendedAttribute> at 93`.

`scripts/db/validate-migrations.sh` was not run: no migration changed, and this host has no Docker.

## Left open

- **The fixture has not been imported into the deployment yet.** Doing so is the walk, not a
  precondition for it.
- **No test resolves Operational Mapping against this fixture end to end.**
  `expected-operational-mapping.json` records what it should resolve, and nothing yet asserts it.
  That is a database test in the API, and it is the natural next thing to hold this fixture honest.
- **`MpxjMspdiExportArtifactServiceTests` still uses only the small fixture.** The most valuable new
  case it could gain is inserting a `PercentComplete` into a task carrying both a
  `<PredecessorLink>` and an `<ExtendedAttribute>` — an ordering interaction the old fixture cannot
  express, because it has no extended attributes at all.
