# Master Console

Purpose: React and Vite application for shutdown control, planners, coordinators, supervisors, package owners, and managers.

Current status: scaffolded React/Vite shell only.

The console currently renders static synthetic review state for:

- source-file validation status
- parsed snapshot review
- task lineage review
- export preview candidates

It does not call backend APIs, store uploaded files, parse Project files, create import batches, create live execution records, approve exports, generate MSPDI/XML, or write back to Microsoft Project.

Local commands from this folder:

```text
npm run dev
npm test
npm run build
```
