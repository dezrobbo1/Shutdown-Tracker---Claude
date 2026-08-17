# Project Candidate Schedule Handoff

## Purpose

This document defines the product contract between reviewed Shutdown Tracker execution inputs and Microsoft Project schedule recalculation.

The goal is not to prevent the schedule from changing. The goal is to make every change attributable, reviewable, reversible, and planner-controlled.

## Core rule

> Shutdown Tracker controls and audits the approved execution inputs. Microsoft Project calculates the candidate schedule. The planner controls adoption.

## Five objects that must not be confused

### Accepted source schedule

The immutable Project file/snapshot used as the planning baseline for the current execution cycle. It has a source file hash and snapshot identity.

### Approved execution input

One exact reviewed fact such as a progress percentage, actual start, actual finish, or another field explicitly enabled by the active handoff policy.

Approval of one input does not mean approval of every value Microsoft Project may later calculate from it.

### Approved-input manifest

An immutable list of the exact inputs approved for one candidate calculation. It includes source snapshot/file identity, task identities, field/value pairs, candidate IDs, approval IDs, actor/timestamp provenance, and a manifest hash.

### Candidate schedule

A new disposable Project schedule produced from the accepted source plus the approved-input manifest. Microsoft Project is allowed to recalculate this candidate. The candidate receives a separate file/artifact identity and hash.

### Candidate delta

The semantic comparison between the accepted source and the candidate schedule.

Every difference should be classified as one of:

- **Approved Shutdown Tracker input** — the exact fact the planner approved before calculation.
- **Microsoft Project-calculated consequence** — a dependent value Project recalculated.
- **Unchanged source fact** — preserved context.
- **Unexpected/unexplained difference** — a change that requires investigation before acceptance.

## Authority model

| Authority | Owner | Responsibility |
| --- | --- | --- |
| Execution-input authority | Shutdown Tracker review workflow | Capture and approve exact execution facts |
| Calculation authority | Microsoft Project | Recalculate the disposable candidate schedule |
| Adoption authority | Planner | Accept, reject, supersede, or manually adopt the candidate |

## Target workflow

```text
accepted source schedule + hash
        ↓
field execution facts
        ↓
supervisor review
        ↓
planner input approval
        ↓
approved-input manifest + hash
        ↓
Project processing against disposable copy
        ↓
candidate schedule + hash
        ↓
source-versus-candidate semantic delta
        ↓
planner candidate review
        ↓
accepted / rejected / superseded
        ↓
optional manual adoption as next master
```

The accepted source/master must never be overwritten as part of candidate generation.

## Input fields versus calculated consequences

The direct-input policy and the candidate delta are separate concerns.

A field that Shutdown Tracker is not allowed to propose directly may still change after Microsoft Project recalculates the candidate.

Examples:

| Value | Direct Tracker input by default? | May change in Project-calculated candidate? |
| --- | --- | --- |
| Percent Complete | Policy-controlled | Yes |
| Physical % Complete | Policy-controlled / project-specific | Yes |
| Actual Start | Policy-controlled | Yes |
| Actual Finish | Policy-controlled | Yes |
| Planned Start/Finish | No | Yes |
| Duration | No | Yes |
| Summary roll-ups | No | Yes |
| Assignment progress/work | No by default | Yes |
| Slack/Criticality | No | Yes |
| Dependencies/constraints/calendars | No by default | Normally preserved unless planner deliberately changes them in Project |

The presence of a Project-calculated consequence in the candidate does not expand Shutdown Tracker's direct input authority.

## Field support model

Do not represent field support as a single boolean. Track these dimensions separately:

- recognised by the importer/candidate vocabulary;
- reviewable as an execution fact;
- authorised as a direct input by product policy;
- supported by the selected handoff mechanism;
- enabled for the current project/import profile.

A failed diagnostic for one handoff mechanism means **unsupported by that handoff mechanism**, not permanently unsupported by the product.

### Progress semantics

- `% Complete` is duration-progress and can trigger Project duration/actual calculations.
- `Physical % Complete` is physical-scope progress and should be enabled only where the site uses it consistently.
- `% Work Complete` is work/assignment progress and should be deferred unless Project resource assignments and Work are intentionally maintained.
- Start/Pause/Resume/Block/Complete buttons are Tracker execution events; they are not automatically equivalent to Project percentage fields.

## Handoff mechanisms

The product may support more than one mechanism behind the same approved-input contract.

### Full-source MSPDI candidate

Create a complete candidate from an accepted full Project source and apply approved inputs without stripping required Project context. This remains valid only when manual Project testing proves the exact inputs survive and the resulting candidate is reviewable.

### Planner-controlled Microsoft Project companion

A future Windows companion may:

1. verify the exact accepted source file hash;
2. open a disposable copy in Microsoft Project;
3. apply only the approved input manifest through Project's own supported automation/object model;
4. allow Project to recalculate;
5. save a new candidate under a new path;
6. produce candidate/delta evidence;
7. leave the accepted source/master untouched.

This mechanism requires a dedicated implementation review before production use.

### Manual planner input package

A fallback mode may present the approved inputs as a signed/reviewable package for the planner to enter manually in Project. It is slower but preserves the authority model while other mechanisms mature.

## Candidate review

The planner review surface should show:

- source schedule identity/hash;
- candidate schedule identity/hash;
- Project version/build used for calculation;
- approved-input manifest and approvals;
- project finish movement;
- planned-date/duration changes;
- summary roll-up changes;
- assignment/work changes;
- critical/slack changes reported by Project;
- unexplained differences;
- candidate acceptance/rejection decision.

A read-only Gantt or timeline comparison is permitted here if it helps the planner understand impact. It must not become a schedule editor or a Shutdown Tracker calculation engine.

## Adoption

`Candidate accepted` is not the same as `master adopted`.

If the planner chooses to make the candidate the next master, record the adoption separately with:

- candidate hash;
- adopted file/hash when available;
- adopted by/at;
- source/master lineage;
- any manual edits performed after candidate review.

Shutdown Tracker must not claim adoption merely because a candidate opened successfully.

## Verification gates

A candidate handoff passes only when:

1. the accepted source remains unchanged;
2. the exact approved inputs are traceable into the Project calculation;
3. the candidate is a separate artifact;
4. Project-calculated consequences are identifiable;
5. unexplained changes are surfaced;
6. the planner can reject the candidate without affecting the source;
7. candidate and source hashes are recorded;
8. the planner decision is audited.

Do not fail a candidate merely because Microsoft Project legitimately recalculated dependent schedule fields. Fail when an approved input is lost/altered, the source is overwritten, provenance is missing, or an unexplained change cannot be reviewed safely.

## Current implementation implication

Existing minimal MSPDI/XML patch generation should be treated as an authority-boundary and diagnostic mechanism until it proves complete candidate-schedule behaviour. Security controls around authoritative candidates, exact approvals, stale-data rejection, immutable audit, and batch provenance remain valuable and should be preserved regardless of which candidate-calculation mechanism is selected.
