-- Shutdown Tracker baseline database support.
-- These enum types are intentionally conservative and may be revisited before
-- production if lookup tables become a better fit.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE import_batch_status AS ENUM (
  'pending',
  'parsing',
  'parsed',
  'accepted',
  'failed',
  'superseded'
);

CREATE TYPE project_snapshot_status AS ENUM (
  'parsed',
  'accepted',
  'rejected',
  'superseded',
  'failed'
);

CREATE TYPE task_execution_state AS ENUM (
  'not_started',
  'in_progress',
  'paused',
  'blocked',
  'completed',
  'awaiting_review',
  'approved',
  'rejected',
  'superseded'
);

CREATE TYPE problem_status AS ENUM (
  'open',
  'assigned',
  'escalated',
  'closed',
  'reopened',
  'superseded'
);

CREATE TYPE action_status AS ENUM (
  'open',
  'assigned',
  'in_progress',
  'completed',
  'verified',
  'closed',
  'reopened',
  'superseded'
);

CREATE TYPE evidence_status AS ENUM (
  'pending_upload',
  'uploaded',
  'linked',
  'unlinked',
  'superseded',
  'failed'
);

CREATE TYPE approval_state AS ENUM (
  'draft',
  'submitted',
  'awaiting_review',
  'correction_requested',
  'approved_for_export',
  'rejected',
  'superseded',
  'exported'
);

CREATE TYPE export_batch_state AS ENUM (
  'draft_preview',
  'awaiting_approval',
  'approved',
  'rejected',
  'generated',
  'opened_in_microsoft_project',
  'verified',
  'superseded',
  'failed'
);

CREATE TYPE audit_actor_type AS ENUM (
  'user',
  'service',
  'system',
  'integration'
);

CREATE TYPE audit_event_category AS ENUM (
  'auth',
  'permission',
  'project',
  'import',
  'reimport',
  'task',
  'problem',
  'action',
  'evidence',
  'critical_watchlist',
  'critical_update',
  'handover',
  'approval',
  'export',
  'offline_sync',
  'system'
);

CREATE TYPE critical_update_status AS ENUM (
  'draft',
  'submitted',
  'correction_requested',
  'reviewed',
  'rejected',
  'superseded'
);

CREATE TYPE reporting_policy_type AS ENUM (
  'none',
  'ad_hoc',
  'fixed_interval',
  'fixed_times',
  'shift_based',
  'event_triggered',
  'custom'
);

CREATE TYPE sync_state AS ENUM (
  'queued',
  'syncing',
  'synced',
  'failed',
  'conflict'
);

COMMENT ON EXTENSION pgcrypto IS 'Provides gen_random_uuid() for UUID primary keys.';
COMMENT ON TYPE import_batch_status IS 'Conservative status values for Microsoft Project import batches.';
COMMENT ON TYPE project_snapshot_status IS 'Status values for immutable imported project snapshots.';
COMMENT ON TYPE task_execution_state IS 'Live execution state values; imported schedule rows remain snapshot records.';
COMMENT ON TYPE approval_state IS 'Review state for operational records that may become export candidates.';
COMMENT ON TYPE export_batch_state IS 'Controlled, reviewed, batch-oriented Microsoft Project export states.';
COMMENT ON TYPE audit_event_category IS 'Audit event categories aligned to docs/architecture/audit-event-schema.md.';
COMMENT ON TYPE reporting_policy_type IS 'Configurable reporting policy types. Four-hour reporting is a template, not a hardcoded rule.';
