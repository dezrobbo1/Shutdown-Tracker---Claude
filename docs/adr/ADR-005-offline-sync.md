# ADR-005: Offline Sync

Status: Draft

## Context

Field users need workflows that can tolerate unreliable connectivity.

## Decision

Design mobile offline workflows around IndexedDB, service workers, Cache API, idempotency keys, visible sync state, and explicit conflict/error handling. Treat Background Sync as progressive enhancement only.

## Consequences

- Correctness must not depend on Background Sync availability.
- Sync state must be visible to field users.
- API operations that may be queued need idempotency and replay-safe behavior.
