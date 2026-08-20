# 2026-08-20 — The queue that could never match

Second session of the day. The first returned the candidate schedule; this one found that nothing
could reach the step before it.

## Scope

Asked to review the repository, the flow path, and Design C, and to work towards the Design C look,
a working flow path, and fewer user types. The review produced a three-phase goal; this entry covers
the review and the first slice, which repairs the flow path between planner approval and a created
export preview.

## What was found

**The journey has been severed since the export lifecycle was built.** The console builds its export
candidate list from the planner queue and filters it:

```ts
plannerQueue.filter(u => u.plannerReviewState === "PLANNER_APPROVED" || u.exportState === "ELIGIBLE")
```

`findPlannerQueue` selects `planner_review_state = 'needs_planner_review'`. `recordPlannerDecision`
sets `planner_review_state` and `export_state` in one statement. So a row is either
`needs_planner_review` + `not_eligible`, or `planner_approved` + `eligible` — **the intersection is
empty by construction.** "0 approved" always; the Create export preview button permanently disabled;
and therefore preview, batch approval, artifact generation, the Microsoft Project open, verification
and candidate return all unreachable through the interface.

It is four defects, not one. Fixing only the first produces a button that enables and then fails:

- **A1** the impossible filter, above;
- **A2** `createPreview` registers candidates and goes straight to `exportPreview.create`, never
  recording an approval event. `ExportPreviewService.validateCandidateAuthority` throws 409 when
  `currentApproval == null`. `createApprovalEvent` existed in the API client and was called by no
  application code;
- **A3** `useSnapshotTasks` sorted snapshots by version and took the first, with **no status
  filter**. V007 requires an accepted snapshot, so a parsed-but-unaccepted re-import made candidate
  creation 409;
- **A4** `candidateRequestsFor` emitted `physical_percent_complete`, which is never MVP-export
  authorised, so every preview built from such an update carried a permanently ineligible line.

**Every test passed the whole time**, and still did after A1 was fixed in isolation. Each test proves
one step; the defects live between steps. The console test covering this code built its own fixture
by hand in a state the endpoint it stood for could never return.

Other findings, recorded in `docs/goals/ACTIVE.md` rather than fixed here: `export_state` never
advances past `eligible` and `export_batch_id` is never written; the field app never checks
`CAPTURE_EVIDENCE`; there is no user or membership endpoint, so a membership can only be created by
raw SQL and nobody can walk the chain at all; neither stylesheet has a single `:focus` rule; the code
implements four tones where the design doc defines six status classes.

## Decisions worth keeping

**The export queue is a third queue, not a filter over the second.** The planner queue answers "what
is waiting for my decision"; the export queue answers "what did I already approve that has not
travelled yet". They are disjoint, and an update crosses from one to the other in a single statement.
Deriving either from the other is what produced the defect.

**It is keyed on `export_state`, not on the planner's decision.** This is the load-bearing choice.
`markSuperseded` sets `export_state` and deliberately **leaves `planner_review_state` reading
`planner_approved`** — the planner did approve that value, once, and a decision is a historical fact
that is not rewritten. Only `export_state` distinguishes a value that may still go from one that has
been replaced or blocked. A queue predicated on the decision would offer a superseded value for
export, which is worse than the bug it replaced because it would appear to work. There is a test
holding this in place, and the index it needs was already there — V009 created
`idx_task_progress_updates_export_candidates` for a query nobody had written.

**`export_batch_id IS NULL` is in the predicate even though nothing writes that column yet.** The
clause is inert today. It is written now so the question is answered in one place, rather than added
later beside a list that has already started offering the same update twice. A test sets the column
by hand so the filter is proved rather than assumed.

**Gated on `CREATE_EXPORT_PREVIEW`, not `VIEW_PROJECT`.** The list is the input to a preview and is
only useful to somebody about to build one — the same reasoning `AssignedWorkController.candidates`
already uses for `MANAGE_RESOURCE_LINK`. Deliberately not `PLANNER_REVIEW_TASK_PROGRESS` either:
that capability *produces* the list, and keeping producer and consumer distinct keeps the review and
approval steps visibly separate one level further down. No new capability constant, so
`CapabilityClientParityTests` is untouched — if it had needed editing, the design had drifted.

**The client-side filter was deleted outright rather than corrected.** A predicate stated in two
places is a predicate that will eventually disagree with itself, and this one already had. The server
owns it now.

**A stale-snapshot candidate is named, not dropped.** Updates approved against a schedule that has
since been superseded are counted and explained in the zone rather than silently filtered out. A
planner who cannot see why a preview is short will assume work was lost.

## Rejected

**A console render test asserting the zone calls the right endpoint.** The console tests run in a
node environment through `renderToString`, which does not run effects, so no request is issued during
a render and there is nothing to observe. A source-text assertion would have passed for the wrong
reason. The cross-step guarantee belongs in the journey integration test, which is the next slice,
and the rule that would have caught this is now written into `docs/testing/README.md` instead: a
fixture standing in for an endpoint must satisfy that endpoint's own predicate.

**Building an "approved-input manifest" entity.** The docs name one; no such table exists. It is
already realised as a sealed policy-1 `export_batch` and its `export_batch_lines`, which carry the
seal, the hashes and the approval binding under 1,700 lines of V007 enforcement. A parallel entity
would duplicate that with no new invariant. Recorded as a naming reconciliation for a later slice.

## Verified

Run from the repository root, on this branch:

| Check | Result |
| --- | --- |
| `mvn test` | 480 tests, 0 failures, 0 errors, 0 skipped |
| `npm test` | 122 tests across the three workspaces |
| `npm run build` | both applications built |
| `git diff --check` | clean |

Backend rose 476 → 480, frontend 119 → 122. No migration in this slice, so
`MigrationSchemaTests` is unchanged and `validate-migrations.sh` was not required.

Two mistakes made while working, both worth recording because the shape recurs:

- A `mvn -q` run reported nothing and I read a **stale surefire report** as success. The run had
  failed to compile. A quiet Maven run that prints nothing is not evidence; the test count is.
- The export-batch fixture inserted a batch against a `parsed` snapshot and V007 refused it —
  correctly, since a batch requires an accepted one. Rather than widening the fixture to paper over
  it, snapshot acceptance became its own explicit fixture call, so the precondition the export policy
  cares most about is stated in the test rather than hidden in a helper.

The end-to-end journey has **not** yet been walked by a person. It cannot be until memberships can be
created without raw SQL, which is slice 4. Nothing here should be read as evidence that the whole
chain works — only that the link that was severed is repaired and covered by tests.

The manual Microsoft Project gate is untouched and remains pending.

## Left open

- **The journey test**, next slice, and the reason the rest of this goal is safe to build.
- **`export_state` and `export_batch_id` never advancing**, which keeps the de-duplication clause
  inert and leaves five enum values and a foreign key as dead schema.
- **No way to create a membership**, so the chain cannot be walked by hand yet.
- **`CAPTURE_EVIDENCE` unchecked in the field app.**
- **`critical_updates.idempotency_key`** still has a plain index where two sibling tables have
  partial unique ones. Unchanged for four sessions now.
