# Product Walkthrough

How to walk Shutdown Tracker from a field update to a returned candidate schedule, as three
different people, on the review deployment.

This exists because `docs/goals/ACTIVE.md` requires that the journey has been walked by a person end
to end. A test proves the chain to CI; it does not prove it to you. The four defects that prompted
this goal — two unset storage roots, no way to import through the interface, no way to download the
artifact, and a fixture with nothing in it — were all found by looking, and none of them would have
failed a test.

## Running it locally

The walk below is written against the review deployment, but the same chain runs on one machine.
Four processes, in this order:

1. **PostgreSQL**, holding `shutdown_tracker` owned by `shutdown_tracker`. `infra/docker/docker-compose.postgres.yml` brings one up where Docker is available.
2. **The project worker**, `services/project-worker`, on 8081. Import parsing and artifact generation go through it; without it steps 2 and 11 fail.
3. **The API**, `services/api`, on 8080, with the `local` profile — it points Flyway at `infra/migrations` and applies them on start, enables persistence, and turns on the trusted-header actor. Nothing authenticates those headers, which is why the profile is local only.
4. **The two front ends**, `npm run dev` in `apps/console` (5173) and `apps/mobile-pwa` (5174). Both ports are fixed and strict, because the two are meant to run together.

Each app asks its own origin for the API, because a deployment serves them together. A dev server is
a second origin and the API declares no CORS, so `vite.config.ts` proxies `/api` and `/actuator` to
`http://127.0.0.1:8080`. Set `SHUTDOWN_TRACKER_API_ORIGIN` when the API is somewhere else.

The API needs the identity seeder and a project to seed against, and both services need to agree on
where files live — the worker reads the source file the API stored and writes the artifact the API
serves:

```text
SHUTDOWN_TRACKER_REVIEW_DEMO_IDENTITIES_ENABLED=true
SHUTDOWN_TRACKER_REVIEW_PROJECT_BOOTSTRAP_ENABLED=true
SHUTDOWN_TRACKER_PROJECT_PARSE_WORKER_ENABLED=true
SHUTDOWN_TRACKER_PROJECT_EXPORT_WORKER_ENABLED=true
SHUTDOWN_TRACKER_WORKER_AUTH_SHARED_SECRET=<any value, the same for both>
SHUTDOWN_TRACKER_SOURCE_FILE_STORAGE_LOCAL_ROOT=<shared, both services>
SHUTDOWN_TRACKER_EXPORT_ARTIFACT_STORAGE_LOCAL_ROOT=<shared, both services>
SHUTDOWN_TRACKER_EVIDENCE_STORAGE_LOCAL_ROOT=<api>
SHUTDOWN_TRACKER_CANDIDATE_SCHEDULE_STORAGE_LOCAL_ROOT=<api>
```

Point the apps at the bootstrapped project with `VITE_SHUTDOWN_TRACKER_PROJECT_ID`, and give each a
starting actor with `VITE_SHUTDOWN_TRACKER_ACTOR_ID`, `_NAME` and `_ROLE`. Those are a starting
point only; the identity pickers below override them.

**Leave `VITE_SHUTDOWN_TRACKER_API_BASE_URL` unset.** It makes the client call that origin directly
rather than its own, which goes around the proxy and straight into the missing CORS configuration.
`SHUTDOWN_TRACKER_API_ORIGIN` is the one to set when the API is not on this machine.

Storage roots that resolve somewhere unwritable are the failure that cost a deployment: the health
check stays green and the first upload fails at request time. Give each root a real directory.

A local run starts on an empty project, so step 1 below is where you begin. Import
`fixtures/import-export/synthetic-shutdown-areas/synthetic-shutdown-areas.mspdi.xml` — it is the
fixture with resources and assignments, so Operational Mapping, Exports › People and My Work have
something in them.

## Before you start

You need three identities, seeded and switchable. They are created by a guarded runner that is
disabled by default; the deployment enables it. Confirm they exist:

```text
GET /api/review-identities
```

Four come back — a field user, a supervisor, a planner and a viewer — all on the synthetic review
project. If that route 404s, the seeder is off and nothing below will work: the console will be one
person and every field control will be refused.

**Switch identity, not role.** The console's picker is in the sidebar under *Acting as*; the field
app's is on the **Sync** screen. Both change who the app acts as, including the user id sent to the
server. There is no role selector any more, and its absence is deliberate — it changed what the
interface offered and never what the server answered.

**The field app refuses to switch identity while reports are queued.** Send them first. Switching
mid-queue would submit one person's work under another's name.

## The walk

Each step names who to be. The point of doing it as three people is that the chain is built so that
two of them cannot be the same person: a planner may not submit progress, and a supervisor may not
approve an export.

| # | As | Where | Do this | Working looks like |
|---|---|---|---|---|
| 1 | Planner | Exports › Import review | Choose a Project file under *Bring a schedule in* | The file is named back with its size, and a *Parse this file* button appears |
| 2 | Planner | same panel | *Parse this file* | Task, resource and assignment counts appear, and a new snapshot is selected below |
| 3 | Planner | Exports › Import review | *Accept* the snapshot | Its state becomes Accepted. Execution now refers to this version |
| 4 | Planner | Exports › Mapping | Configure a category from **Resource Group** and resolve it | Categories resolve against the crews the file carries |
| 5 | Planner | Exports › People | Link the field identity to a Project resource | The link names the resource and how many leaf tasks it carries |
| 6 | **Field user** | Mobile › My Work | Submit progress against one of your tasks | The card shows the update as queued, then sent |
| 7 | **Supervisor** | Tasks › Supervisor review | Accept the update | It leaves your queue and enters the planner's |
| 8 | Planner | Exports › Planner review | Approve it | It becomes eligible for export |
| 9 | Planner | Exports › Batches | *Create export preview* | A batch appears with one line per approved field |
| 10 | Planner | same panel | Approve the batch | Its state becomes Approved |
| 11 | Planner | same panel | Generate the artifact | The filename, the updated-of-total task counts and a hash are reported |
| 12 | Planner | same panel | **Download candidate schedule** | An `.mspdi.xml` file saves to your machine |
| 13 | — | Microsoft Project | Open the downloaded file | See the gate below |
| 14 | Planner | same panel | Record the open, then the verification | The batch reaches Verified |
| 15 | Planner | Exports › Batches | Return the recalculated candidate | A candidate run is recorded with three hashes beside each other |

Step 5 matters more than it looks. **Without it the field user's My Work list is empty**, and that
is correct rather than broken — an unlinked person is shown nothing rather than the whole
schedule.

## What the walk proves, and what it does not

Walking the chain proves the links between steps hold. It does **not** discharge either of the two
human gates:

- **The manual Microsoft Project gate.** Opening the generated candidate in Microsoft Project and
  confirming it is a complete schedule is recorded through
  [Manual Microsoft Project Round-Trip Evidence](manual-microsoft-project-round-trip-evidence.md).
  No automated result may be reported as that gate.
- **Returning a candidate is not the same as returning a recalculated one.** If you return the file
  you downloaded, its hash equals the generated artifact's, and the record says so honestly — but
  nothing has recalculated it. The candidate a planner returns must be one Microsoft Project
  actually saved before any delta drawn from it means anything.

The chain also does not prove correctness of what it carried. It proves that what the field
submitted, a supervisor accepted and a planner approved is what reached the artifact.

## Recording what you find

Findings go in this document, under the dated section below, **as you go**. Do not fix them
mid-walk: stopping to fix loses the thread and mixes a repair into a review. Raise each as its own
slice afterwards.

For each finding record what you were doing, who you were, what you expected, what happened, and
whether you could carry on. "Could carry on" is the part that decides whether it blocks the next
walk or merely annoys.

**Anything that needs a terminal is a finding.** The point of this walk is that a person can operate
the product; a step that needs `curl` or `psql` has not been delivered, however well it works.

## 2026-08-21 — first walk

Walked through the API rather than the interface, as the seeded identities, on the synthetic
`synthetic-shutdown-areas` fixture. Every step succeeded.

What it confirmed:

- The chain runs end to end: upload, parse, accept, link, submit, supervisor accept, planner
  approve, export queue, preview, approve, generate, download, open, verify, return.
- The parse summary matched the fixture manifest exactly against the live deployment — 48 tasks, 12
  summary, 36 leaf, 8 resources, 34 assignments, 1 calendar, 3 custom fields.
- **112 extended attributes** were imported, not the 120 the file appears to carry. The eight values
  in the unaliased field are dropped, which is the alias filter working through the real import.
- The generated candidate is a schedule, not an extract: 48 tasks, 8 resources, 34 assignments, 1
  calendar and 24 predecessor links all preserved, with exactly one `PercentComplete` and one
  `ActualStart` inserted — the two approved fields and nothing else.
- Those two insertions landed in schema sequence, after `Summary` and before `ExtendedAttribute`.
  That is the placement logic exercised for the first time on a file that has extended attributes at
  all.
- Both storage roots hold files, so evidence upload and candidate return both work. Before the roots
  were set they resolved under a directory the service user could not write to, and failed at
  request time while the health check stayed green.

Findings:

1. **A rejected request says only "Bad Request".** Three requests were refused during the walk — an
   unknown field, a missing `executionState`, a decision value of `ACCEPTED` where the enum wants
   `SUPERVISOR_ACCEPTED`. Each returned a bare Spring error body with no message naming the field or
   the reason. The applications send typed requests and do not hit this, so it is not a blocker for
   the interface walk; it makes the API hard to drive by hand, and it will make the first
   integration by anyone else slower than it needs to be. *Could carry on: yes.*
2. **The walk was not done through the interface.** Everything above was exercised over HTTP. The
   controls exist and are capability-gated, but "a planner can do this in the console" is still
   unproven by a person clicking it. *Could carry on: yes, but this is the gap this document exists
   to close.*

Not attempted: Operational Mapping (step 4). `expected-operational-mapping.json` records what the
fixture should resolve and nothing yet asserts it.
