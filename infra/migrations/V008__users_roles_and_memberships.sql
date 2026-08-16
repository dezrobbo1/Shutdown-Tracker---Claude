-- Users, project roles, and membership.
--
-- Every *_by_user_id column already in the schema was an unvalidated UUID with no
-- table behind it, so an audit trail could name a user that never existed. These
-- tables give those columns something real to reference.
--
-- Authentication identifies the user. Role plus explicit project membership decides
-- what they may do. Project-derived Operational Category membership never grants
-- application authority by itself; see docs/product/roles-and-capabilities.md.

CREATE TYPE project_role AS ENUM (
  'admin',
  'planner',
  'shutdown_control',
  'coordinator',
  'supervisor',
  'field_user',
  'contractor',
  'inspector',
  'viewer'
);

CREATE TYPE user_status AS ENUM (
  'invited',
  'active',
  'suspended',
  'deactivated'
);

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  display_name TEXT NOT NULL,
  status user_status NOT NULL DEFAULT 'invited',
  external_subject TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT users_email_not_blank_check CHECK (length(btrim(email)) > 0),
  CONSTRAINT users_display_name_not_blank_check CHECK (length(btrim(display_name)) > 0),
  CONSTRAINT users_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

-- Case-insensitive uniqueness: an identity provider that returns a differently-cased
-- address must not be able to create a second account for the same person.
CREATE UNIQUE INDEX idx_users_email_unique ON users (lower(email));

-- The subject claim from the identity provider, when one is configured. Unique so two
-- accounts cannot claim the same external identity.
CREATE UNIQUE INDEX idx_users_external_subject_unique
  ON users (external_subject)
  WHERE external_subject IS NOT NULL;

COMMENT ON TABLE users IS 'People who can sign in. Authentication is external; this table records identity and status.';
COMMENT ON COLUMN users.external_subject IS 'Stable subject identifier from the identity provider.';
COMMENT ON COLUMN users.status IS 'Only active users may act. Suspended and deactivated users fail authorisation.';

CREATE TABLE project_memberships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  user_id UUID NOT NULL REFERENCES users(id),
  role project_role NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  granted_by_user_id UUID REFERENCES users(id),
  revoked_at TIMESTAMPTZ,
  revoked_by_user_id UUID REFERENCES users(id),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT project_memberships_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT project_memberships_revoked_consistency_check CHECK (
    (active AND revoked_at IS NULL) OR (NOT active AND revoked_at IS NOT NULL)
  )
);

-- One active role per user per project. A user needing two roles is a policy question,
-- not something to allow by accident.
CREATE UNIQUE INDEX idx_project_memberships_active_unique
  ON project_memberships (project_id, user_id)
  WHERE active;

CREATE INDEX idx_project_memberships_project_role ON project_memberships (project_id, role) WHERE active;
CREATE INDEX idx_project_memberships_user ON project_memberships (user_id) WHERE active;

COMMENT ON TABLE project_memberships IS 'Project-scoped role grants. Roles are per project, not global.';
COMMENT ON COLUMN project_memberships.role IS 'Default role. Explicit responsibility and delegation refine it further.';

-- Point the existing attribution columns at real users. These were previously free
-- UUIDs, so an audit event could name a user that did not exist.
ALTER TABLE projects
  ADD CONSTRAINT projects_created_by_user_fk
  FOREIGN KEY (created_by_user_id) REFERENCES users(id);

ALTER TABLE source_files
  ADD CONSTRAINT source_files_uploaded_by_user_fk
  FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id);

ALTER TABLE import_batches
  ADD CONSTRAINT import_batches_created_by_user_fk
  FOREIGN KEY (created_by_user_id) REFERENCES users(id);

ALTER TABLE project_snapshots
  ADD CONSTRAINT project_snapshots_accepted_by_user_fk
  FOREIGN KEY (accepted_by_user_id) REFERENCES users(id);

ALTER TABLE task_lineage_links
  ADD CONSTRAINT task_lineage_links_reviewed_by_user_fk
  FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id);

ALTER TABLE audit_events
  ADD CONSTRAINT audit_events_actor_user_fk
  FOREIGN KEY (actor_user_id) REFERENCES users(id);

ALTER TABLE approval_records
  ADD CONSTRAINT approval_records_requested_by_user_fk
  FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
  ADD CONSTRAINT approval_records_reviewed_by_user_fk
  FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id);

ALTER TABLE export_batches
  ADD CONSTRAINT export_batches_approved_by_user_fk
  FOREIGN KEY (approved_by_user_id) REFERENCES users(id),
  ADD CONSTRAINT export_batches_generated_by_user_fk
  FOREIGN KEY (generated_by_user_id) REFERENCES users(id),
  ADD CONSTRAINT export_batches_verified_by_user_fk
  FOREIGN KEY (verified_by_user_id) REFERENCES users(id);

ALTER TABLE export_batch_lines
  ADD CONSTRAINT export_batch_lines_source_actor_user_fk
  FOREIGN KEY (source_actor_user_id) REFERENCES users(id);

-- V007 landed the export-candidate model ahead of this migration and introduced two further
-- attribution columns. They are constrained here for the same reason as the ones above: an
-- audit trail must not be able to name a user that does not exist.
ALTER TABLE export_candidate_records
  ADD CONSTRAINT export_candidate_records_source_actor_user_fk
  FOREIGN KEY (source_actor_user_id) REFERENCES users(id);

ALTER TABLE export_batches
  ADD CONSTRAINT export_batches_opened_in_microsoft_project_by_user_fk
  FOREIGN KEY (opened_in_microsoft_project_by_user_id) REFERENCES users(id);
