\set ON_ERROR_STOP on

-- Rows created under V007 policy 1 before V008 must remain readable and frozen.
INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state,
  requested_at, reviewed_at, reason, created_at
)
VALUES
  ('10000000-0000-0000-0000-000000000601', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000701', 'approved_for_export', '2026-07-18T04:00:00Z', '2026-07-18T04:01:00Z', 'Policy 1 percent candidate', '2026-07-18T04:01:00Z'),
  ('10000000-0000-0000-0000-000000000602', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000702', 'approved_for_export', '2026-07-18T04:02:00Z', '2026-07-18T04:03:00Z', 'Policy 1 actual-start candidate', '2026-07-18T04:03:00Z');

INSERT INTO export_batches (
  id, project_id, project_snapshot_id, status, preview_created_at, created_at, metadata
)
VALUES
  ('10000000-0000-0000-0000-000000000611', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T04:10:00Z', '2026-07-18T04:10:00Z', '{"fixture":"policy1-draft"}'),
  ('10000000-0000-0000-0000-000000000612', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T04:11:00Z', '2026-07-18T04:11:00Z', '{"fixture":"policy1-approved"}');

INSERT INTO export_batch_lines (
  id, export_batch_id, project_id, project_snapshot_id, imported_task_id,
  source_entity_type, source_entity_id, field_name, old_value, new_value,
  source_timestamp, reason, is_leaf_task, is_export_eligible,
  captured_approval_record_id, captured_approval_state, created_at
)
VALUES
  ('10000000-0000-0000-0000-000000000621', '10000000-0000-0000-0000-000000000611', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101', 'task_update', '10000000-0000-0000-0000-000000000701', 'percent_complete', '25', '50', '2026-07-18T04:01:00Z', 'Policy 1 percent candidate', true, true, '10000000-0000-0000-0000-000000000601', 'approved_for_export', '2026-07-18T04:10:01Z'),
  ('10000000-0000-0000-0000-000000000622', '10000000-0000-0000-0000-000000000612', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101', 'task_update', '10000000-0000-0000-0000-000000000702', 'actual_start', '2026-07-18T01:00:00Z', '2026-07-18T01:30:00Z', '2026-07-18T04:03:00Z', 'Policy 1 actual-start candidate', true, true, '10000000-0000-0000-0000-000000000602', 'approved_for_export', '2026-07-18T04:11:01Z');

UPDATE export_batches
SET line_set_sealed = true
WHERE id IN ('10000000-0000-0000-0000-000000000611', '10000000-0000-0000-0000-000000000612');

UPDATE export_batches
SET status = 'approved', approved_at = '2026-07-18T04:12:00Z'
WHERE id = '10000000-0000-0000-0000-000000000612';

CREATE TABLE validation.pre_v008_policy1_fingerprints (
  relation_name TEXT PRIMARY KEY,
  row_count BIGINT NOT NULL,
  business_hash TEXT NOT NULL
);

INSERT INTO validation.pre_v008_policy1_fingerprints
SELECT 'export_batches', count(*), encode(digest(string_agg(jsonb_build_array(
  id, project_id, project_snapshot_id, status, preview_created_at,
  approved_at, approved_by_user_id, generated_at, generated_by_user_id,
  verified_at, verified_by_user_id, export_file_uri, export_file_hash,
  failure_reason, superseded_by_export_batch_id, created_at, metadata,
  integrity_policy_version, line_set_sealed
)::text, E'\n' ORDER BY id), 'sha256'), 'hex')
FROM export_batches WHERE integrity_policy_version = 1;

INSERT INTO validation.pre_v008_policy1_fingerprints
SELECT 'export_batch_lines', count(*), encode(digest(string_agg(jsonb_build_array(
  id, export_batch_id, project_id, project_snapshot_id, imported_task_id,
  source_entity_type, source_entity_id, field_name, old_value, new_value,
  source_actor_user_id, source_timestamp, reason, is_leaf_task,
  is_export_eligible, created_at, metadata, integrity_policy_version,
  captured_approval_record_id, captured_approval_state
)::text, E'\n' ORDER BY id), 'sha256'), 'hex')
FROM export_batch_lines WHERE integrity_policy_version = 1;
