# Packages

Shared cross-application contracts and clients live here when they have a real implementation and owner.

Current packages:

- `api-client`: TypeScript client for current import/export review API surfaces.
- `project-import-contract`: Java request/response records for API-to-worker Project import parse handoff.
- `project-export-contract`: Java request/response records for API-to-worker MSPDI/XML export artifact handoff.

Do not reserve empty package directories for possible future abstractions. Add a new shared package only when an implemented capability requires it and its ownership/boundary is clear.
