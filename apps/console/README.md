# Master Console

Purpose: React and Vite application for shutdown control, planners, coordinators, supervisors, package owners, and managers.

Current status: scaffolded React/Vite shell with shared API client wiring, opt-in live import/export review data fetching, and a static Task Progress Review visual shell.

The console renders synthetic review state by default for:

- source-file validation status
- parsed snapshot review
- task lineage review
- export preview candidates
- import/export review API client operations
- task progress review and export approval workflow
- supervisor review queue
- planner progress review queue
- progress candidates in export preview
- Microsoft Project verification metadata
- structured blockers and handover summary examples

To fetch live review data from the API, configure:

```text
VITE_SHUTDOWN_TRACKER_API_BASE_URL=http://localhost:8080
VITE_SHUTDOWN_TRACKER_PROJECT_ID=<review-project-id>
VITE_SHUTDOWN_TRACKER_IMPORT_SNAPSHOT_ID=<optional-snapshot-id>
VITE_SHUTDOWN_TRACKER_EXPORT_BATCH_ID=<optional-export-batch-id>
```

When `VITE_SHUTDOWN_TRACKER_PROJECT_ID` is absent, the console stays in synthetic review mode and does not call the backend. When it is present, the console reads import snapshot summaries, the selected or latest snapshot detail, and the optional export preview batch. The refresh button repeats those read-only calls.

The task-progress review surfaces are visual/product-review only and use synthetic data. They do not add production task execution APIs, supervisor review APIs, planner review APIs, production offline sync, evidence upload, handover workflow, generated artifacts, or Microsoft Project write-back.

The console imports `@shutdown-tracker/api-client` and can be configured with `VITE_SHUTDOWN_TRACKER_API_BASE_URL`. It does not upload source files, store uploaded files, parse Project files, create import batches, create live execution records, generate MSPDI/XML, approve exports, calculate schedules, or write back to Microsoft Project.

## Visual shell limitations

The current Task Progress Review surfaces are static/synthetic visual review surfaces. They are not final information architecture and should not be treated as production route structure or backend API contracts.

Before the next UI PR:

- restore the console top-level IA to Today, Tasks, Problems, Evidence, Exports;
- treat Supervisor Review and Planner Review as saved views or sections, not permanent top-level navigation;
- treat Project Verification as part of Exports;
- reduce card/chip density;
- replace visible `Synthetic Task A1` style labels with sanitized realistic examples;
- keep write-like controls disabled until APIs exist;
- keep Project-boundary warnings visible;
- avoid adding more panels to the single overview page.

Relevant product source docs:

- `docs/product/frontend-visual-review-scope.md`
- `docs/product/ux-anti-slop-rules.md`
- `docs/product/design-language-and-status-semantics.md`
- `docs/product/task-progress-review-export-approval.md`

## Local commands

```text
npm run dev
npm test
npm run build
```
