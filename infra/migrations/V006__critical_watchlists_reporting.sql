-- Critical Watchlist and Critical Work Package reporting foundation.
-- Critical WPs are reporting objects, not scheduling objects. They do not
-- calculate critical path or move Microsoft Project dates.

CREATE TABLE critical_watchlists (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  name TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_user_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT critical_watchlists_status_check CHECK (status IN ('active', 'archived')),
  CONSTRAINT critical_watchlists_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE critical_watchlists IS 'Named operational reporting lists for one shutdown, area, or purpose.';
COMMENT ON COLUMN critical_watchlists.updated_at IS 'Mutable metadata timestamp maintained by future application code.';

CREATE TABLE critical_work_packages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  critical_watchlist_id UUID NOT NULL REFERENCES critical_watchlists(id),
  name TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_user_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT critical_work_packages_status_check CHECK (status IN ('active', 'archived', 'superseded')),
  CONSTRAINT critical_work_packages_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE critical_work_packages IS 'Critical Work Packages are reporting objects, not scheduling objects.';
COMMENT ON COLUMN critical_work_packages.updated_at IS 'Mutable metadata timestamp maintained by future application code.';

CREATE TABLE critical_work_package_sources (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  critical_work_package_id UUID NOT NULL REFERENCES critical_work_packages(id),
  project_snapshot_id UUID NOT NULL REFERENCES project_snapshots(id),
  source_type TEXT NOT NULL,
  imported_task_id UUID NOT NULL REFERENCES imported_tasks(id),
  include_descendants BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_user_id UUID,
  reason TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT critical_work_package_sources_source_type_check CHECK (source_type IN ('summary_task', 'multi_summary')),
  CONSTRAINT critical_work_package_sources_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE critical_work_package_sources IS 'Sources for Critical WPs. Manual arbitrary leaf-task grouping is deferred.';
COMMENT ON COLUMN critical_work_package_sources.source_type IS 'Use summary_task for one summary task or multi_summary when one reporting group spans schedule boundaries.';
COMMENT ON COLUMN critical_work_package_sources.include_descendants IS 'Summary-task source includes descendants for reporting. This is not schedule recalculation.';

CREATE TABLE reporting_policy_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  critical_work_package_id UUID NOT NULL REFERENCES critical_work_packages(id),
  policy_type reporting_policy_type NOT NULL,
  effective_from TIMESTAMPTZ NOT NULL,
  effective_to TIMESTAMPTZ,
  cadence_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  required_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_user_id UUID,
  reason TEXT,
  CONSTRAINT reporting_policy_versions_effective_range_check CHECK (
    effective_to IS NULL OR effective_to > effective_from
  ),
  CONSTRAINT reporting_policy_versions_cadence_config_object_check CHECK (jsonb_typeof(cadence_config) = 'object'),
  CONSTRAINT reporting_policy_versions_required_fields_object_check CHECK (jsonb_typeof(required_fields) = 'object')
);

COMMENT ON TABLE reporting_policy_versions IS 'Versioned reporting policies. Four-hour reporting is a configurable template, not hardcoded behavior.';
COMMENT ON COLUMN reporting_policy_versions.cadence_config IS 'Flexible cadence settings for fixed interval, fixed time, shift, event, or custom reporting.';

CREATE TABLE reporting_periods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  critical_work_package_id UUID NOT NULL REFERENCES critical_work_packages(id),
  reporting_policy_version_id UUID NOT NULL REFERENCES reporting_policy_versions(id),
  due_at TIMESTAMPTZ,
  period_start TIMESTAMPTZ,
  period_end TIMESTAMPTZ,
  grace_until TIMESTAMPTZ,
  sync_state sync_state,
  status TEXT NOT NULL DEFAULT 'open',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT reporting_periods_status_check CHECK (status IN ('open', 'submitted', 'missed', 'closed', 'superseded')),
  CONSTRAINT reporting_periods_period_range_check CHECK (
    period_start IS NULL OR period_end IS NULL OR period_end >= period_start
  ),
  CONSTRAINT reporting_periods_grace_after_due_check CHECK (
    due_at IS NULL OR grace_until IS NULL OR grace_until >= due_at
  ),
  CONSTRAINT reporting_periods_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE reporting_periods IS 'Generated reporting periods for Critical Work Package reporting policies.';
COMMENT ON COLUMN reporting_periods.sync_state IS 'Offline/mobile sync state where a reporting period is affected by queued submissions.';

CREATE TABLE critical_updates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  critical_work_package_id UUID NOT NULL REFERENCES critical_work_packages(id),
  reporting_period_id UUID REFERENCES reporting_periods(id),
  status critical_update_status NOT NULL,
  update_mode TEXT NOT NULL DEFAULT 'ad_hoc',
  submitted_at TIMESTAMPTZ,
  submitted_by_user_id UUID,
  due_at TIMESTAMPTZ,
  offline_local_id TEXT,
  idempotency_key TEXT,
  supersedes_critical_update_id UUID REFERENCES critical_updates(id),
  review_state TEXT,
  current_focus TEXT,
  current_blocker_summary TEXT,
  next_target TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT critical_updates_update_mode_check CHECK (update_mode IN ('ad_hoc', 'scheduled', 'shift', 'event', 'custom')),
  CONSTRAINT critical_updates_review_state_check CHECK (
    review_state IS NULL OR review_state IN ('pending', 'reviewed', 'correction_requested', 'rejected', 'superseded')
  ),
  CONSTRAINT critical_updates_not_self_superseded_check CHECK (
    supersedes_critical_update_id IS NULL OR supersedes_critical_update_id <> id
  ),
  CONSTRAINT critical_updates_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE critical_updates IS 'Immutable submitted Critical Work Package reports, corrected through superseding records.';
COMMENT ON COLUMN critical_updates.offline_local_id IS 'Client local ID for offline Critical Update queue correlation.';
COMMENT ON COLUMN critical_updates.idempotency_key IS 'Replay-safe key for queued or retried Critical Update submissions.';
COMMENT ON COLUMN critical_updates.current_blocker_summary IS 'Reporting summary only. This does not calculate critical path or move schedule dates.';

CREATE TABLE critical_update_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  critical_update_id UUID NOT NULL REFERENCES critical_updates(id),
  imported_task_id UUID REFERENCES imported_tasks(id),
  target_text TEXT,
  actual_text TEXT,
  delay_or_issue_text TEXT,
  solution_or_next_action_text TEXT,
  percent_complete NUMERIC(5,2),
  physical_percent_complete NUMERIC(5,2),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT critical_update_lines_percent_complete_check CHECK (
    percent_complete IS NULL OR (percent_complete >= 0 AND percent_complete <= 100)
  ),
  CONSTRAINT critical_update_lines_physical_percent_complete_check CHECK (
    physical_percent_complete IS NULL OR (physical_percent_complete >= 0 AND physical_percent_complete <= 100)
  ),
  CONSTRAINT critical_update_lines_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE critical_update_lines IS 'Optional line-level detail for Critical Updates. These lines do not directly update Microsoft Project.';
COMMENT ON COLUMN critical_update_lines.imported_task_id IS 'Optional imported task reference for reporting context only.';

CREATE INDEX idx_critical_watchlists_project ON critical_watchlists(project_id, status);
CREATE INDEX idx_critical_work_packages_watchlist ON critical_work_packages(critical_watchlist_id, status);
CREATE INDEX idx_critical_work_package_sources_cwp ON critical_work_package_sources(critical_work_package_id);
CREATE INDEX idx_critical_work_package_sources_imported_task ON critical_work_package_sources(imported_task_id);
CREATE INDEX idx_reporting_policy_versions_cwp_effective ON reporting_policy_versions(critical_work_package_id, effective_from DESC, effective_to);
CREATE INDEX idx_reporting_periods_due_status ON reporting_periods(project_id, due_at, status);
CREATE INDEX idx_reporting_periods_cwp_status ON reporting_periods(critical_work_package_id, status);
CREATE INDEX idx_critical_updates_cwp_submitted ON critical_updates(critical_work_package_id, submitted_at DESC);
CREATE INDEX idx_critical_updates_reporting_period ON critical_updates(reporting_period_id);
CREATE INDEX idx_critical_updates_idempotency_key ON critical_updates(idempotency_key);
CREATE INDEX idx_critical_updates_offline_local_id ON critical_updates(offline_local_id);
CREATE INDEX idx_critical_update_lines_update ON critical_update_lines(critical_update_id);
CREATE INDEX idx_critical_update_lines_imported_task ON critical_update_lines(imported_task_id);
