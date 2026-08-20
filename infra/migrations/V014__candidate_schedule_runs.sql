-- The schedule Microsoft Project calculated, brought back.
--
-- Candidate generation already produces an artifact and proves Shutdown Tracker wrote nothing
-- into it but the approved execution inputs. What Microsoft Project then does to that artifact
-- has never been recorded. Project opens it, recalculates it, and derives dates, durations,
-- roll-ups, slack and criticality from the approved inputs — and none of that comes back, so
-- there is nothing to compare against the source, nothing to classify, and nothing to decide
-- about. This table is where a recalculated candidate lands.
--
-- **A separate entity, not another export batch state.** docs/product/approval-export-state-model.md
-- says so directly: candidate-schedule work introduces a separate run entity rather than
-- overloading `verified`, which means only that a generated artifact opened in Microsoft Project
-- as expected. It does not mean a candidate was recalculated, and it does not mean anything was
-- adopted. Two facts that different people establish for different reasons do not share a column.
--
-- **Bound to the source it must have come from.** The run records the accepted source file and
-- the SHA-256 recorded for it at import. Candidate generation already refuses to build unless the
-- source still matches that hash; a review carries the same requirement for the same reason. A
-- delta computed against a different schedule than the candidate was derived from would classify
-- every difference wrongly, and would do it convincingly.
--
-- **The returned file is evidence, not a baseline.** It lives in its own store rather than in
-- source_files, because source_files is what import batches and snapshots are built from. A
-- candidate becomes a planning baseline only if a planner adopts it and imports it deliberately,
-- which is a later slice and a separate decision.
--
-- **`calculation_pending` is deliberately absent from the state type.** The lifecycle in the state
-- model begins with a pending calculation, which belongs to the planner-controlled Microsoft
-- Project companion. No mechanism in this repository produces that state, so declaring it would
-- claim a capability that does not exist. It can be added when something can reach it.

CREATE TYPE candidate_schedule_run_state AS ENUM (
  'returned',
  'delta_ready',
  'accepted',
  'rejected',
  'superseded',
  'failed'
);

COMMENT ON TYPE candidate_schedule_run_state IS
  'Lifecycle of one Microsoft Project candidate calculation, from docs/product/approval-export-state-model.md. Acceptance is not adoption.';

-- Composite keys so a run cannot name a batch or a file belonging to another project.
-- project_snapshots already carries its own from V007.
ALTER TABLE export_batches
  ADD CONSTRAINT export_batches_id_project_unique
  UNIQUE (id, project_id);

ALTER TABLE source_files
  ADD CONSTRAINT source_files_id_project_unique
  UNIQUE (id, project_id);

CREATE TABLE candidate_schedule_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),

  -- The batch whose generated artifact Microsoft Project was handed. This is what ties a
  -- recalculation back to the exact approved inputs that were written into it.
  export_batch_id UUID NOT NULL,
  project_snapshot_id UUID NOT NULL,
  accepted_source_file_id UUID NOT NULL,

  -- The source hash recorded at import, copied here at return time. Held on the run rather than
  -- read through the batch chain later, so a review always states the schedule it was actually
  -- compared against.
  accepted_source_file_hash TEXT NOT NULL,

  -- What Shutdown Tracker handed Microsoft Project, from export_batches.export_file_hash. Nullable
  -- only because a batch may be superseded and its artifact facts re-established; a run that has
  -- it can show the planner all three identities in one line.
  generated_artifact_hash TEXT,

  state candidate_schedule_run_state NOT NULL DEFAULT 'returned',

  candidate_original_filename TEXT NOT NULL,
  candidate_storage_uri TEXT NOT NULL,
  candidate_content_hash TEXT NOT NULL,
  candidate_size_bytes BIGINT NOT NULL,

  -- What Microsoft Project reported about itself, as the planner recorded it. Free text on
  -- purpose: it is provenance a person types, not a value anything computes from.
  microsoft_project_version TEXT,
  planner_note TEXT,

  returned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  returned_by_user_id UUID NOT NULL REFERENCES users(id),

  superseded_by_candidate_schedule_run_id UUID REFERENCES candidate_schedule_runs(id),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  CONSTRAINT candidate_schedule_runs_export_batch_fkey
    FOREIGN KEY (export_batch_id, project_id) REFERENCES export_batches(id, project_id),
  CONSTRAINT candidate_schedule_runs_snapshot_fkey
    FOREIGN KEY (project_snapshot_id, project_id) REFERENCES project_snapshots(id, project_id),
  CONSTRAINT candidate_schedule_runs_source_file_fkey
    FOREIGN KEY (accepted_source_file_id, project_id) REFERENCES source_files(id, project_id),

  CONSTRAINT candidate_schedule_runs_source_hash_not_blank_check
    CHECK (length(btrim(accepted_source_file_hash)) > 0),
  CONSTRAINT candidate_schedule_runs_filename_not_blank_check
    CHECK (length(btrim(candidate_original_filename)) > 0),
  CONSTRAINT candidate_schedule_runs_storage_uri_not_blank_check
    CHECK (length(btrim(candidate_storage_uri)) > 0),
  CONSTRAINT candidate_schedule_runs_content_hash_check
    CHECK (candidate_content_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT candidate_schedule_runs_size_bytes_check
    CHECK (candidate_size_bytes > 0),
  CONSTRAINT candidate_schedule_runs_metadata_object_check
    CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT candidate_schedule_runs_not_self_superseded_check
    CHECK (superseded_by_candidate_schedule_run_id IS NULL
           OR superseded_by_candidate_schedule_run_id <> id),
  CONSTRAINT candidate_schedule_runs_superseded_consistency_check
    CHECK ((state = 'superseded') = (superseded_by_candidate_schedule_run_id IS NOT NULL))
);

COMMENT ON TABLE candidate_schedule_runs IS
  'One Microsoft Project candidate calculation returned by a planner. Immutable evidence about a recalculation; never a planning baseline and never an adoption.';
COMMENT ON COLUMN candidate_schedule_runs.accepted_source_file_hash IS
  'The SHA-256 recorded for the accepted source at import. A candidate is only reviewable against the schedule it was derived from.';
COMMENT ON COLUMN candidate_schedule_runs.generated_artifact_hash IS
  'The hash of the artifact Shutdown Tracker generated and Microsoft Project opened.';
COMMENT ON COLUMN candidate_schedule_runs.candidate_content_hash IS
  'SHA-256 of the returned file, computed by the server from the bytes it stored rather than accepted from the caller.';
COMMENT ON COLUMN candidate_schedule_runs.microsoft_project_version IS
  'Project version/build as the planner reported it. Provenance for the review, not an input to anything.';

-- The same bytes returned against the same batch are the same candidate, not a second one.
-- Re-uploading is therefore a replay that resolves to the run the first upload created, on the
-- same terms as the idempotency keys the offline queue relies on.
CREATE UNIQUE INDEX idx_candidate_schedule_runs_batch_content
  ON candidate_schedule_runs (export_batch_id, candidate_content_hash);

CREATE INDEX idx_candidate_schedule_runs_project_returned
  ON candidate_schedule_runs (project_id, returned_at DESC);

-- Candidate runs are append-only evidence. What was returned, by whom, against which source,
-- cannot be edited after the fact; only the lifecycle state and the facts a later slice
-- establishes may change, and each of those may be established once.
--
-- The permitted transitions follow the state model's candidate lifecycle. Two of them carry a
-- rule worth stating: a decision requires a delta, because docs/product/project-candidate-schedule-handoff.md
-- binds a planner decision to one candidate hash and one semantic delta, and accepting a schedule
-- nothing has compared is exactly the unreviewed adoption the authority model exists to prevent.
-- Only the transition into `delta_ready` has code behind it in the slice that adds the delta; the
-- decision states are enforced here because the invariant is permanent, not because they are
-- reachable yet.
CREATE OR REPLACE FUNCTION enforce_candidate_schedule_run_integrity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'candidate schedule run history cannot be deleted'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.state IS DISTINCT FROM 'returned'::candidate_schedule_run_state THEN
      RAISE EXCEPTION 'a candidate schedule run begins when a candidate is returned'
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.project_id IS DISTINCT FROM OLD.project_id
     OR NEW.export_batch_id IS DISTINCT FROM OLD.export_batch_id
     OR NEW.project_snapshot_id IS DISTINCT FROM OLD.project_snapshot_id
     OR NEW.accepted_source_file_id IS DISTINCT FROM OLD.accepted_source_file_id
     OR NEW.accepted_source_file_hash IS DISTINCT FROM OLD.accepted_source_file_hash
     OR NEW.generated_artifact_hash IS DISTINCT FROM OLD.generated_artifact_hash
     OR NEW.candidate_original_filename IS DISTINCT FROM OLD.candidate_original_filename
     OR NEW.candidate_storage_uri IS DISTINCT FROM OLD.candidate_storage_uri
     OR NEW.candidate_content_hash IS DISTINCT FROM OLD.candidate_content_hash
     OR NEW.candidate_size_bytes IS DISTINCT FROM OLD.candidate_size_bytes
     OR NEW.microsoft_project_version IS DISTINCT FROM OLD.microsoft_project_version
     OR NEW.planner_note IS DISTINCT FROM OLD.planner_note
     OR NEW.returned_at IS DISTINCT FROM OLD.returned_at
     OR NEW.returned_by_user_id IS DISTINCT FROM OLD.returned_by_user_id THEN
    RAISE EXCEPTION 'what a candidate schedule run returned, and against which source, is immutable'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.state IS DISTINCT FROM OLD.state THEN
    IF NOT (
      (OLD.state = 'returned' AND NEW.state IN ('delta_ready', 'failed', 'superseded'))
      OR (OLD.state = 'delta_ready' AND NEW.state IN ('accepted', 'rejected', 'superseded'))
      OR (OLD.state IN ('accepted', 'rejected', 'failed') AND NEW.state = 'superseded')
    ) THEN
      RAISE EXCEPTION 'candidate schedule run cannot move from % to %', OLD.state, NEW.state
        USING ERRCODE = '23514';
    END IF;
  END IF;

  -- Which run replaced this one is established once, with the supersession itself. Repointing it
  -- afterwards would rewrite the lineage rather than extend it.
  IF OLD.superseded_by_candidate_schedule_run_id IS NOT NULL
     AND NEW.superseded_by_candidate_schedule_run_id
         IS DISTINCT FROM OLD.superseded_by_candidate_schedule_run_id THEN
    RAISE EXCEPTION 'candidate schedule run supersession was already established'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER candidate_schedule_runs_enforce_integrity
BEFORE INSERT OR UPDATE OR DELETE ON candidate_schedule_runs
FOR EACH ROW
EXECUTE FUNCTION enforce_candidate_schedule_run_integrity();
