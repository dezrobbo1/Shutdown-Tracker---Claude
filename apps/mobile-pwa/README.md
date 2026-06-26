# Mobile Field App

Purpose: mobile-first PWA for field supervisors, leading hands, contractors, inspectors, and execution crews.

Current status: scaffolded React/Vite mobile shell with static Task Progress Review states for product review.

The app includes a web app manifest and static synthetic field-work state for mobile layout review. It shows progress cards, a short submit-progress visual flow, and sync queue examples for queued, failed, server-received, and conflict states.

The mobile progress surfaces are visual-only. They do not implement IndexedDB, production offline queues, background sync, evidence upload, task execution writes, supervisor/planner review writes, import/restore flows, export generation, or Project write-back.

## Visual shell limitations

The current mobile Task Progress Review surfaces are static/synthetic visual review surfaces. They are not production offline sync, not production task progress submission, and not final mobile IA.

Before the next UI PR:

- keep mobile top-level navigation to My Work, Today, Problems, Evidence, Sync;
- keep My Work focused on assigned work before diagnostics;
- replace the three large sync/status tiles with a compact sync banner;
- reduce each task card to task name, area/work package, state, percent where relevant, one blocker/evidence indicator, one sync indicator, and one primary action;
- replace visible `Synthetic Task A1` style labels with sanitized realistic examples;
- keep all write-like controls disabled until APIs exist;
- keep queued/failed/server-received copy explicit.

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
