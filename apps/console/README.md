# Master Console

Purpose: React and Vite application for shutdown control, planners, coordinators, supervisors, package owners, and managers.

Current status: scaffolded React/Vite shell with shared API client wiring.

The console currently renders static synthetic review state for:

- source-file validation status
- parsed snapshot review
- task lineage review
- export preview candidates
- import/export review API client operations

The console imports `@shutdown-tracker/api-client` and can be configured with `VITE_SHUTDOWN_TRACKER_API_BASE_URL`. It does not fetch backend data during render, store uploaded files, parse Project files, create import batches, create live execution records, generate MSPDI/XML, or write back to Microsoft Project.

Local commands from this folder:

```text
npm run dev
npm test
npm run build
```
