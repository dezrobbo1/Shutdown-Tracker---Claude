-- Approval records and controlled Microsoft Project export batches.
-- Export remains reviewed, approved, and batch-oriented. Export format is
-- MSPDI/XML. Native MPP writing is out of scope.

CREATE TABLE approval_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  source_entity_type TEXT NOT NULL,
  source_entity_id UUID NOT NULL,
  approval_state approval_state NOT NULL,
  requested_by_user_id UUID,
  requested_at TIMESTAMPTZ,
  reviewed_by_user_id UUID,
  reviewed_at TIMESTAMPTZ,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT approval_records_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT approval_records_reviewed_after_requested_check CHECK (
    requested_at IS NULL OR reviewed_at IS NULL OR reviewed_at >= requested_at
  )
);

COMMENT ON TABLE approval_records IS 'Review records for operational data that may become export candidates.';
COMMENT ON COLUMN approval_records.source_entity_type IS 'Source entity such as task_update, completion_review, or correction.';
COMMENT ON COLUMN approval_records.approval_state IS 'Approval workflow state. Export batch approval remains separate and Planner-owned by default.';

CREATE TABLE export_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  status export_batch_state NOT NULL,
  preview_created_at TIMESTAMPTZ,
  approved_at TIMESTAMPTZ,
  approved_by_user_id UUID,
  generated_at TIMESTAMPTZ,
  generated_by_user_id UUID,
  verified_at TIMESTAMPTZ,
  verified_by_user_id UUID,
  export_file_uri TEXT,
  export_file_hash TEXT,
  failure_reason TEXT,
  superseded_by_export_batch_id UUID REFERENCES export_batches(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT export_batches_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT export_batches_approved_after_preview_check CHECK (
    preview_created_at IS NULL OR approved_at IS NULL OR approved_at >= preview_created_at
  ),
  CONSTRAINT export_batches_generated_after_approved_check CHECK (
    approved_at IS NULL OR generated_at IS NULL OR generated_at >= approved_at
  ),
  CONSTRAINT export_batches_verified_after_generated_check CHECK (
    generated_at IS NULL OR verified_at IS NULL OR verified_at >= generated_at
  ),
  CONSTRAINT export_batches_not_self_superseded_check CHECK (
    superseded_by_export_batch_id IS NULL OR superseded_by_export_batch_id <> id
  )
);

COMMENT ON TABLE export_batches IS 'Controlled Microsoft Project export batches. Generated batches are immutable by application rule.';
COMMENT ON COLUMN export_batches.export_file_uri IS 'Object-storage URI for generated MSPDI/XML export artifacts.';
COMMENT ON COLUMN export_batches.verified_at IS 'Manual verification timestamp after reopening generated artifact in Microsoft Project.';

ALTER TABLE audit_events
  ADD CONSTRAINT audit_events_export_batch_id_fkey
  FOREIGN KEY (export_batch_id) REFERENCES export_batches(id);

CREATE TABLE export_batch_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  export_batch_id UUID NOT NULL REFERENCES export_batches(id),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  imported_task_id UUID NOT NULL REFERENCES imported_tasks(id),
  source_entity_type TEXT NOT NULL,
  source_entity_id UUID NOT NULL,
  field_name TEXT NOT NULL,
  old_value TEXT,
  new_value TEXT,
  source_actor_user_id UUID,
  source_timestamp TIMESTAMPTZ,
  reason TEXT,
  is_leaf_task BOOLEAN NOT NULL,
  is_export_eligible BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT export_batch_lines_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT export_batch_lines_leaf_export_eligible_check CHECK (
    is_export_eligible = false OR is_leaf_task = true
  )
);

COMMENT ON TABLE export_batch_lines IS 'Candidate and approved line-level values for controlled Microsoft Project export batches.';
COMMENT ON COLUMN export_batch_lines.field_name IS 'Export candidate field. Only approved leaf-task progress/actual fields may be eligible.';
COMMENT ON COLUMN export_batch_lines.is_leaf_task IS 'Snapshot-time leaf-task indicator used to prevent summary task actuals from being export eligible.';
COMMENT ON COLUMN export_batch_lines.is_export_eligible IS 'Application-reviewed export eligibility. Summary task actuals must not be eligible.';

CREATE INDEX idx_approval_records_project_state ON approval_records(project_id, approval_state);
CREATE INDEX idx_approval_records_source ON approval_records(source_entity_type, source_entity_id);
CREATE INDEX idx_approval_records_requested_at ON approval_records(project_id, requested_at DESC);

CREATE INDEX idx_export_batches_project_status ON export_batches(project_id, status);
CREATE INDEX idx_export_batches_project_snapshot ON export_batches(project_id, project_snapshot_id);
CREATE INDEX idx_export_batches_created_at ON export_batches(project_id, created_at DESC);
CREATE INDEX idx_export_batches_superseded_by ON export_batches(superseded_by_export_batch_id);

CREATE INDEX idx_export_batch_lines_batch ON export_batch_lines(export_batch_id);
CREATE INDEX idx_export_batch_lines_project_snapshot ON export_batch_lines(project_id, project_snapshot_id);
CREATE INDEX idx_export_batch_lines_imported_task ON export_batch_lines(imported_task_id);
CREATE INDEX idx_export_batch_lines_source ON export_batch_lines(source_entity_type, source_entity_id);
CREATE INDEX idx_export_batch_lines_eligibility ON export_batch_lines(export_batch_id, is_export_eligible);
