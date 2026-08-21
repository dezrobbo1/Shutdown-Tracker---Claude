# 2026-08-21 — The first walk

## Scope

Phase 0's fifth item, and the first time the chain has been taken end to end. The deployment was
brought up to `main`, the fixture imported into it, and the journey walked from a field update to a
returned candidate schedule. This entry records what the walk found; the repeatable procedure is
[Product Walkthrough](../testing/product-walkthrough.md).

Walked over HTTP as the seeded identities rather than through the interface. That is stated as a
finding rather than glossed: it proves the chain, not the console.

## What was found

**The chain runs end to end.** Upload, parse, accept, link a resource, submit progress, supervisor
accept, planner approve, export queue, preview, approve, generate, download, record the open, record
verification, return the candidate. Fifteen steps, every one of them successful.

**The parse matched the fixture manifest exactly against the live deployment** — 48 tasks, 12
summary, 36 leaf, 8 resources, 34 assignments, 1 calendar, 3 custom fields. The manifest was written
from a measurement taken locally, and this is the first time the same numbers came back out of a
real import through the worker.

**112 extended attributes were imported, not 120.** The file carries eight values in a field defined
without an `Alias`, and the extractor drops them. That arithmetic was asserted in the fixture's
expected output; this is it holding through the real path into the database.

**The generated candidate is a schedule rather than an extract.** 48 tasks, 8 resources, 34
assignments, 1 calendar and 24 predecessor links all preserved, with exactly one `PercentComplete`
and one `ActualStart` inserted — the two approved fields and nothing else. That is the product's
central claim, and it is now true of a real generated file rather than of a fixture.

**Both insertions landed in schema sequence**, after `Summary` at position 28 and before
`ExtendedAttribute` at 93. This is the first time the writer's placement logic has been exercised
against a source that carries extended attributes at all — the small fixture has none, so the
interaction could not previously be expressed.

**Both storage roots hold files.** Evidence upload and candidate return both worked. Before the
roots were set they resolved under a directory the service user could not write to and failed at
request time, while the health check stayed green — which is why nothing had noticed.

**An unlinked field user sees nothing, and that is correct.** Before the resource link, My Work was
empty; after it, six real tasks. The list says which of its causes it is rather than falling back to
the whole schedule.

## Findings

1. **A rejected request says only "Bad Request".** Three requests were refused during the walk: one
   carrying an unknown field, one missing the required `executionState`, and one sending a decision
   of `ACCEPTED` where the enum wants `SUPERVISOR_ACCEPTED`. Each returned a bare error body with no
   message naming the field or the reason. The applications send typed requests and do not hit this,
   so it does not block the interface walk — but it makes the API hard to drive by hand and will
   make anyone else's first integration slower than it needs to be. Raised as its own slice rather
   than fixed mid-walk.
2. **The walk has still not been done through the interface.** The controls exist and are
   capability-gated, but nobody has clicked them. That is the gap the walkthrough document exists to
   close, and it is not closed by this entry.

Not attempted: Operational Mapping. `expected-operational-mapping.json` records what the fixture
should resolve and nothing yet asserts it.

## Decisions

**Findings are recorded, not fixed.** Stopping to repair loses the thread and mixes a fix into a
review. Both findings above are left standing.

**Returning the downloaded artifact is not the Microsoft Project gate.** Its hash equals the
generated artifact's, and the record says so honestly — but nothing recalculated it. The walkthrough
states this explicitly, because a returned candidate that happens to be byte-identical is exactly
the thing that could be mistaken for a round trip.

## Verified

The walk itself, against `https://dez.tsenior.uk` at `main` `0cc92be`. Afterwards the deployment
holds 1 source file, 1 accepted snapshot, 48 imported tasks, 8 resources, 34 assignments, 112
extended attributes, 1 resource link, 1 progress update, 1 export batch with 2 lines, 1 candidate
schedule run, 1 evidence record with its file, and 19 audit events.

This entry adds documentation only. `mvn test`, `npm test` and `npm run build` were run and are
unchanged from the previous slice: **523 backend tests, 0 skipped**, and 134 frontend.

## Left open

- **The interface walk**, per finding 2.
- **A mapping test** asserting `expected-operational-mapping.json`.
- **Error bodies that name the problem**, per finding 1.
- **The migration drift guard**, unchanged and still the last Phase 0 item. V012 and V014 were
  applied to this deployment by hand and nothing records that they were.
