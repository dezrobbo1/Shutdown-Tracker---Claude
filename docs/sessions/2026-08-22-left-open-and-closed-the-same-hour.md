# 2026-08-22 — Left open, and closed the same hour

A short entry, for one reason: the first item on the "Left open" list in
[A handover that had aged](2026-08-22-a-handover-that-had-aged.md) was closed about twenty minutes
after that entry was written, and entries are not rewritten after the fact. It also records the
check that justified the deletion, which happened after that entry was already committed.

## Scope

Item 1 of the previous entry — fold `docs/concept/README.md` into `docs/product/README.md` — landed
as pull request #23. Items 2 to 9 of that list stand unchanged.

## What was found

The previous entry proposed a *merge*, on the assumption that the concept pack held product framing
with nowhere else to live. It did not. Checked section by section against the rest of the
repository before deleting anything, every claim it made was already stated elsewhere, and in most
cases with more detail:

| Concept pack content | Already stated in |
|---|---|
| Progress methods — `% Complete`, `Physical % Complete`, `% Work Complete`, state-only | `docs/product/README.md:76-81` |
| MVP inclusions and exclusions | `ADR-008`, which lists ten exclusions and six inclusions against the concept pack's nine and nine. The one exclusion ADR-008 does not carry — automatic permissions from Project categories — is in `docs/product/README.md` under Product boundary |
| Multi-valued resource-derived categories | `product/project-operational-mapping.md:120`, `ADR-011:34` |
| Formula and lookup-backed fields as read-only context | `product/project-operational-mapping.md:150` |
| Summary-plus-descendants and multi-summary Critical Work Packages | `product/critical-watchlist-permissions.md:11,37,38` |
| Editable Gantt exclusion | `ADR-008`, `AGENTS.md:105`, `product/ux-anti-slop-rules.md:40` |
| Authority model, application experiences, handoff chain | `docs/product/README.md`, and more current there |

So nothing was carried forward. The merge became a deletion.

## Decisions

**The MVP scope boundary needed a named owner before the concept pack could go.** Both
documentation-authority lists advertised `docs/concept` as the place holding the high-level product
and MVP definition. Deleting it without saying where that responsibility went would have left the
boundary owned by nothing in particular. `README.md` and `CONTRIBUTING.md` now name ADR-008
explicitly, which is where the boundary actually lives and always did.

**A second entry rather than an edit.** `README.md` in this folder says an entry is not rewritten
once written, and that a later change gets its own entry linking back. Correcting the previous
entry's list in place would have been the smaller diff and the wrong one — the list was accurate
when it was written, and the record of it being wrong later is the useful part.

## Verified

- `mvn test` on merged `main` — BUILD SUCCESS, 523 passing (448 `services/api`, 75
  `services/project-worker`), 0 failures, 0 skipped.
- `npm test` — 144 passing. `npm run build` — both apps build, `api-client` type-checks clean.
- Every markdown link in the repository resolves, and no reference to `docs/concept` survives
  outside prose in the archived handover brief and the previous entry's own "Left open" list.

Merged `main` was checked directly rather than trusting the two pull requests: #22 and #23 branched
from `675e2da` independently, so neither pipeline had tested the combination that merging produced.

## Left open

Items 2 to 9 of [the previous entry](2026-08-22-a-handover-that-had-aged.md#left-open), unchanged.
Next in order of value to risk: the two unreferenced fixture example files, then the two
`@ConditionalOnProperty` spike runners, then a repeated-`Resource` case in
`MspdiCandidateDifferenceTests`. The first item that is not free is retiring the six dead
`ProgressExportState` values, which needs a new migration and the Docker Compose migration job.
