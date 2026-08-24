# 2026-08-24 — A control the server would refuse

Phase 1 slice 5 of [the active goal](../goals/ACTIVE.md), the last slice of Phase 1.

## Scope

The field app offered evidence capture to every role and let the server refuse it. In scope: check
`CAPTURE_EVIDENCE` in `apps/mobile-pwa` and say why when it is absent. Out of scope: Phase 2, the
role tiers that will change what the capability map says; offline evidence capture, which is
deferred elsewhere for its own reasons; and any server change — the server was already right.

## What was found

**The goal's description of the gap was half stale, and the half that mattered still held.**
`docs/goals/ACTIVE.md` says `apps/mobile-pwa` "does not reference capabilities at all". It does:
`fieldSession.ts` has exported `fieldSessionAllows` for some time, and `App.tsx` already gated three
controls with it — `SUBMIT_TASK_PROGRESS` on progress reporting, `SUBMIT_CRITICAL_UPDATE` on the
Critical Update form, and `RAISE_PROBLEM` on raising a problem. What was true is the sentence before
it: `CAPTURE_EVIDENCE` was never checked, and it was the only field capability left unchecked.

This is not the first time — `ACTIVE.md` records its own drift through August, and
[the entry that restated it](2026-08-22-a-goal-that-described-a-younger-repository.md) drew the
lesson. What this instance adds is a finer version of it: the *claims* in that document age faster
than its *structure*. The slice list was right about what to do next and in what order; a supporting
sentence under one slice was not. Checking the code before building on the description cost one
grep, which is the cheap half of the advice `AGENTS.md` already gives — do not infer implemented
behaviour from roadmap documents.

**Reading evidence and capturing it are already separate permissions on the server**, and the split
is what makes the gate narrower than the screen. From `OperationalRecordController`:

```text
POST /evidence                     CAPTURE_EVIDENCE
POST /evidence/{id}/content        CAPTURE_EVIDENCE
GET  /evidence                     VIEW_PROJECT
GET  /tasks/{id}/evidence          VIEW_PROJECT
GET  /evidence/{id}/content        VIEW_PROJECT
```

So gating the Evidence screen as a whole would have hidden a list the server would gladly have
served, and taken a read away from somebody who has it.

**A coordinator is the case that motivates the slice.** `CAPTURE_EVIDENCE` is held by `field_user`,
`contractor`, `supervisor` and `inspector`. `coordinator` and `shutdown_control` are not on that
list, and neither is a stranger to the field app: a coordinator holds `SUBMIT_TASK_PROGRESS` and
`RAISE_PROBLEM`, and shutdown control holds `RAISE_PROBLEM` and `SUBMIT_CRITICAL_UPDATE`. Each uses
write surfaces this app already gates correctly, and each was offered one more that the server would
refuse. That is not a theoretical role: it is a real identity on a real screen.

**The console had already decided how this should look**, in `components.tsx`:

> A control the current role may not use. Disabled rather than hidden, with the reason on the
> element. Hiding it leaves someone hunting for a control that is not there; showing why makes it
> obvious that the next step belongs to a different role.

and its `EvidenceZone` states the reason as "Capturing evidence is a field, contractor, supervisor,
or inspector responsibility."

## What changed

`EvidenceScreen` takes `canCapture`, wired at the call site from
`fieldSessionAllows(session, "CAPTURE_EVIDENCE")`. The file input, the caption and the send button
go inert without it, the send path itself refuses, and the notice above the list says which of the
two refusals is in play. The task picker and the evidence list are untouched: they are reads.

Two decisions came out of the markup as named functions — `evidenceCaptureNotice` and
`evidenceCaptureDisabled` — following the file's existing habit of exporting small pure decisions
(`validateFieldProgress`, `screenTitle`, `mobileChipTone`). That habit earns its keep here for a
specific reason, under Verified below.

## Decisions

**Disabled rather than hidden, quoting the console's reason verbatim.** The alternative was to drop
the capture form for a role that cannot use it, which is tempting on a phone where space is scarce.
Rejected: the goal asks that the same operational state read the same in both applications, and the
console had already answered this exact question for this exact capability. Copying its sentence
means a supervisor moving between the two reads one rule, not two.

**The reason sits above the list, not beside the button.** The field app's other three gates put
their reason next to the control, and this one does not, because this screen's capture form only
renders once a task is chosen. A reason placed beside the button would be invisible to a reader who
has not selected anything — which is exactly the reader who has not yet discovered they cannot
capture. Above the list, it is read first.

**The capture advice is replaced, not supplemented.** The line previously said either "A photo is
sent as you take it" or, offline, "capture it again when you are back on". Told to somebody who
lacks the capability, the second is a false promise: they will not be able to capture when they are
back on. The notice is one line with three outcomes rather than two lines that can contradict.

**The inputs are disabled, not only the button.** The app's other gates disable the submit control
alone, and that is right where the inputs are the screen. Here, letting somebody frame and shoot a
photo before refusing it is worse than a plainly inert control — the photo is the work.

**Nothing was hidden from the read path, and no server change was made.** The server's split was
already correct, and the temptation to "simplify" by gating the whole zone would have removed a
permission a coordinator holds.

## Verified

Everything below was run in this session, on this branch.

- `npm test` — **150 passing**, being 73 console, **49 mobile-pwa** (43 before, plus the six added
  here) and 28 api-client. `ACTIVE.md` records the expected frontend baseline as 144; that figure
  predates the service worker's 13 tests, which are on an unmerged branch and not in this count.
- `npm run build` — clean on all three workspaces, `tsc --noEmit` included.
- `mvn test` — **535 passing**, 460 in `services/api` and 75 in `services/project-worker`, 0
  skipped. Exactly the count the goal names. The backend is untouched by this change and was run
  because the contract asks for it.
- `git diff --check` clean.
- **The new tests were confirmed to bite**, by severing the gate twice and watching them fail:
  dropping `!state.canCapture` from `evidenceCaptureDisabled` failed "refuses the send control on
  the permission alone"; making the notice ignore `canCapture` failed both "says which of the two
  refusals it is" and "leaves the evidence a role cannot add to still readable". Both were restored
  and the suite re-run green. This is why the two decisions were extracted as functions: without a
  DOM, the assertion the mutation has to fail could not otherwise be written.

**Not run:** `scripts/db/validate-migrations.sh`, which needs Docker this machine does not have. No
migration, no SQL and no schema were touched by this change, so there is nothing new for it to
validate; CI runs it on the branch regardless and is the authority for it.

**Not verified: the call-site wiring itself.** The tests cover `fieldSessionAllows(session,
"CAPTURE_EVIDENCE")` for a coordinator session, and they cover both decisions the screen makes with
the result — but not the JSX prop that carries one to the other. Someone replacing
`canCapture={fieldSessionAllows(...)}` with `canCapture` would pass every check here. The field app
has no DOM test environment (no jsdom, no testing-library; `renderToString` is the whole toolkit),
and `EvidenceScreen` renders its form only after a task is chosen in local state, which a server
render cannot reach. Closing this needs a DOM environment, which is its own change and is not worth
making inside a slice. It is named here rather than left for a reader to assume covered.

## Corrections

The active goal's Phase 1 slice 5 states that `apps/mobile-pwa` "does not reference capabilities at
all". That was not true when this session started: three controls were already gated. What was true
is that `CAPTURE_EVIDENCE` specifically was never checked, which is what this slice fixes.

## Left open

- **`docs/goals/ACTIVE.md` is not updated by this branch.** The repository's habit is to record a
  slice as merged after it merges, in its own commit, so the document never claims something that a
  review might still change. When this merges, Phase 1 is complete and the remaining work is Phase 2
  — slices 6 and 7 — plus the two unplaced items and the hygiene track. The stale sentence corrected
  above should be fixed in the same pass.
- **A DOM test environment for the field app**, which would let the wiring gap above be closed, and
  would let the other three gates be asserted as rendered rather than as predicates.
- Everything the goal already lists: the interface walk, the hygiene items, and the deferred
  supervisor review surface. None of them are touched by this change.
