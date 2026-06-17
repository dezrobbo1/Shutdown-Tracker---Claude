# ADR-009: UX/UI Architecture

Status: Draft

## Context

Different users need different operational surfaces during shutdown execution.

## Decision

Provide a Master Console for coordination, review, reporting, and export approval. Provide a Mobile Field App for assigned work, fast updates, problem/action entry, evidence capture, handover notes, and sync state.

## Consequences

- Console navigation starts with Today, Tasks, Problems, Evidence, and Exports.
- Mobile navigation starts with My Work, Today, Problems, Evidence, and Sync.
- Schedule editing and dependency planning are not part of the baseline UX.
