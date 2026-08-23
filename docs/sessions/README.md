# Session History

One file per working session, recording what was investigated, what was decided, and what was
learned — including the things that do not survive into the diff.

## What this is for

Git history and pull requests already record *what changed*. They are the authority for that, and
a session log that repeats them is worse than no session log, because it will drift.

This folder records what the diff cannot:

- what was investigated and found to be fine, so the next session does not re-derive it;
- approaches considered and rejected, and why;
- dead ends, and the evidence that closed them;
- facts about the environment discovered the hard way;
- what was actually verified versus assumed, and which checks could not be run;
- what was deliberately left undone, and what would have to be true to pick it up.

A reader six months from now should be able to open one of these and understand why the
repository is the way it is, without reconstructing the reasoning from commit messages.

## Naming

```text
YYYY-MM-DD-short-slug.md
```

Use the date the session started. If two sessions land on one day, the slug distinguishes them.
Do not renumber or rewrite an entry after the fact; a later session that changes the picture gets
its own entry and links back.

## What an entry contains

Use the template below. Keep sections that have something to say and drop the ones that do not —
an empty heading is noise.

```markdown
# YYYY-MM-DD — Short title

## Scope

One or two sentences: what was asked for, and what was in and out of scope.

## What was found

The state of things at the start, and anything surprising. Include the evidence — an error
message, a test count, a query result — rather than only the conclusion.

## What changed

A short summary with links to the commits or pull request. Not a file-by-file list; the diff
already has that.

## Decisions

Each decision, the alternatives, and why this one. Record the rejected options: the next session
needs to know they were considered.

## Verified

Exactly which checks were run, on what, and their results. Name any check that could not be run
and why. Do not list a check here unless it was actually executed in this session.

## Corrections

Anything stated earlier in the session, in a commit message, or in a pull request that turned out
to be wrong, and what is true instead.

## Left open

What remains, precisely enough to act on.
```

## What must never go in an entry

The repository content rules apply here in full. Never record:

- secrets, tokens, connection strings, or credentials of any kind;
- real customer, site, or schedule data, or anything copied from a real Project file;
- generated export artifacts, or screenshots containing operational data;
- absolute developer paths, machine names, or other local environment detail beyond what a
  reader needs;
- a claim of manual Microsoft Project verification. That gate is recorded through
  [Manual Microsoft Project Round-Trip Evidence](../testing/manual-microsoft-project-round-trip-evidence.md),
  and a session entry must not stand in for it.

An entry is a durable, public record. Write it as one.

## Index

- [2026-08-17 — Fresh repository review and green baseline](2026-08-17-fresh-repo-green-baseline.md)
- [2026-08-17 — Candidate-schedule differencing, and what it could not see](2026-08-17-candidate-schedule-differencing.md)
- [2026-08-18 — Where an inserted element lands, and why nothing noticed](2026-08-18-candidate-element-placement.md)
- [2026-08-18 — A front end that does what it shows: the first three slices](2026-08-18-evidence-carries-its-file.md)
- [2026-08-19 — Raising a problem where there is no signal](2026-08-19-offline-problem-raising.md)
- [2026-08-19 — Which work is yours](2026-08-19-assignment-scoped-work.md)
- [2026-08-20 — The candidate comes back](2026-08-20-candidate-comes-back.md)
- [2026-08-20 — The queue that could never match](2026-08-20-the-queue-that-could-never-match.md)
- [2026-08-21 — Two migrations nobody applied](2026-08-21-two-migrations-nobody-applied.md)
- [2026-08-21 — Identities to walk it as](2026-08-21-identities-to-walk-it-as.md)
- [2026-08-21 — A role the client could not read](2026-08-21-a-role-the-client-could-not-read.md)
- [2026-08-21 — A door at both ends](2026-08-21-a-door-at-both-ends.md)
- [2026-08-21 — A fixture worth walking](2026-08-21-a-fixture-worth-walking.md)
- [2026-08-21 — The first walk](2026-08-21-the-first-walk.md)
- [2026-08-21 — The design that was there all along](2026-08-21-the-design-that-was-there-all-along.md)
- [2026-08-21 — A surface that looks operational](2026-08-21-a-surface-that-looks-operational.md)
- [2026-08-21 — Something that knows what ran](2026-08-21-something-that-knows-what-ran.md)
- [2026-08-22 — A handover that had aged](2026-08-22-a-handover-that-had-aged.md)
- [2026-08-22 — Left open, and closed the same hour](2026-08-22-left-open-and-closed-the-same-hour.md)
- [2026-08-22 — A goal that described a younger repository](2026-08-22-a-goal-that-described-a-younger-repository.md)
- [2026-08-22 — One test that walks the whole thing](2026-08-22-one-test-that-walks-the-whole-thing.md)
- [2026-08-23 — The batch says what it carried](2026-08-23-the-batch-says-what-it-carried.md)
