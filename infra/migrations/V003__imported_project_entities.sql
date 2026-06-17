-- Imported Microsoft Project entities.
-- These rows are immutable snapshot records and must not be updated as live
-- execution truth. Shutdown Tracker does not own schedule recalculation.

CREATE TABLE imported_tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  external_uid TEXT,
  external_id TEXT,
  name TEXT NOT NULL,
  wbs TEXT,
  outline_number TEXT,
  outline_level INTEGER,
  is_summary BOOLEAN NOT NULL DEFAULT false,
  parent_external_uid TEXT,
  parent_imported_task_id UUID REFERENCES imported_tasks(id),
  planned_start TIMESTAMPTZ,
  planned_finish TIMESTAMPTZ,
  actual_start TIMESTAMPTZ,
  actual_finish TIMESTAMPTZ,
  percent_complete NUMERIC(5,2),
  physical_percent_complete NUMERIC(5,2),
  notes TEXT,
  raw_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT imported_tasks_outline_level_check CHECK (outline_level IS NULL OR outline_level >= 0),
  CONSTRAINT imported_tasks_percent_complete_check CHECK (
    percent_complete IS NULL OR (percent_complete >= 0 AND percent_complete <= 100)
  ),
  CONSTRAINT imported_tasks_physical_percent_complete_check CHECK (
    physical_percent_complete IS NULL OR (physical_percent_complete >= 0 AND physical_percent_complete <= 100)
  ),
  CONSTRAINT imported_tasks_planned_dates_check CHECK (
    planned_start IS NULL OR planned_finish IS NULL OR planned_finish >= planned_start
  ),
  CONSTRAINT imported_tasks_actual_dates_check CHECK (
    actual_start IS NULL OR actual_finish IS NULL OR actual_finish >= actual_start
  ),
  CONSTRAINT imported_tasks_raw_data_object_check CHECK (jsonb_typeof(raw_data) = 'object')
);

COMMENT ON TABLE imported_tasks IS 'Imported Microsoft Project task rows for one immutable project snapshot.';
COMMENT ON COLUMN imported_tasks.is_summary IS 'Imported summary-task flag. Summary task actuals must not be export eligible.';
COMMENT ON COLUMN imported_tasks.planned_start IS 'Imported schedule value. Shutdown Tracker must not move dates automatically.';
COMMENT ON COLUMN imported_tasks.raw_data IS 'Flexible parser payload for values not yet modeled as first-class columns.';

CREATE TABLE imported_resources (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  external_uid TEXT,
  name TEXT,
  resource_type TEXT,
  raw_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT imported_resources_raw_data_object_check CHECK (jsonb_typeof(raw_data) = 'object')
);

COMMENT ON TABLE imported_resources IS 'Imported Microsoft Project resource rows for one immutable project snapshot.';
COMMENT ON COLUMN imported_resources.resource_type IS 'Imported resource type only. This is not a resource-levelling model.';

CREATE TABLE imported_assignments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  external_uid TEXT,
  task_external_uid TEXT,
  resource_external_uid TEXT,
  imported_task_id UUID REFERENCES imported_tasks(id),
  imported_resource_id UUID REFERENCES imported_resources(id),
  raw_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT imported_assignments_raw_data_object_check CHECK (jsonb_typeof(raw_data) = 'object')
);

COMMENT ON TABLE imported_assignments IS 'Imported Microsoft Project assignment rows for one immutable project snapshot.';

CREATE TABLE imported_extended_attributes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  entity_type TEXT NOT NULL,
  entity_external_uid TEXT,
  field_id TEXT,
  field_name TEXT,
  alias TEXT,
  value TEXT,
  raw_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT imported_extended_attributes_entity_type_check CHECK (
    entity_type IN ('project', 'task', 'resource', 'assignment', 'calendar', 'other')
  ),
  CONSTRAINT imported_extended_attributes_raw_data_object_check CHECK (jsonb_typeof(raw_data) = 'object')
);

COMMENT ON TABLE imported_extended_attributes IS 'Imported custom or extended attributes from Microsoft Project snapshots.';

CREATE TABLE task_lineage_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  previous_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  current_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  previous_imported_task_id UUID REFERENCES imported_tasks(id),
  current_imported_task_id UUID REFERENCES imported_tasks(id),
  match_method TEXT NOT NULL,
  match_confidence NUMERIC(5,2),
  review_state TEXT NOT NULL DEFAULT 'suggested',
  reviewed_by_user_id UUID,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT task_lineage_links_match_confidence_check CHECK (
    match_confidence IS NULL OR (match_confidence >= 0 AND match_confidence <= 100)
  ),
  CONSTRAINT task_lineage_links_review_state_check CHECK (review_state IN ('suggested', 'accepted', 'rejected', 'superseded')),
  CONSTRAINT task_lineage_links_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT task_lineage_links_distinct_snapshots_check CHECK (previous_snapshot_id <> current_snapshot_id)
);

COMMENT ON TABLE task_lineage_links IS 'Reviewable task matching between imported snapshots. This does not calculate schedule dependencies or critical path.';
COMMENT ON COLUMN task_lineage_links.match_method IS 'Matching method such as external_uid, wbs, outline_number, name, or manual_review.';

CREATE INDEX idx_imported_tasks_project_snapshot ON imported_tasks(project_id, project_snapshot_id);
CREATE INDEX idx_imported_tasks_external_uid ON imported_tasks(project_snapshot_id, external_uid);
CREATE INDEX idx_imported_tasks_wbs ON imported_tasks(project_snapshot_id, wbs);
CREATE INDEX idx_imported_tasks_outline_number ON imported_tasks(project_snapshot_id, outline_number);
CREATE INDEX idx_imported_tasks_parent ON imported_tasks(parent_imported_task_id);
CREATE INDEX idx_imported_tasks_summary ON imported_tasks(project_snapshot_id, is_summary);

CREATE INDEX idx_imported_resources_project_snapshot ON imported_resources(project_id, project_snapshot_id);
CREATE INDEX idx_imported_resources_external_uid ON imported_resources(project_snapshot_id, external_uid);

CREATE INDEX idx_imported_assignments_project_snapshot ON imported_assignments(project_id, project_snapshot_id);
CREATE INDEX idx_imported_assignments_task_external_uid ON imported_assignments(project_snapshot_id, task_external_uid);
CREATE INDEX idx_imported_assignments_resource_external_uid ON imported_assignments(project_snapshot_id, resource_external_uid);
CREATE INDEX idx_imported_assignments_task ON imported_assignments(imported_task_id);
CREATE INDEX idx_imported_assignments_resource ON imported_assignments(imported_resource_id);

CREATE INDEX idx_imported_extended_attributes_project_snapshot ON imported_extended_attributes(project_id, project_snapshot_id);
CREATE INDEX idx_imported_extended_attributes_entity ON imported_extended_attributes(project_snapshot_id, entity_type, entity_external_uid);

CREATE INDEX idx_task_lineage_links_snapshot_pair ON task_lineage_links(previous_snapshot_id, current_snapshot_id);
CREATE INDEX idx_task_lineage_links_previous_task ON task_lineage_links(previous_imported_task_id);
CREATE INDEX idx_task_lineage_links_current_task ON task_lineage_links(current_imported_task_id);
CREATE INDEX idx_task_lineage_links_review_state ON task_lineage_links(project_id, review_state);
