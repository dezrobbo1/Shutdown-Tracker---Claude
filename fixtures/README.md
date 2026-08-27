# Fixtures

Fixtures support import/export and offline-sync testing.

## Policy

- Real Microsoft Project schedule files are permitted as committed test fixtures when added
  deliberately with a documented purpose.
- Do not commit secrets, credentials, `.env` files, local databases, or generated build artifacts.
- Keep local experiments outside Git or under ignored `_local/` folders.

## Layout

- `import-export/` — synthetic MSPDI fixtures with manifests and expected-output JSON.
- `project-files/` — real Microsoft Project XML fixtures. `boiler/` holds the before/after
  evidence pair behind [the progress field contract](../docs/product/project-progress-field-contract.md).

See [Import/Export Fixture Strategy](../docs/testing/import-export-fixture-strategy.md) for the
review checklist.
