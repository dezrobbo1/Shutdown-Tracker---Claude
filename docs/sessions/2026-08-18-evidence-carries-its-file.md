# 2026-08-18 — Evidence that carries its file

## Scope

Continue toward a working front end, following the repository's own plan for it. In practice: pick
the first slice from the frontend gaps in the root `README.md`, build it end to end — API,
storage, both applications, tests, documents — and set the active goal that names the rest.

The second entry for 2026-08-18; the first is
[Where an inserted element lands](2026-08-18-candidate-element-placement.md), which is a different
goal and a different branch.

## What was found

### The plan already existed, and part of it was out of date

`docs/product/frontend-visual-review-scope.md` is the frontend plan: the information-architecture
guardrail, the anti-slop rules, the build-status labels, and a "Verified current frontend
capability" table. Two rows of that table no longer described the code:

- **Critical Watch — "Service and repository exist server-side; no controller and no UI."** Both
  exist: `CriticalWatchController` serves watchlists, work packages and Critical Update reporting,
  and `CriticalWatchZone.tsx` is 414 lines of console surface. The root `README.md` had been
  updated and this table had not.
- **Evidence — "No binary upload exists in either app or the API."** True, and the subject of this
  session.

The document's "Next coding implication" was stale for the same reason: it pointed at Critical
Watch and Critical Update review surfaces that have since been built.

### Registering evidence recorded that a file existed somewhere else

`evidence` rows carry `original_filename`, `content_type`, `storage_uri`, `size_bytes` and a status
of `pending_upload`/`uploaded`, with a `evidence_uploaded_has_location_check` constraint tying the
last two together. The schema had been ready for an upload since V010. Nothing uploaded anything.

What that produced in the console was a text field labelled **"Stored at — where the file is kept,
if it has been stored"**, asking a reviewer to type a location by hand, and a panel explaining that
Shutdown Tracker does not store the file. In the field app it was a line of copy: *"Evidence
captured on this device cannot be uploaded yet."* Both were honest. Neither was a product.

Every record either app could create was therefore `pending_upload`, and nothing existed that could
ever move one out of it.

## What changed

Evidence is registered and then its file is uploaded against that record — two calls, in that
order, from both applications. `EvidenceStorage`/`LocalEvidenceStorage` mirror the source-file
storage that already existed; `POST /projects/{id}/evidence/{evidenceId}/content` attaches a
binary to a pending record and moves it to `uploaded`;
`GET /projects/{id}/evidence/{evidenceId}/content` streams it back. The console has a file picker
and a download; the field app captures from the camera. `EvidenceRecord` gained `sizeBytes`, which
is what tells a reviewer the file actually arrived.

The upload also records the SHA-256 the store already computed into `evidence.content_hash`, a
column that has existed since V010 and was being left null. Evidence is a verification artifact;
what was stored has to be identifiable afterwards.

`docs/goals/ACTIVE.md` now carries the frontend goal and the ordered slices behind it, with the two
completed goals kept as history. `frontend-visual-review-scope.md` and `README.md` describe what
the applications do.

### Second slice: nobody could ask what evidence a shutdown has

Evidence is registered against a task and was readable only per task. The console's own boundary
note said so: *"There is no project-wide evidence list yet, so choose the task you are checking."*
Reviewing a shutdown means asking what evidence it has, not asking task by task and remembering the
answers.

`GET /projects/{id}/evidence` returns the project's evidence newest first, bounded at 200. The
Evidence zone opens on that and the task selector narrows it, rather than the two being different
screens. `EvidenceRecord` gained `capturedAt`, without which a newest-first list across tasks
cannot be read, and each row names its task.

## Decisions

**Two calls, not one.** A single multipart endpoint that registered and stored together would be
simpler for the client and would never leave a half-finished record. It was rejected because
`pending_upload` is a real state with a real meaning — evidence that is still outstanding — and the
one-call design would make it unreachable, which is the same as deleting it. It also forecloses the
offline capture in slice 3 of the goal, where the record is registered when a connection returns
and the file follows. The cost is a record that can exist without its file; that is the state being
modelled, and both apps show it.

**The status guard is in the `WHERE` clause, not a prior read.** `attachEvidenceContent` updates
`WHERE ... AND status = 'pending_upload'` and returns the row. Read-then-update would let two
uploads racing on one record both pass the check and the second silently replace the first. A
second upload is now told the evidence already arrived. Replacing the file behind an accepted
record is what supersession is for, and it is a different decision with a different audit trail.

**The binary is written before the row is updated.** Either order can fail in the middle. This one
leaves an unreferenced file; the other leaves a row naming a file that is not there. A row that
lies about its own evidence is worse than a file nobody points at.

**`storage_uri` is confined to the configured root on read.** The value reaches the storage layer
from a database column, and a store that fetches whatever URI it is handed turns an evidence row
into a way to read any file the process can reach. Both the unit test and the database-backed
service test assert the refusal; breaking the confinement fails both.

**Downloads are fetched and saved, never opened.** The server sends `Content-Disposition:
attachment` and `X-Content-Type-Options: nosniff`, but those headers stop applying the moment the
console turns the response into a blob URL — and a blob URL opened in a tab runs in the
application's origin. The link is `download`, so the browser saves it whatever the type says. The
fetch is needed anyway, because the actor headers travel on the request and a plain `href` would
not carry them.

**Field capture needs a connection, and says so.** The offline queue holds small JSON progress
reports with idempotency keys. A queue of megabyte photos needs eviction rules, a storage budget,
and retry behaviour of its own; a queue that fills a phone and then fails to send is worse than one
that never accepted the photo. The screen says which of the two situations the user is in rather
than accepting a capture it cannot deliver.

**The project list is bounded in SQL, and the console says when it was cut.** A shutdown
accumulates evidence for as long as it runs, and this list exists to be read on a screen. Capping in
the console would mean the whole set crossing the wire first; capping without saying so would make a
truncated list look like all the evidence there is. The console's copy of the limit is pinned to the
server's by `EvidenceListLimitParityTests`, following `CapabilityClientParityTests`: a stale copy
fails in the worse direction, because it would stop warning at all.

**Rejected: a narrower download capability.** `docs/product/permission-matrix.md` separates
"Download original evidence" from "View scoped evidence", with field users and contractors limited
to their own. The `Capability` enum has no such distinction, and the existing per-task metadata read
is already gated on `VIEW_PROJECT`. Gating the bytes more tightly than the metadata that names them
would be a permission model half-applied. Download matches the read it accompanies; closing the gap
between the matrix and the enum is a permissions change, not a frontend one.

## Verified

Linux, Java 21.0.12, Node 22.

| Check | Result |
| --- | --- |
| `mvn test` | 426 tests, 0 failures, 0 errors, **0 skipped** (365 API, 61 worker) |
| API tests added | 9 database-backed evidence tests, 6 `LocalEvidenceStorage` tests, 1 limit parity test |
| `npm ci`, `npm test` | 93 passed across the three workspaces (console 49, mobile 22, api-client 22) |
| `npm run build` | both apps built |
| `git diff --check` | clean |

The API count rose from 349 to 365, and the frontend from 84 to 93.

A negative check confirms the confinement tests bite: with the root check in
`LocalEvidenceStorage.read` disabled, `refusesToReadAFileOutsideTheConfiguredRoot` and the
database-backed `readingEvidenceStoredOutsideTheConfiguredRootIsRefused` both fail, and nothing
else does.

`bash scripts/db/validate-migrations.sh` was **not** run: it needs Docker, which this machine does
not have. This session adds no migration — the columns and the
`evidence_uploaded_has_location_check` constraint have been in V010 since it landed — and the
Docker path is covered by the branch's own CI run.

No manual Microsoft Project check was performed. That gate belongs to the completed
candidate-schedule goal and is unchanged.

## Left open

- The remaining slices are named in `docs/goals/ACTIVE.md`: Critical Update reporting from the
  field app, offline problem raising, and assignment-scoped work lists. The last two are not purely
  frontend work — problem creation has no server-side idempotency key,
  and nothing links a Microsoft Project resource to a Shutdown Tracker user.
- Production object storage. `LocalEvidenceStorage` is the development and review implementation
  the architecture already called for; the interface is what production replaces.
- Evidence supersession. A second upload against an accepted record is refused rather than
  superseding it, and nothing yet performs the supersession that
  `docs/product/correction-and-supersession-rules.md` describes.
- The gap between `permission-matrix.md` and the `Capability` enum on evidence download, described
  under Decisions.
- Evidence is not searchable or filterable by status, only by task. A shutdown with more than 200
  records can only reach the older ones through the task filter.
- `docs/goals/ACTIVE.md` is edited by these branches and by `fix/candidate-element-placement`. Whichever
  merges second resolves a small conflict in the Status section; this branch's version supersedes.
