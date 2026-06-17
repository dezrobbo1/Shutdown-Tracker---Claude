-- Append-only audit event table by application rule.
-- No update/delete triggers are added yet; future application code must not
-- edit or delete audit rows in ordinary workflows.

CREATE TABLE audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID REFERENCES projects(id),
  event_category audit_event_category NOT NULL,
  event_type TEXT NOT NULL,
  event_version INTEGER NOT NULL DEFAULT 1,
  occurred_at TIMESTAMPTZ NOT NULL,
  server_received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id UUID,
  actor_display_name TEXT,
  actor_role TEXT,
  actor_scope TEXT,
  actor_type audit_actor_type NOT NULL,
  target_entity_type TEXT,
  target_entity_id UUID,
  target_display_name TEXT,
  old_value_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
  new_value_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
  reason TEXT,
  client_context JSONB NOT NULL DEFAULT '{}'::jsonb,
  source_system TEXT NOT NULL,
  correlation_id TEXT,
  request_id TEXT,
  idempotency_key TEXT,
  offline_local_id TEXT,
  project_snapshot_id UUID REFERENCES project_snapshots(id),
  export_batch_id UUID,
  evidence_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT audit_events_event_version_check CHECK (event_version > 0),
  CONSTRAINT audit_events_old_value_summary_object_check CHECK (jsonb_typeof(old_value_summary) = 'object'),
  CONSTRAINT audit_events_new_value_summary_object_check CHECK (jsonb_typeof(new_value_summary) = 'object'),
  CONSTRAINT audit_events_client_context_object_check CHECK (jsonb_typeof(client_context) = 'object'),
  CONSTRAINT audit_events_metadata_object_check CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE audit_events IS 'Append-only audit event log. Application code must not edit or delete rows in ordinary workflows.';
COMMENT ON COLUMN audit_events.event_type IS 'Specific event type from docs/architecture/audit-event-schema.md or future documented extension.';
COMMENT ON COLUMN audit_events.occurred_at IS 'Time the user or system action occurred.';
COMMENT ON COLUMN audit_events.server_received_at IS 'Server acceptance time, especially important for offline sync.';
COMMENT ON COLUMN audit_events.recorded_at IS 'Time the audit row was inserted.';
COMMENT ON COLUMN audit_events.idempotency_key IS 'Replay-safe operation key for submitted or synced operations.';
COMMENT ON COLUMN audit_events.offline_local_id IS 'Client local ID for offline queue lifecycle correlation.';
COMMENT ON COLUMN audit_events.export_batch_id IS 'Nullable export batch reference; FK is added after export_batches is created.';
COMMENT ON COLUMN audit_events.evidence_id IS 'Nullable evidence reference; FK can be added after evidence tables mature.';
COMMENT ON COLUMN audit_events.metadata IS 'Flexible metadata for event-specific details. Future partitioning may be required.';

CREATE INDEX idx_audit_events_project_recorded_at ON audit_events(project_id, recorded_at DESC);
CREATE INDEX idx_audit_events_category_type ON audit_events(event_category, event_type);
CREATE INDEX idx_audit_events_target ON audit_events(target_entity_type, target_entity_id);
CREATE INDEX idx_audit_events_actor_user ON audit_events(actor_user_id);
CREATE INDEX idx_audit_events_correlation ON audit_events(correlation_id);
CREATE INDEX idx_audit_events_idempotency_key ON audit_events(idempotency_key);
CREATE INDEX idx_audit_events_offline_local_id ON audit_events(offline_local_id);
CREATE INDEX idx_audit_events_export_batch ON audit_events(export_batch_id);
