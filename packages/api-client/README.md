# API Client

Purpose: hand-maintained TypeScript API client for the console and future mobile PWA API wiring.

## Current Scope

- Typed import review methods for listing snapshots, reading snapshot detail, and accepting or rejecting parsed snapshots.
- Typed source-file upload method for the local-profile endpoint that stores accepted files and creates pending import batches.
- Typed import-batch parse-summary handoff method for requesting worker-owned summary parsing of pending import batches.
- Typed task lineage review methods for listing links, creating suggested links, and accepting or rejecting suggested links.
- Typed export preview methods for creating and reading draft preview batches.
- Typed export batch lifecycle methods for approving, rejecting, and recording generated artifact metadata.
- A small review API surface manifest used by the console to show which local-profile operations are wired.

The client does not fetch data by itself, persist state, call MPXJ directly, parse uploaded files in the API, generate files, implement auth, run offline sync, or write back to Microsoft Project.

## Local Commands

```text
npm test --workspace @shutdown-tracker/api-client
npm run build --workspace @shutdown-tracker/api-client
```
