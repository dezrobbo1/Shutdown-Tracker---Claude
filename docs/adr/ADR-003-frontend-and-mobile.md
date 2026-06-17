# ADR-003: Frontend and Mobile

Status: Draft

## Context

The product needs a management console and a mobile field experience.

## Decision

Use React and Vite for the Master Console and Mobile Field App. Build the field app as a mobile-first PWA rather than a native app for the MVP.

## Consequences

- The web stack can share TypeScript packages and UI primitives.
- Offline-capable behavior can be developed with browser storage and service worker patterns.
- Native mobile features are excluded until explicitly approved.
