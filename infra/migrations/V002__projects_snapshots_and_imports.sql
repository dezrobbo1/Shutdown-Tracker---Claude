-- Core project, source-file, import-batch, and immutable project snapshot tables.
-- Microsoft Project remains the schedule authority. Shutdown Tracker stores
-- imported snapshots and live execution/reporting records separately.

CREATE TABLE projects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'active',
  timezone TEXT NOT NULL DEFAULT 'UTC',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_user_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT projects_status_check CHECK (status IN ('draft', 'active', 'archived')),
  CONSTRAINT projects_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE projects IS 'Project-level scope for shutdown, turnaround, or construction execution tracking.';
COMMENT ON COLUMN projects.timezone IS 'Project timezone for reporting and operational date presentation.';
COMMENT ON COLUMN projects.updated_at IS 'Mutable metadata timestamp maintained by future application code.';

CREATE TABLE source_files (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  original_filename TEXT NOT NULL,
  file_kind TEXT NOT NULL,
  storage_uri TEXT NOT NULL,
  content_hash TEXT,
  size_bytes BIGINT,
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  uploaded_by_user_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT source_files_file_kind_check CHECK (file_kind IN ('mpp', 'mspdi_xml', 'xml', 'other')),
  CONSTRAINT source_files_size_bytes_check CHECK (size_bytes IS NULL OR size_bytes >= 0),
  CONSTRAINT source_files_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE source_files IS 'Immutable source file metadata for uploaded Microsoft Project files or import/export artifacts.';
COMMENT ON COLUMN source_files.storage_uri IS 'Object-storage URI. Do not store uploaded binary file contents in PostgreSQL.';
COMMENT ON COLUMN source_files.content_hash IS 'Optional content hash for duplicate detection and traceability.';

CREATE TABLE import_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  source_file_id UUID NOT NULL REFERENCES source_files(id),
  status import_batch_status NOT NULL,
  parser_name TEXT,
  parser_version TEXT,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  warning_count INTEGER NOT NULL DEFAULT 0,
  error_count INTEGER NOT NULL DEFAULT 0,
  parse_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_user_id UUID,
  CONSTRAINT import_batches_warning_count_check CHECK (warning_count >= 0),
  CONSTRAINT import_batches_error_count_check CHECK (error_count >= 0),
  CONSTRAINT import_batches_parse_summary_object_check CHECK (jsonb_typeof(parse_summary) = 'object'),
  CONSTRAINT import_batches_completed_after_started_check CHECK (
    started_at IS NULL OR completed_at IS NULL OR completed_at >= started_at
  )
);

COMMENT ON TABLE import_batches IS 'One parser run against one immutable source file.';
COMMENT ON COLUMN import_batches.parse_summary IS 'Flexible parser summary, warnings, and non-domain parse metadata.';

CREATE TABLE project_snapshots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  import_batch_id UUID NOT NULL REFERENCES import_batches(id),
  status project_snapshot_status NOT NULL,
  external_project_uid TEXT,
  external_project_name TEXT,
  project_status_date TIMESTAMPTZ,
  snapshot_version INTEGER NOT NULL,
  accepted_at TIMESTAMPTZ,
  accepted_by_user_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT project_snapshots_snapshot_version_check CHECK (snapshot_version > 0),
  CONSTRAINT project_snapshots_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT project_snapshots_project_version_unique UNIQUE (project_id, snapshot_version),
  CONSTRAINT project_snapshots_import_batch_unique UNIQUE (import_batch_id)
);

COMMENT ON TABLE project_snapshots IS 'Immutable imported Microsoft Project schedule snapshot. Each import creates a new snapshot.';
COMMENT ON COLUMN project_snapshots.project_status_date IS 'Imported Microsoft Project status date, stored as snapshot metadata and not recalculated by Shutdown Tracker.';
COMMENT ON COLUMN project_snapshots.snapshot_version IS 'Monotonic version within a project. Imported schedule data is not mutated in place.';

CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_source_files_project_uploaded ON source_files(project_id, uploaded_at DESC);
CREATE INDEX idx_source_files_project_hash ON source_files(project_id, content_hash);
CREATE INDEX idx_import_batches_project_created ON import_batches(project_id, created_at DESC);
CREATE INDEX idx_import_batches_source_file ON import_batches(source_file_id);
CREATE INDEX idx_import_batches_status ON import_batches(status);
CREATE INDEX idx_project_snapshots_project_created ON project_snapshots(project_id, created_at DESC);
CREATE INDEX idx_project_snapshots_project_status ON project_snapshots(project_id, status);
CREATE INDEX idx_project_snapshots_external_uid ON project_snapshots(project_id, external_project_uid);
