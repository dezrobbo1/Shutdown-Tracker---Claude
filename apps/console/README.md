# Master Console

Purpose: React and Vite application for shutdown control, planners, coordinators, supervisors, package owners, and managers.

Current status: scaffolded React/Vite shell with shared API client wiring and opt-in live import/export review data fetching.

The console renders synthetic review state by default for:

- source-file validation status
- parsed snapshot review
- task lineage review
- export preview candidates
- import/export review API client operations

To fetch live review data from the API, configure:

```text
VITE_SHUTDOWN_TRACKER_API_BASE_URL=http://localhost:8080
VITE_SHUTDOWN_TRACKER_PROJECT_ID=<review-project-id>
VITE_SHUTDOWN_TRACKER_IMPORT_SNAPSHOT_ID=<optional-snapshot-id>
VITE_SHUTDOWN_TRACKER_EXPORT_BATCH_ID=<optional-export-batch-id>
```

When `VITE_SHUTDOWN_TRACKER_PROJECT_ID` is absent, the console stays in synthetic review mode and does not call the backend. When it is present, the console reads import snapshot summaries, the selected or latest snapshot detail, and the optional export preview batch. The refresh button repeats those read-only calls.

The console imports `@shutdown-tracker/api-client` and can be configured with `VITE_SHUTDOWN_TRACKER_API_BASE_URL`. It does not upload source files, store uploaded files, parse Project files, create import batches, create live execution records, generate MSPDI/XML, approve exports, or write back to Microsoft Project.

Local commands from this folder:

```text
npm run dev
npm test
npm run build
```
