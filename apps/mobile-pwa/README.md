# Mobile Field App

Purpose: mobile-first PWA for field supervisors, leading hands, contractors, inspectors, and execution crews.

Current status: scaffolded React/Vite mobile shell with static Task Progress Review states for product review.

The app includes a web app manifest and static synthetic field-work state for mobile layout review. It shows progress cards, a short submit-progress visual flow, and sync queue examples for queued, failed, server-received, and conflict states.

The mobile progress surfaces are visual-only. They do not implement IndexedDB, production offline queues, background sync, evidence upload, task execution writes, supervisor/planner review writes, import/restore flows, export generation, or Project write-back.

Local commands from this folder:

```text
npm run dev
npm test
npm run build
```
