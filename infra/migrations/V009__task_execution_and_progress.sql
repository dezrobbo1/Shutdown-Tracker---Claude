-- Task execution state and structured progress submissions.
--
-- This is the bridge described in docs/product/task-progress-review-export-approval.md:
--
--   field progress update -> supervisor review -> planner review -> export eligibility
--
-- Without it, export preview has to be fed by hand-made candidates rather than by
-- reviewed execution records.
--
-- Task condition is deliberately not collapsed into a single status. A task can be
-- blocked, awaiting planner review, and not export-eligible at the same time, so each
-- dimension is its own column with its own vocabulary.
--
-- Imported Project rows are never mutated by any of this. Execution truth is stored
-- alongside the snapshot; Microsoft Project remains the schedule authority.

-- 'ready' is in the product's execution vocabulary but was missing from the V001 enum.
-- Added in its own statement and not used until a later migration, because a new enum
-- value cannot be used in the transaction that adds it.
ALTER TYPE task_execution_state ADD VALUE IF NOT EXISTS 'ready' AFTER 'not_started';

CREATE TYPE progress_review_state AS ENUM (
  'draft',
  'submitted',
  'supervisor_accepted',
  'correction_requested',
  'rejected',
  'superseded'
);

CREATE TYPE planner_review_state AS ENUM (
  'not_required',
  'needs_planner_review',
  'planner_approved',
  'planner_rejected'
);

CREATE TYPE progress_export_state AS ENUM (
  'not_eligible',
  'eligible',
  'export_blocked',
  'approved_for_export',
  'in_export_preview',
  'artifact_generated',
  'opened_in_microsoft_project',
  'verified',
  'superseded'
);

CREATE TABLE task_execution_states (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  imported_task_id UUID NOT NULL REFERENCES imported_tasks(id),
  execution_state task_execution_state NOT NULL DEFAULT 'not_started',
  state_changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  state_changed_by_user_id UUID REFERENCES users(id),
  state_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT task_execution_states_task_unique UNIQUE (imported_task_id)
);

COMMENT ON TABLE task_execution_states IS 'Current execution state at the workfront for one imported task. Execution truth is owned by Shutdown Tracker; the imported schedule is not modified.';
COMMENT ON COLUMN task_execution_states.execution_state IS 'What is happening at the workfront. Separate from review state and export state.';

CREATE INDEX idx_task_execution_states_project_state
  ON task_execution_states (project_id, execution_state);

CREATE TABLE task_progress_updates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  imported_task_id UUID NOT NULL REFERENCES imported_tasks(id),

  -- What the field reported.
  execution_state task_execution_state NOT NULL,
  percent_complete NUMERIC(5,2),
  actual_start TIMESTAMPTZ,
  actual_finish TIMESTAMPTZ,
  physical_percent_complete NUMERIC(5,2),
  comment TEXT,

  submitted_by_user_id UUID NOT NULL REFERENCES users(id),
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Independent review dimensions.
  progress_review_state progress_review_state NOT NULL DEFAULT 'submitted',
  supervisor_reviewed_by_user_id UUID REFERENCES users(id),
  supervisor_reviewed_at TIMESTAMPTZ,
  supervisor_review_note TEXT,

  planner_review_state planner_review_state NOT NULL DEFAULT 'not_required',
  planner_reviewed_by_user_id UUID REFERENCES users(id),
  planner_reviewed_at TIMESTAMPTZ,
  planner_review_note TEXT,

  export_state progress_export_state NOT NULL DEFAULT 'not_eligible',
  export_batch_id UUID REFERENCES export_batches(id),

  -- Offline field capture. The device generates the idempotency key so a retried
  -- submission cannot become a second update.
  sync_state sync_state NOT NULL DEFAULT 'synced',
  idempotency_key TEXT,
  offline_local_id TEXT,

  supersedes_progress_update_id UUID REFERENCES task_progress_updates(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  CONSTRAINT task_progress_updates_percent_complete_check CHECK (
    percent_complete IS NULL OR (percent_complete >= 0 AND percent_complete <= 100)
  ),
  CONSTRAINT task_progress_updates_physical_percent_check CHECK (
    physical_percent_complete IS NULL OR (physical_percent_complete >= 0 AND physical_percent_complete <= 100)
  ),
  CONSTRAINT task_progress_updates_actual_dates_check CHECK (
    actual_start IS NULL OR actual_finish IS NULL OR actual_finish >= actual_start
  ),
  CONSTRAINT task_progress_updates_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  -- A planner decision must name the planner who made it.
  CONSTRAINT task_progress_updates_planner_attribution_check CHECK (
    planner_review_state NOT IN ('planner_approved', 'planner_rejected')
      OR planner_reviewed_by_user_id IS NOT NULL
  ),
  -- Likewise for supervisor acceptance.
  CONSTRAINT task_progress_updates_supervisor_attribution_check CHECK (
    progress_review_state <> 'supervisor_accepted' OR supervisor_reviewed_by_user_id IS NOT NULL
  )
);

COMMENT ON TABLE task_progress_updates IS 'Append-only structured progress submissions. Corrections supersede rather than overwrite, so the original field claim stays visible.';
COMMENT ON COLUMN task_progress_updates.export_state IS 'Only reviewed leaf-task percent complete, actual start, and actual finish may reach export. See the MVP export whitelist.';
COMMENT ON COLUMN task_progress_updates.idempotency_key IS 'Device-generated. Prevents a retried offline submission becoming a duplicate update.';

-- A retried submission from the field must not create a second row.
CREATE UNIQUE INDEX idx_task_progress_updates_idempotency
  ON task_progress_updates (project_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_task_progress_updates_task_submitted
  ON task_progress_updates (imported_task_id, submitted_at DESC);

-- Supports the supervisor and planner review queues.
CREATE INDEX idx_task_progress_updates_supervisor_queue
  ON task_progress_updates (project_id, progress_review_state, submitted_at);

CREATE INDEX idx_task_progress_updates_planner_queue
  ON task_progress_updates (project_id, planner_review_state, submitted_at)
  WHERE planner_review_state = 'needs_planner_review';

CREATE INDEX idx_task_progress_updates_export_candidates
  ON task_progress_updates (project_id, export_state)
  WHERE export_state IN ('eligible', 'approved_for_export');
