-- Problems, actions, evidence, and handover.
--
-- V001 declared problem_status, action_status, and evidence_status and then created no
-- table that used them. These are the operational records the field, supervisors, and
-- the control room work with day to day, and the ones progress review leans on when a
-- completion claim needs proof or a blocker needs following up.
--
-- None of these are schedule objects. They attach to imported tasks for context and never
-- alter imported Project values.

CREATE TYPE problem_severity AS ENUM (
  'low',
  'medium',
  'high',
  'critical'
);

CREATE TABLE problems (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  imported_task_id UUID REFERENCES imported_tasks(id),
  title TEXT NOT NULL,
  description TEXT,
  status problem_status NOT NULL DEFAULT 'open',
  severity problem_severity NOT NULL DEFAULT 'medium',
  blocks_execution BOOLEAN NOT NULL DEFAULT false,
  raised_by_user_id UUID NOT NULL REFERENCES users(id),
  raised_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  assigned_to_user_id UUID REFERENCES users(id),
  resolved_at TIMESTAMPTZ,
  resolved_by_user_id UUID REFERENCES users(id),
  resolution_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT problems_title_not_blank_check CHECK (length(btrim(title)) > 0),
  CONSTRAINT problems_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  -- A closed problem has to say who closed it and when.
  CONSTRAINT problems_resolution_attribution_check CHECK (
    status <> 'closed' OR (resolved_at IS NOT NULL AND resolved_by_user_id IS NOT NULL)
  )
);

COMMENT ON TABLE problems IS 'Structured operational issues. A problem may block execution but never alters the imported schedule.';
COMMENT ON COLUMN problems.blocks_execution IS 'Whether this problem is why work cannot continue. Drives blocked-task reporting, not schedule logic.';

CREATE INDEX idx_problems_project_status ON problems (project_id, status);
CREATE INDEX idx_problems_task ON problems (imported_task_id) WHERE imported_task_id IS NOT NULL;
CREATE INDEX idx_problems_assignee ON problems (assigned_to_user_id) WHERE assigned_to_user_id IS NOT NULL;

CREATE TABLE actions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  problem_id UUID REFERENCES problems(id),
  imported_task_id UUID REFERENCES imported_tasks(id),
  title TEXT NOT NULL,
  description TEXT,
  status action_status NOT NULL DEFAULT 'open',
  assigned_to_user_id UUID REFERENCES users(id),
  due_at TIMESTAMPTZ,
  created_by_user_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  completed_by_user_id UUID REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT actions_title_not_blank_check CHECK (length(btrim(title)) > 0),
  CONSTRAINT actions_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT actions_completion_attribution_check CHECK (
    status NOT IN ('completed', 'verified', 'closed')
      OR (completed_at IS NOT NULL AND completed_by_user_id IS NOT NULL)
  )
);

COMMENT ON TABLE actions IS 'Follow-up work tracked against a problem or task. Not a schedule activity.';

CREATE INDEX idx_actions_project_status ON actions (project_id, status);
CREATE INDEX idx_actions_problem ON actions (problem_id) WHERE problem_id IS NOT NULL;
CREATE INDEX idx_actions_assignee_due ON actions (assigned_to_user_id, due_at) WHERE assigned_to_user_id IS NOT NULL;

CREATE TABLE evidence (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  imported_task_id UUID REFERENCES imported_tasks(id),
  problem_id UUID REFERENCES problems(id),
  action_id UUID REFERENCES actions(id),
  task_progress_update_id UUID REFERENCES task_progress_updates(id),
  original_filename TEXT NOT NULL,
  content_type TEXT,
  storage_uri TEXT,
  content_hash TEXT,
  size_bytes BIGINT,
  status evidence_status NOT NULL DEFAULT 'pending_upload',
  captured_by_user_id UUID NOT NULL REFERENCES users(id),
  captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  caption TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT evidence_original_filename_not_blank_check CHECK (length(btrim(original_filename)) > 0),
  CONSTRAINT evidence_size_bytes_check CHECK (size_bytes IS NULL OR size_bytes >= 0),
  CONSTRAINT evidence_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  -- Evidence has to be evidence *of* something.
  CONSTRAINT evidence_subject_present_check CHECK (
    imported_task_id IS NOT NULL
      OR problem_id IS NOT NULL
      OR action_id IS NOT NULL
      OR task_progress_update_id IS NOT NULL
  ),
  -- Once uploaded there must be somewhere to fetch it from.
  CONSTRAINT evidence_uploaded_has_location_check CHECK (
    status = 'pending_upload' OR status = 'failed' OR storage_uri IS NOT NULL
  )
);

COMMENT ON TABLE evidence IS 'Evidence metadata only. Binary content lives in object storage; storage_uri points at it.';
COMMENT ON COLUMN evidence.storage_uri IS 'Object-storage URI. Do not store uploaded file contents in PostgreSQL.';

CREATE INDEX idx_evidence_project_status ON evidence (project_id, status);
CREATE INDEX idx_evidence_task ON evidence (imported_task_id) WHERE imported_task_id IS NOT NULL;
CREATE INDEX idx_evidence_progress_update ON evidence (task_progress_update_id)
  WHERE task_progress_update_id IS NOT NULL;

CREATE TABLE handover_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  imported_task_id UUID REFERENCES imported_tasks(id),
  problem_id UUID REFERENCES problems(id),
  shift_label TEXT NOT NULL,
  note TEXT NOT NULL,
  requires_acknowledgement BOOLEAN NOT NULL DEFAULT false,
  created_by_user_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  acknowledged_by_user_id UUID REFERENCES users(id),
  acknowledged_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT handover_notes_note_not_blank_check CHECK (length(btrim(note)) > 0),
  CONSTRAINT handover_notes_shift_label_not_blank_check CHECK (length(btrim(shift_label)) > 0),
  CONSTRAINT handover_notes_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT handover_notes_acknowledgement_pairing_check CHECK (
    (acknowledged_by_user_id IS NULL) = (acknowledged_at IS NULL)
  )
);

COMMENT ON TABLE handover_notes IS 'What the incoming shift must know. Structured handover, not chat.';

CREATE INDEX idx_handover_notes_project_shift ON handover_notes (project_id, shift_label, created_at DESC);
CREATE INDEX idx_handover_notes_unacknowledged
  ON handover_notes (project_id, created_at)
  WHERE requires_acknowledgement AND acknowledged_at IS NULL;
