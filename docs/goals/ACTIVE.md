# Active Goal — PR #48 Export Integrity Final Review

## Status

Active.

Pull request [#48](https://github.com/dezrobbo1/Shutdown-Tracker/pull/48) must remain **draft** throughout this goal. Do not merge it and do not mark it ready for review.

Expected branch:

`backend/enforce-export-integrity`

The manual Microsoft Project open/reopen round-trip is deliberately outside the automated work in this goal and remains pending.

## Outcome

Bring PR #48 to an independently verified state where every automated export-integrity gate is complete and the only remaining release gate is the documented human Microsoft Project round-trip.

Do not assume the PR description or previous validation report is correct. Review the complete diff against `main`, trace the end-to-end authority chain, reproduce the important database and application guarantees, and make only the smallest corrections required by confirmed findings.

The target authority chain is:

```text
authoritative export candidate
→ exact approval event
→ preview selection by candidate identity
→ sealed preview line
→ batch approval revalidation
→ generation-time locking and revalidation
→ narrowed worker request
→ request-specific MSPDI/XML allowlist
→ generated-state metadata
```

An approval must authorize one exact execution fact for one project, accepted snapshot, imported task, field, normalized old value, normalized proposed value, source identity/version, candidate identity, and approval identity. It must not be reusable for another task, field, value, snapshot, project, or source version.

## Corrected implementation claims to verify

The corrected branch must provide:

- immutable normalized `export_candidate_records`;
- current export-integrity policy version 1 introduced by the corrected, unmerged V007;
- exact candidate-to-approval and candidate-to-preview-line binding;
- preservation of V006 historical business values with legacy policy, candidate, approval-reference, and event-order fields left null;
- accepted-snapshot, task-identity, old-value, proposed-value, source, and approval freshness checks;
- deterministic approval-event ordering;
- sealed and immutable preview membership;
- stable database lock ordering across approval and generation;
- a worker contract limited to `percent_complete`, `actual_start`, and `actual_finish`;
- request-specific task and field allowlisting in generated MSPDI/XML;
- a committed PostgreSQL validation suite for clean install, populated upgrades, integrity assertions, concurrency, and migration rollback;
- a 21-table V001–V007 baseline;
- passing Java, TypeScript, frontend build, and migration validation.

Treat each item as a claim requiring evidence, not as an established fact.

## Success criteria

### Exact candidate authority

- Every current-policy preview line is derived from an immutable authoritative candidate.
- The preview caller cannot authoritatively override project, snapshot, imported task, Project UID/ID, field, old value, new value, source identity/version, or source fingerprint.
- Every current-policy approval event identifies exactly one authoritative candidate through a database-enforced relationship.
- An approval for candidate A cannot satisfy candidate B.
- A newer approval event invalidates a preview that captured an older approval identity, even when the approval state is unchanged.
- Missing or ambiguous authority fails closed.
- Unsupported future policy versions fail closed.

### Value normalization

- `percent_complete` uses one canonical whole-number representation within 0–100.
- Semantically equivalent inputs such as `75`, `75.0`, and `075` do not create different approved facts.
- Proposed `actual_start` and `actual_finish` values use one documented whole-second canonical date-time rule consistent across candidate creation, previewing, revalidation, worker handoff, and XML verification.
- Imported actual baselines retain their available microsecond precision under a separate canonicalizer used for exact freshness comparison.
- Proposed-value normalization preserves the intended Microsoft Project local wall-clock component; the worker does not convert that component to UTC.
- `physical_percent_complete` remains readable where required for historical/internal compatibility but cannot become newly export eligible.

### Baseline and task freshness

Before preview sealing, approval, generation, and generated-state recording where applicable, prove for every candidate that:

- the project snapshot still exists and remains accepted;
- the candidate belongs to the batch project and snapshot;
- the imported task still exists in that project and snapshot;
- Microsoft Project task UID, ID, name or trace identity, and leaf status match the reviewed candidate;
- the current imported value normalizes to the captured old value;
- the proposed normalized value matches the authoritative candidate;
- the exact approval identity and state remain current;
- the field remains recognized and export-authorized;
- candidate uniqueness within the batch remains valid.

Any failed line must block the complete batch.

### Concurrency and locking

- There is no check-then-use race involving preview membership, candidate facts, approval authority, snapshot status, task baseline/identity, worker output, or generated-state persistence.
- Lock acquisition follows one documented stable order.
- Approval-event insertion cannot change authority between final validation and artifact output.
- Snapshot or task changes cannot invalidate reviewed authority after final validation and before generated state is committed.
- Reversed multi-source contention has a documented outcome; deadlock retry may remain a follow-up only if integrity still fails closed.
- Worker failure rolls back database lifecycle changes and does not falsely mark a batch generated.

### Historical compatibility

- V006 business rows are not rewritten, normalized, deduplicated, deleted, or assigned invented chronology.
- Historical physical-percent and duplicate rows remain readable.
- Legacy V006 terminal batches remain readable.
- Legacy V006 draft and approved batches cannot newly progress under policy 1.
- New policy, candidate, approval-reference, and event-order columns remain null on historical rows where required.
- Pre/post deterministic hashes over historical business columns match exactly.

### Worker and artifact boundary

Only these fields may reach the worker or MSPDI/XML writer:

- `percent_complete`
- `actual_start`
- `actual_finish`

The worker and writer must reject or fail closed on:

- `physical_percent_complete`;
- unknown fields or policy values;
- numeric enum aliases;
- unknown or duplicate JSON properties;
- duplicate task/field candidates;
- summary-task candidates;
- missing, invalid, or mismatched Project task identity;
- invalid or fractional percentages under the current rule;
- invalid date-time values;
- task membership or value differences between the request and generated XML;
- any schedule-authority element outside the explicit allowlist.

No native `.mpp` may be written. Artifact generation, opening, and verification metadata must never imply that the master `.mpp` was updated.

### Reproducible PostgreSQL evidence

The committed validation suite and CI must reproduce:

- clean V001–V007 installation;
- expected table count and key database objects;
- populated V006-to-V007 upgrade preservation;
- candidate, approval, and preview-line relationship enforcement;
- candidate, approval, and line immutability;
- candidate uniqueness and field-authority enforcement;
- accepted-snapshot and task/baseline drift rejection;
- approval identity and state drift rejection;
- changed approvals on ineligible lines blocking mixed batches;
- line insertion versus sealing;
- concurrent duplicate insertion;
- approval changes versus approval and generation;
- worker failure rollback;
- an intentional late V007 failure leaving no partial migration objects or V006 business-data changes.

Fake repository tests do not replace PostgreSQL evidence for constraints, triggers, foreign keys, locking, concurrency, or rollback.

### Documentation and pull request accuracy

The controlled handoff lifecycle remains:

1. Candidate created — master `.mpp` not updated.
2. Candidate approved — master `.mpp` not updated.
3. Export preview created — master `.mpp` not updated.
4. Export batch approved — master `.mpp` not updated.
5. MSPDI/XML artifact generated — master `.mpp` not updated.
6. Artifact opened in Microsoft Project — master `.mpp` not updated.
7. Artifact verified in Microsoft Project — master `.mpp` not updated.
8. Planner manually updates or saves the master `.mpp` — outside Shutdown Tracker automation.

- Product, API, worker, migration, testing, and operational documentation describe the implementation that actually exists.
- Documentation distinguishes candidate creation, candidate approval, preview creation, batch approval, artifact generation, Project open, Project verification, and planner-controlled master-file save.
- The PR body reports Java tests, PostgreSQL validation, GitHub Actions, Bash execution, PowerShell wrapper status, and the manual Project gate as separate evidence without presenting one as proof of another.
- Direct PowerShell wrapper execution and its underlying PostgreSQL transaction-pattern validation are reported separately.
- The manual Microsoft Project round-trip remains explicitly pending.
- PR #48 remains draft.

## Non-goals

Do not implement or expand:

- CPM, critical-path, float, dependency scheduling, recovery scheduling, resource levelling, or automatic date movement;
- native `.mpp` output or Microsoft Project automation;
- automatic master-file updates;
- broad task-progress, evidence, problem, handover, or communications features beyond what is strictly required for export-candidate integrity;
- frontend feature work or visual design;
- authentication or authorization expansion;
- asynchronous queues;
- worker filesystem path confinement;
- filesystem orphan compensation;
- broad HTTP restructuring;
- unrelated dependency upgrades or refactors.

Worker HTTP timeout and lock-duration observability, filesystem compensation, asynchronous processing, path confinement, authentication, and deadlock retry handling may remain separately documented follow-ups unless inspection proves one is necessary to preserve current export integrity.

## Required validation

Run the strongest available validation from the repository root.

At minimum:

```text
git status -sb
git diff --check
mvn test
npm ci
npm test
npm run build
bash scripts/db/validate-migrations.sh
```

Also run or inspect the committed focused validation for:

- authoritative candidate creation and immutability;
- candidate-to-approval binding;
- preview selection by candidate ID;
- value normalization;
- snapshot, task identity, old-value, proposed-value, source, and approval drift;
- policy-1 and legacy readability/freeze behavior;
- deterministic concurrency cases;
- intentional late-migration rollback;
- worker request deserialization;
- shared contract field restrictions;
- MSPDI/XML task membership, identity, value, and element allowlisting.

Verify GitHub Actions for the final branch head. Do not treat a previously green run as evidence for later commits.

Before completion:

- inspect every changed file in the complete PR diff;
- inspect staged content before committing;
- confirm no secrets, real Project files, generated exports, database files, screenshots, IDE state, absolute developer paths, or temporary validation output are included;
- confirm the backend worktree is clean after push;
- confirm `frontend/rebuild-review-shell-ia` retains its branch, HEAD, status, and existing content fingerprints.

Report exact test totals, exact migration outcomes, and any check that could not be run.

## Safety constraints

- Preserve existing commits. Add new commits only when a confirmed correction or required goal/document update justifies them.
- Do not amend, rebase, squash, rewrite history, or force-push.
- Push only `backend/enforce-export-integrity`.
- Keep PR #48 draft.
- Do not merge PR #48.
- Do not modify another worktree.
- Do not change Windows execution policy.
- Do not install global tooling without explicit approval.
- Do not commit generated artifacts, temporary SQL copies, Docker volumes, local database data, real schedules, customer/site data, or secrets.
- If the implementation already satisfies a criterion, record the evidence instead of introducing speculative churn.

## Manual Microsoft Project gate

This goal does not authorize claiming or fabricating a manual Microsoft Project result.

After all automated criteria pass, the remaining human gate is to generate a synthetic MSPDI/XML artifact and have a planner manually verify that it:

- opens successfully in Microsoft Project;
- preserves the intended task UID and ID identity;
- contains only approved leaf-task values for the three authorized fields;
- excludes summary-task actuals;
- does not perform schedule recalculation or update the master `.mpp` through Shutdown Tracker.

Keep generated artifacts outside Git and record only sanitized text evidence according to the repository testing guide.

## Completion conditions

The automated portion of this goal is complete only when:

- the full PR diff has received an independent end-to-end authority review;
- no unresolved material export-integrity defect remains;
- every success criterion is either proven by code plus reproducible tests or identified as a precise blocker;
- relevant focused and full tests pass;
- the PostgreSQL clean-install, populated-upgrade, concurrency, and atomicity suite passes;
- final-head GitHub Actions are green;
- documentation and the PR body accurately match the implementation and evidence;
- the branch is pushed without rewriting history;
- PR #48 remains draft;
- the backend worktree is clean;
- the frontend worktree is demonstrably unchanged;
- the manual Microsoft Project round-trip is the only remaining gate and is explicitly reported as pending.

If a material defect remains or a required automated check cannot be completed, finish with a precise blocker report rather than claiming review readiness.
