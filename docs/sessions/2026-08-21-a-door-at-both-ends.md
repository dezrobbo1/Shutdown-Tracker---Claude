# 2026-08-21 — A door at both ends

## Scope

Phase 0's third item: import and artifact download through the interface. Both are product gaps
rather than testing conveniences — a planner could get a schedule in only by `curl` and the
generated candidate out only from the server's filesystem.

## What was found

**The import sequence needs no new endpoint.** `SourceFileUploadService.upload` already calls
`importBatchService.createPending` and returns the `ImportBatchRecord` inside its response, so the
whole chain is two calls and the batch id comes from the first. Both client methods already existed
and were called by nothing.

**A rejected upload is HTTP 200.** `SourceFileUploadResponse.accepted` is a boolean, and a rejected
file returns `accepted: false` with a `rejectionReason` and no `sourceFile` or `importBatch`.
Branching on a thrown error — the obvious way to write this — would report "that is not a Project
file" as a successful import and leave the planner with no batch and no explanation.

**`DisconnectedProjectParseJobClient` is on by default** (`matchIfMissing = true`), so the parse
request answers 502 and records the batch as failed unless the worker flag is set. The panel says
so rather than leaving a planner to interpret a 502.

**There is no GET on `/source-files` or `/import-batches` anywhere.** A reload between the two calls
strands the pending batch, because `requestParseSummary` refuses anything that is not `PENDING`.

**`ExportArtifactStorage` had no read path at all** — only `prepareExportArtifact`. And the artifact
was surfaced in the console as `exportFileUri`, a path under the server's
`/var/lib/.../export-artifacts` rendered into the page. In a browser that is a broken link.

## Decisions

**No list endpoint for pending import batches.** Re-uploading is a working recovery, the outcome
does not need it, and adding an endpoint to paper over a two-call sequence is more surface than the
problem deserves. The gap is stated in the panel copy and in
`docs/product/frontend-visual-review-scope.md` so the next person does not rediscover it.

**The read path delegates to `LocalFileStore`; generation deliberately does not.** Generation
returns a path and the *project worker* writes the bytes, under
`<root>/<projectId>/<batchId>.mspdi.xml` rather than the `<root>/<uuid>/<name>` layout `store`
produces. That difference does not matter for reading, which only requires the file to sit inside
the root and be a regular file — and a test now says so. Routing generation through `store` as well
would take those bytes away from the worker that owns them.

**A missing file is 409, not 500.** The row saying an artifact was generated stays true when the
store has since lost the bytes. The returned-candidate endpoint already made this choice; this
follows it.

**Gated on `GENERATE_EXPORT_ARTIFACT`, not `VIEW_PROJECT`.** The artifact is a complete Microsoft
Project schedule with the approved inputs written into it, not a task list. Whoever may cause one
to exist is who may read it back; `VIEW_PROJECT` would hand the whole plan as a file to any
contractor with read access.

**`/artifact`, not `/content`.** An export batch's "content" is arguably its lines. This is the
file.

**`saveBlob` moved out of `EvidenceZone` into `download.ts`.** Two zones saving files justifies one
helper; three would be a smell. Both downloads are fetched rather than linked because the actor
headers travel on the request and an `<a href>` cannot carry them — which is what leaves a blob to
save. `link.download` is what keeps that safe, and the comment saying so moved with the code.

**Rejected: dropping the boundary sentence with the path.** "Open it in Microsoft Project yourself;
saving the master plan stays your decision" is a product boundary statement, not a caption for the
link. It stays; only the unreachable path went.

## Verified

| Check | Result |
| --- | --- |
| `mvn test` | **515 tests, 0 failures, 0 errors, 0 skipped** (448 API + 67 worker) |
| `npm test` | **134 tests** across the three workspaces |
| `npm run build` | both applications built |
| `git diff --check` | clean |

`LocalExportArtifactStorageTests` covers the read path four ways, including a URI outside the
configured root and a non-`file:` scheme — the URI arrives from a database column, so a row naming a
file this store never wrote is the case worth refusing.

`scripts/db/validate-migrations.sh` was not run: no migration changed, and this host has no Docker.

## Left open

- **Nothing has been imported yet.** The live database still holds no tasks, snapshots or source
  files, and the only fixture declares no resources, assignments or custom fields — so Operational
  Mapping, Exports › People and the field app's My Work stay inert until a fixture worth walking
  exists. That is the next Phase 0 item.
- **The parse worker flag** is on in this deployment, but nothing in the repository asserts that a
  deployment which enables export generation also enables parsing. A mismatch is a 502 at the least
  convenient moment.
- **No eviction story for generated artifacts**, unchanged: `prepareExportArtifact` writes and
  nothing removes.
