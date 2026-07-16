# Mobile Field App

Purpose: mobile-first PWA for field supervisors, leading hands, contractors, inspectors, and execution crews.

Current status: scaffolded React/Vite mobile shell with static Task Progress Review states for product review. The initial visual cleanup pass keeps My Work focused on assigned work, uses sanitised operational examples, and presents sync state in a compact banner.

The app includes a web app manifest and static synthetic field-work state for mobile layout review. It shows progress cards, a short submit-progress visual flow, and sync queue examples for queued, failed, server-received, and conflict states.

The mobile progress surfaces are visual-only. They do not implement IndexedDB, production offline queues, background sync, evidence upload, task execution writes, supervisor/planner review writes, import/restore flows, export generation, or Project write-back.

## Visual shell limitations

The current mobile Task Progress Review surfaces are static/synthetic visual review surfaces. They are not production offline sync or production task progress submission.

The initial cleanup pass now:

- keeps mobile top-level navigation to My Work, Today, Problems, Evidence, Sync;
- keeps My Work focused on assigned work before diagnostics;
- replaces the three large sync/status tiles with a compact sync banner;
- reduces task cards to the minimum operational fields and two status indicators;
- uses sanitised operational examples;
- keeps all write-like controls disabled until APIs exist;
- keeps queued/failed/server-received copy explicit.

Required offline copy:

```text
Saved locally.
Queued on this device. Not yet sent.
Could not send. Still saved on this device.
Server received.
This progress update is not submitted until the server receives it.
Last synced at [time].
```

Use `Thread may be out of date. Last synced at [time].` only for future discussion/comment threads, not for task progress screens.

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
