# Fixtures

Fixtures support future import/export and offline-sync testing without exposing real project data.

## Policy

- Do not commit real customer, shutdown, turnaround, construction, site, vendor, contractor, work order, asset, cost, people, location, or commercial data.
- Do not commit real MPP, XML, MSPDI, XER, ZIP, PDF, DOCX, screenshots, generated exports, or uploaded source archives.
- Binary fixture files are blocked by default unless a future PR explicitly approves a small synthetic or fully sanitized test file.
- Keep local experiments outside Git or under ignored `_local/` folders.
- Use text-only manifests and expected-output JSON where possible.

See [Import/Export Fixture Strategy](../docs/testing/import-export-fixture-strategy.md) for the full policy and review checklist.
