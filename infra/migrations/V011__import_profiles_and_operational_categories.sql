-- Import Profiles and Operational Categories.
--
-- Microsoft Project supplies the source facts. A planner adds an operational
-- interpretation on top without rewriting them: naming a category such as "Work Group"
-- or "Area" and mapping it to a Project source. Category names are Tracker configuration;
-- source values stay exactly as imported.
--
-- Three source modes are supported, matching docs/concept/README.md:
--   1. a direct imported task field or aliased custom field;
--   2. task hierarchy, using a summary-task ancestor at a chosen outline level;
--   3. task assignments resolved through the assigned resource's Project Group field.
--
-- Resolved membership is stored with its provenance so the question "why is this task in
-- this category?" can always be answered, including after a later re-import changes the
-- current classification.
--
-- Classification is not authorisation. A category such as Work Group = CVM MECH may make
-- a task relevant to someone; it never grants them authority over it.

CREATE TYPE category_source_mode AS ENUM (
  'task_field',
  'hierarchy_ancestor',
  'resource_group'
);

CREATE TYPE mapping_health AS ENUM (
  'healthy',
  'healthy_with_new_values',
  'configuration_changed',
  'confirmation_required',
  'broken',
  'profile_mismatch'
);

CREATE TABLE import_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  name TEXT NOT NULL,
  version INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT false,
  description TEXT,
  created_by_user_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at TIMESTAMPTZ,
  activated_by_user_id UUID REFERENCES users(id),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT import_profiles_name_not_blank_check CHECK (length(btrim(name)) > 0),
  CONSTRAINT import_profiles_version_check CHECK (version > 0),
  CONSTRAINT import_profiles_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT import_profiles_name_version_unique UNIQUE (project_id, name, version)
);

-- Profiles are versioned so a planning convention can be reused and revised, but only one
-- version of a profile drives a project at a time.
CREATE UNIQUE INDEX idx_import_profiles_one_active_per_project
  ON import_profiles (project_id)
  WHERE active;

COMMENT ON TABLE import_profiles IS 'Versioned planner mapping configuration, reusable across projects sharing a Project template.';

CREATE TABLE operational_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  import_profile_id UUID NOT NULL REFERENCES import_profiles(id) ON DELETE CASCADE,
  project_id UUID NOT NULL REFERENCES projects(id),
  name TEXT NOT NULL,
  source_mode category_source_mode NOT NULL,

  -- What the mode reads. For task_field this is the field name or custom-field alias;
  -- for hierarchy_ancestor the target outline level; for resource_group nothing, because
  -- the Project Group field is the fixed source.
  source_field TEXT,
  source_outline_level INTEGER,

  -- Resource-derived categories must allow several values: a task can legitimately carry
  -- assignments from more than one Resource Group.
  multi_valued BOOLEAN NOT NULL DEFAULT false,
  required_for_execution BOOLEAN NOT NULL DEFAULT false,
  health mapping_health NOT NULL DEFAULT 'healthy',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  CONSTRAINT operational_categories_name_not_blank_check CHECK (length(btrim(name)) > 0),
  CONSTRAINT operational_categories_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT operational_categories_name_unique UNIQUE (import_profile_id, name),
  -- Each mode needs its own configuration and nothing else.
  CONSTRAINT operational_categories_source_configuration_check CHECK (
    (source_mode = 'task_field' AND source_field IS NOT NULL AND source_outline_level IS NULL)
    OR (source_mode = 'hierarchy_ancestor' AND source_outline_level IS NOT NULL AND source_outline_level >= 0)
    OR (source_mode = 'resource_group')
  )
);

COMMENT ON TABLE operational_categories IS 'A planner-named classification mapped to a Project source. The name is Tracker configuration; values stay as imported.';
COMMENT ON COLUMN operational_categories.health IS 'Re-import validation outcome. An uncertain source is never silently remapped.';
COMMENT ON COLUMN operational_categories.required_for_execution IS 'Drives execution-readiness checks for missing operational classification.';

CREATE INDEX idx_operational_categories_profile ON operational_categories (import_profile_id);

CREATE TABLE operational_category_aliases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  operational_category_id UUID NOT NULL REFERENCES operational_categories(id) ON DELETE CASCADE,
  source_value TEXT NOT NULL,
  display_alias TEXT,
  rollup_value TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT operational_category_aliases_source_value_not_blank_check CHECK (length(btrim(source_value)) > 0),
  CONSTRAINT operational_category_aliases_unique UNIQUE (operational_category_id, source_value)
);

COMMENT ON TABLE operational_category_aliases IS 'Friendly names and higher-level roll-ups. The original source value is never overwritten.';

CREATE TABLE task_category_values (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  operational_category_id UUID NOT NULL REFERENCES operational_categories(id) ON DELETE CASCADE,
  imported_task_id UUID NOT NULL REFERENCES imported_tasks(id),
  source_value TEXT NOT NULL,

  -- Provenance: enough to answer "why is this task in this category?" long after the
  -- classification itself has changed.
  resolved_via category_source_mode NOT NULL,
  resolved_from_reference TEXT,
  resolved_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT task_category_values_source_value_not_blank_check CHECK (length(btrim(source_value)) > 0),
  CONSTRAINT task_category_values_unique UNIQUE (operational_category_id, imported_task_id, source_value)
);

COMMENT ON TABLE task_category_values IS 'Resolved category membership per task per snapshot, retained with provenance.';
COMMENT ON COLUMN task_category_values.resolved_from_reference IS 'What the value came from: the field name, the ancestor task, or the resource.';

CREATE INDEX idx_task_category_values_category_value
  ON task_category_values (operational_category_id, source_value);
CREATE INDEX idx_task_category_values_task ON task_category_values (imported_task_id);
CREATE INDEX idx_task_category_values_snapshot ON task_category_values (project_snapshot_id);
