# 2026-08-17 — Candidate-schedule differencing, and what it could not see

## Scope

Continue the candidate-schedule branch. In practice: bring `docs/candidate-schedule-authority` up
to date with `main`, review the candidate generator that landed on it earlier the same day through
pull request [#4](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/4), and act on
whatever that review found. No new product surface.

The second entry for 2026-08-17; the first is
[Fresh repository review and green baseline](2026-08-17-fresh-repo-green-baseline.md).

## What was found

### The branch had drifted from `main` in one direction only

`docs/candidate-schedule-authority` was cut before pull request #2 merged, so it carried none of
the session-history protocol that the same protocol now requires it to follow. `main` was four
commits ahead of it and the merge was clean.

### The differencing check could not see most of the document it claimed to prove unchanged

This is the finding worth keeping. The candidate generator's entire authority argument rests on
one comparison: write the approved values into the accepted source, then diff the written file
against the source and fail unless every difference is an approved `(task UID, field)` pair. The
commit message and `docs/product/project-candidate-schedule-handoff.md` both state that this
"proves every other value is exactly the accepted source's."

It did not. The comparison matched a parent's children into a map keyed on element local name,
with `putIfAbsent`. Where an MSPDI parent has several children of the same name, only the first
was ever compared and the rest were invisible — and because both sides collapsed to the same
single key, a change in how many there are produced no difference either.

MSPDI repeats element names everywhere. The repository's own synthetic fixture is enough to show
the size of the hole:

```text
$ grep -o '<[A-Za-z]*>' fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml \
    | sort | uniq -c | sort -rn | head -3
     10 <WorkingTime>
     10 <ToTime>
     10 <FromTime>
      7 <WeekDay>
```

Of a calendar's seven `<WeekDay>` children, one was checked. Of a working day's `<WorkingTime>`
children, one. A task's second and later `<PredecessorLink>` elements — the dependencies that make
the candidate a schedule rather than a list — were not compared at all. Element attributes were
not compared anywhere.

Nothing in the shipped writer touches any of that, so no artifact generated today was wrong. The
defect was in the proof, not in the product: the guarantee was resting on the writer's intent,
which is precisely what the differencing exists so as not to rely on.

## What changed

The comparison moved out of `MpxjMspdiExportArtifactService` into `MspdiCandidateDifference`, and
now matches children by element name **and occurrence**, compares element attributes, and keeps
matching `<Task>` elements on Microsoft Project UID so a task is still compared against the task it
is rather than whatever sits in its position. `project-candidate-schedule-handoff.md` states the
matching rule, since it is what makes the claim above true.

`docs/goals/ACTIVE.md` now records this goal. It had said no engineering goal was active while a
branch was implementing one.

## Decisions

**Extracted the comparison rather than fixing it in place.** It is the authority proof for the
whole candidate mechanism and it had no test of its own — it could only be exercised by generating
an artifact, which can only demonstrate the differences the writer is capable of producing. A
check that cannot be shown a difference it should catch is not evidence. As a separate class it
takes two documents and an approved-input map, so the tests hand it a dropped `<PredecessorLink>`,
an altered third `<WeekDay>`, a duplicated approved field, and a changed attribute directly.

**Occurrence-indexed keys rather than a natural key per element type.** Matching `<Calendar>` on
its `<UID>`, `<Resource>` on its `<UID>`, `<Assignment>` on its own identity and so on would be
order-independent, which reads as the more principled option. It was rejected: it needs a table of
which element types carry which identity, that table would have to track the MSPDI schema, and a
wrong or missing entry fails open — exactly the failure mode being closed. Occurrence keys need no
schema knowledge and fail closed, at the cost of reporting a difference if a candidate ever
reordered siblings. Nothing reorders siblings; the writer edits in place.

**Tasks stay keyed on UID**, unlike every other element. Reordering tasks is a real thing an MSPDI
file can do without changing what the schedule means, and comparing task 3 against task 5 because
they swapped positions would report differences that are not differences. This was the existing
behaviour and it is right; it is now covered by a test that says so.

**Rejected: also comparing text on elements that have element children.** Mixed content does not
occur in MSPDI, and the added path would be unreachable code carrying an implied claim about
documents this product does not accept.

**Rejected: comparing namespace declarations as attributes.** A serializer may legitimately move
or repeat one. Comparing them would fail candidates over a difference that is not one.

## Verified

Linux, Java 21.0.12, Node 22.

| Check | Result |
| --- | --- |
| `mvn test` | 410 tests, 0 failures, 0 errors, **0 skipped** (349 API, 61 worker) |
| `MspdiCandidateDifferenceTests` | 11 tests, all passing |
| `npm ci`, `npm test` | 84 passed across the three workspaces |
| `npm run build` | both apps built |
| `git diff --check` | clean |

A negative check confirmed the new tests bite. Reverting `indexByKey` to the previous name-keyed
`putIfAbsent` matching and removing the attribute comparison, while leaving the path format alone
so the failures isolate the behaviour rather than the message, fails exactly five of the eleven:
the altered third `<WeekDay>`, the dropped `<PredecessorLink>`, the added `<WeekDay>`, the
duplicated approved field, and the changed attribute. The other six pass under both, which is
correct — they cover behaviour this session did not change.

`bash scripts/db/validate-migrations.sh` was **not** run: it needs Docker, which this machine does
not have. No migration or SQL file is touched by this session's changes, and the Docker path is
covered by the branch's own CI run.

No manual Microsoft Project check was performed. That gate is unchanged and still pending.

## Left open

- Pull request [#3](https://github.com/dezrobbo1/Shutdown-Tracker---Claude/pull/3) is a **draft**
  and unmerged, per `AGENTS.md`. Its body still describes the branch as documentation-only, which
  was true when it was opened and stopped being true when #4 merged into it.
- Delta classification and the planner adoption record — the read-only source-versus-candidate
  comparison surface, and the record of what a planner decided — remain unimplemented.
- The manual Microsoft Project round-trip gate remains pending, and is now the only thing standing
  between the candidate mechanism and a claim that it works end to end.
- `.mpp`-sourced snapshots cannot produce a candidate, by decision rather than by omission. If a
  planner ever needs one, that is a format-conversion question, not a bug.
