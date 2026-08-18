# 2026-08-18 — Where an inserted element lands, and why nothing noticed

## Scope

Continue the candidate-schedule branch. The differencing that proves Shutdown Tracker authored
nothing but the approved inputs was reviewed and repaired the previous day; this session reviewed
the other half of the same mechanism — the writer that puts an approved value into the source
document — and acted on what that found. No new product surface.

## What was found

### The branch was finished and green; the gap was in what the checks could see

`docs/candidate-schedule-authority` was in sync with its remote, six commits ahead of `main`, and
GitHub Actions was green on its head (`a564a92`, all four jobs). Pull request
[#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3) is a draft and remains one.

### No approved field is an overwrite; every one is an insertion

No task in `fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml` carries
`<PercentComplete>`, `<ActualStart>` or `<ActualFinish>`. Every write the tests exercise therefore
goes through `MspdiTaskElementOrder` and `insertionPointFor`, which decide *where* in the task the
new element goes. `<Task>` children are an `xsd:sequence`, so that placement is part of whether
Microsoft Project will open the file at all.

Nothing asserted it. The artifact tests compared element *name sets*
(`assertThat(directElementNames(...)).contains("PercentComplete", "ActualStart")`), and
`MspdiCandidateDifference` matches children by name and occurrence, which is deliberately blind to
where among differently named siblings an element sits. A field written in entirely the wrong
position satisfied both. Replacing `insertionPointFor` with a plain append — the crudest possible
regression — failed **none** of the thirteen tests that existed.

### The placement rule failed open on exactly the case its own javadoc anticipated

`positionOfOrLast` returned `Integer.MAX_VALUE` for an element MPXJ's MSPDI binding does not
model, with the comment "Unknown elements sort last". That is what a comparator would do; the
caller is not a sort but a linear scan that returns the first child positioned after the new
element. An unknown element therefore stopped the scan wherever it happened to sit, and the
approved field was inserted immediately before it.

The class javadoc already contemplates the source that triggers this: "A source file may
legitimately carry extension elements this MSPDI binding does not model." A file written by a
newer Microsoft Project than MPXJ 16.4 models is the realistic case.

Given a source whose task 3 carries one unmodelled element between `<OutlineNumber>` and
`<OutlineLevel>`, the approved `<ActualFinish>` landed at sequence position 10 instead of after
`<Summary>`:

```text
UID, ID, Name, Active, Manual, Type, IsNull, CreateDate, WBS, OutlineNumber,
ActualFinish, UnmodelledTaskElement, OutlineLevel, Priority, Start, Finish,
Duration, DurationFormat, Work, Summary, PredecessorLink
```

Generation **succeeded**. The differencing raised nothing, because the candidate and the source
agree on every element's name and occurrence; only their order differs. The result is a candidate
that passes every automated check in the repository and is out of schema order.

### The order table itself is correct today

`MspdiTaskElementOrder` reads the sequence from MPXJ's JAXB `propOrder` rather than a transcribed
list. Read directly out of `org.mpxj.mspdi.schema.Project$Tasks$Task` on the classpath, it yields
109 entries with **no** `propOrder` entry lacking a matching declared field, and the positions the
writer depends on are `Summary` 28 < `PercentComplete` 49 < `ActualStart` 54 < `ActualFinish` 55 <
`PredecessorLink` 90. So the mechanism was placing fields correctly for the committed fixture; the
defect was reachable only through a source the fixture does not represent.

## What changed

`insertionPointFor` now skips elements whose schema position the binding does not know, instead of
treating them as belonging last. `positionOfOrLast` is replaced by `knownPositionOf`, which returns
`OptionalInt.empty()` for such an element and so cannot be used to place anything.

`loadPositions` no longer falls back to the raw JAXB property name when a `propOrder` entry has no
matching declared field. That fallback put a camel-cased name in the table that no MSPDI element
ever matches, so the affected element would silently lose its position and stop being able to place
anything; an unreadable binding now fails at load.

Three tests cover the placement claim directly, and `MspdiTaskElementOrderTests` covers the order
table. `docs/product/project-candidate-schedule-handoff.md` states where the sequence comes from
and, explicitly, that the differencing does not cover placement.

## Decisions

**Skipped unmodelled elements rather than refusing to generate.** Failing a candidate because the
source carries an element MPXJ has not caught up with would reject a schedule over something
unrelated to any approved input, which the class javadoc already rejected as an option. Skipping
places the field by the elements whose position is actually known.

**Rejected: inserting immediately after the last known element at or before the target.** It gives
the same answer in every case in this repository and differs only in where an approved field lands
among a run of adjacent unmodelled elements — a position that is unknowable either way, since the
unmodelled element has no position to compare against. It is a larger change to a mechanism whose
correct cases are already right, so the smaller one was taken.

**Made an unreadable binding fail at class load rather than degrade.** The alternative is the
status quo: a partially-resolved table where some elements silently have no position. That fails
open in the same direction as the defect being fixed. Failing loudly costs an MPXJ upgrade a red
test suite, which is the intended signal — the whole reason the order is read from the binding
instead of transcribed is that a silent drift is the thing to avoid.

**Left the differencing alone.** Making it order-sensitive would report a difference whenever a
candidate reordered siblings, and nothing reorders siblings. Placement is a property of the writer,
and it is now tested as one. The handoff document says so, so the differencing paragraph is not
read as covering it.

## Verified

Linux, Java 21.0.12, Node 22.

| Check | Result |
| --- | --- |
| `mvn test` | 416 tests, 0 failures, 0 errors, **0 skipped** (349 API, 67 worker) |
| `MspdiTaskElementOrderTests` | 4 tests, all passing |
| `MpxjMspdiExportArtifactServiceTests` | 15 tests, all passing (13 before this session) |
| `git diff --check` | clean |

Negative checks confirm the new tests bite:

- With `insertionPointFor` reduced to an unconditional append, both new placement tests fail and
  **none** of the thirteen pre-existing tests do.
- Before the fix, `placesApprovedFieldsByTheElementsTheBindingKnowsRatherThanTheOnesItDoesNot`
  failed with the out-of-order document quoted above, while the other fourteen passed.

`bash scripts/db/validate-migrations.sh` was **not** run: it needs Docker, which this machine does
not have. No migration or SQL file is touched by this session's changes, and the Docker path is
covered by the branch's own CI run.

No manual Microsoft Project check was performed. That gate is unchanged and still pending.

## Corrections

`MspdiTaskElementOrder`'s javadoc said "Unknown elements sort last", describing an intent the
calling code did not implement. Nothing in the repository stated the placement guarantee falsely,
but `docs/product/project-candidate-schedule-handoff.md` asserted that inserted elements land at
their schema-sequence position without saying that the differencing does not check it, which left
the claim looking better covered than it was.

## Left open

- Pull request [#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3) is still a
  **draft** and unmerged, per `AGENTS.md`.
- Where an approved field lands among a run of adjacent elements MPXJ does not model is not
  determined by anything, and cannot be until the binding models them. Only their neighbours with
  known positions are respected.
- Delta classification and the planner adoption record remain unimplemented.
- The manual Microsoft Project round-trip gate remains pending.
