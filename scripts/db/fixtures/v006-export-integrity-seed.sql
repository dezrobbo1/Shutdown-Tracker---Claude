\set ON_ERROR_STOP on

-- Synthetic V006 history used only by the migration-integrity validator.
INSERT INTO projects (id, name, timezone)
VALUES ('10000000-0000-0000-0000-000000000001', 'Synthetic export-integrity validation project', 'UTC');

INSERT INTO source_files (
  id, project_id, original_filename, file_kind, storage_uri, content_hash, size_bytes
)
VALUES (
  '10000000-0000-0000-0000-000000000002',
  '10000000-0000-0000-0000-000000000001',
  'synthetic-validation.xml',
  'mspdi_xml',
  'validation://synthetic/source',
  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  1024
);

INSERT INTO import_batches (
  id, project_id, source_file_id, status, parser_name, parser_version, started_at, completed_at
)
VALUES (
  '10000000-0000-0000-0000-000000000003',
  '10000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000002',
  'accepted',
  'synthetic-validator',
  '1',
  '2026-07-18T00:00:00Z',
  '2026-07-18T00:01:00Z'
);

INSERT INTO project_snapshots (
  id, project_id, import_batch_id, status, external_project_uid,
  external_project_name, project_status_date, snapshot_version, accepted_at
)
VALUES (
  '10000000-0000-0000-0000-000000000004',
  '10000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000003',
  'accepted',
  '9001',
  'Synthetic validation schedule',
  '2026-07-18T00:00:00Z',
  1,
  '2026-07-18T00:02:00Z'
);

INSERT INTO imported_tasks (
  id, project_id, project_snapshot_id, external_uid, external_id, name, wbs,
  outline_number, outline_level, is_summary, actual_start, actual_finish,
  percent_complete, physical_percent_complete
)
VALUES
  (
    '10000000-0000-0000-0000-000000000101',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000004',
    '101', '11', 'Synthetic leaf task', '1.1', '1.1', 2, false,
    '2026-07-18T01:00:00Z', NULL, 25, 50
  ),
  (
    '10000000-0000-0000-0000-000000000102',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000004',
    '100', '10', 'Synthetic summary task', '1', '1', 1, true,
    NULL, NULL, 10, 10
  );

INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state,
  requested_at, reviewed_at, reason, created_at, metadata
)
VALUES
  ('10000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000301', 'approved_for_export', '2026-07-18T02:00:00Z', '2026-07-18T02:01:00Z', 'Historical physical percent approval', '2026-07-18T02:01:00Z', '{"fixture":"physical"}'),
  ('10000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000302', 'approved_for_export', '2026-07-18T02:00:00Z', '2026-07-18T02:01:00Z', 'Older approval before awaiting review', '2026-07-18T02:01:00Z', '{"fixture":"awaiting-old"}'),
  ('10000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000302', 'awaiting_review', '2026-07-18T02:02:00Z', NULL, 'Newer awaiting review', '2026-07-18T02:02:00Z', '{"fixture":"awaiting-new"}'),
  ('10000000-0000-0000-0000-000000000204', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000303', 'approved_for_export', '2026-07-18T02:00:00Z', '2026-07-18T02:01:00Z', 'Older approval before rejection', '2026-07-18T02:01:00Z', '{"fixture":"rejected-old"}'),
  ('10000000-0000-0000-0000-000000000205', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000303', 'rejected', '2026-07-18T02:02:00Z', '2026-07-18T02:03:00Z', 'Newer rejection', '2026-07-18T02:03:00Z', '{"fixture":"rejected-new"}'),
  ('10000000-0000-0000-0000-000000000206', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000304', 'approved_for_export', '2026-07-18T02:00:00Z', '2026-07-18T02:01:00Z', 'Older approval before supersession', '2026-07-18T02:01:00Z', '{"fixture":"superseded-old"}'),
  ('10000000-0000-0000-0000-000000000207', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000304', 'superseded', '2026-07-18T02:02:00Z', '2026-07-18T02:03:00Z', 'Newer supersession', '2026-07-18T02:03:00Z', '{"fixture":"superseded-new"}'),
  ('10000000-0000-0000-0000-000000000208', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000305', 'approved_for_export', '2026-07-18T02:00:00Z', '2026-07-18T02:01:00Z', 'Tied legacy approval', '2026-07-18T02:04:00Z', '{"fixture":"tie-approved"}'),
  ('10000000-0000-0000-0000-000000000209', '10000000-0000-0000-0000-000000000001', 'task_update', '10000000-0000-0000-0000-000000000305', 'rejected', '2026-07-18T02:00:00Z', '2026-07-18T02:01:00Z', 'Tied legacy rejection', '2026-07-18T02:04:00Z', '{"fixture":"tie-rejected"}');

INSERT INTO export_batches (
  id, project_id, project_snapshot_id, status, preview_created_at, approved_at,
  generated_at, verified_at, export_file_uri, export_file_hash, failure_reason,
  superseded_by_export_batch_id, created_at, metadata
)
VALUES
  ('10000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T03:00:00Z', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-18T03:00:00Z', '{"fixture":"draft"}'),
  ('10000000-0000-0000-0000-000000000402', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'awaiting_approval', '2026-07-18T03:00:00Z', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-18T03:01:00Z', '{"fixture":"awaiting"}'),
  ('10000000-0000-0000-0000-000000000403', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'approved', '2026-07-18T03:00:00Z', '2026-07-18T03:01:00Z', NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-18T03:02:00Z', '{"fixture":"approved"}'),
  ('10000000-0000-0000-0000-000000000404', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'rejected', '2026-07-18T03:00:00Z', NULL, NULL, NULL, NULL, NULL, 'Synthetic rejection', NULL, '2026-07-18T03:03:00Z', '{"fixture":"rejected"}'),
  ('10000000-0000-0000-0000-000000000405', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'generated', '2026-07-18T03:00:00Z', '2026-07-18T03:01:00Z', '2026-07-18T03:02:00Z', NULL, 'validation://synthetic/generated.xml', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', NULL, NULL, '2026-07-18T03:04:00Z', '{"fixture":"generated"}'),
  ('10000000-0000-0000-0000-000000000406', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'opened_in_microsoft_project', '2026-07-18T03:00:00Z', '2026-07-18T03:01:00Z', '2026-07-18T03:02:00Z', NULL, 'validation://synthetic/opened.xml', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', NULL, NULL, '2026-07-18T03:05:00Z', '{"fixture":"opened"}'),
  ('10000000-0000-0000-0000-000000000407', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'verified', '2026-07-18T03:00:00Z', '2026-07-18T03:01:00Z', '2026-07-18T03:02:00Z', '2026-07-18T03:03:00Z', 'validation://synthetic/verified.xml', 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', NULL, NULL, '2026-07-18T03:06:00Z', '{"fixture":"verified"}'),
  ('10000000-0000-0000-0000-000000000408', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'superseded', '2026-07-18T03:00:00Z', '2026-07-18T03:01:00Z', NULL, NULL, NULL, NULL, NULL, '10000000-0000-0000-0000-000000000409', '2026-07-18T03:07:00Z', '{"fixture":"superseded"}'),
  ('10000000-0000-0000-0000-000000000409', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'failed', '2026-07-18T03:00:00Z', NULL, NULL, NULL, NULL, NULL, 'Synthetic generation failure', NULL, '2026-07-18T03:08:00Z', '{"fixture":"failed"}');

INSERT INTO export_batch_lines (
  id, export_batch_id, project_id, project_snapshot_id, imported_task_id,
  source_entity_type, source_entity_id, field_name, old_value, new_value,
  source_timestamp, reason, is_leaf_task, is_export_eligible, created_at, metadata
)
VALUES
  ('10000000-0000-0000-0000-000000000501', '10000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101', 'task_update', '10000000-0000-0000-0000-000000000301', 'physical_percent_complete', '25', '50', '2026-07-18T02:01:00Z', 'Historical physical percent', true, true, '2026-07-18T03:00:01Z', '{"fixture":"physical"}'),
  ('10000000-0000-0000-0000-000000000502', '10000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101', 'task_update', '10000000-0000-0000-0000-000000000302', 'percent_complete', '25', '50', '2026-07-18T02:01:00Z', 'Same-value duplicate one', true, true, '2026-07-18T03:00:02Z', '{"fixture":"duplicate-one"}'),
  ('10000000-0000-0000-0000-000000000503', '10000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101', 'task_update', '10000000-0000-0000-0000-000000000302', 'percent_complete', '25', '50', '2026-07-18T02:01:00Z', 'Same-value duplicate two', true, true, '2026-07-18T03:00:03Z', '{"fixture":"duplicate-two"}'),
  ('10000000-0000-0000-0000-000000000504', '10000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101', 'task_update', '10000000-0000-0000-0000-000000000302', 'percent_complete', '25', '75', '2026-07-18T02:01:00Z', 'Different-value duplicate', true, true, '2026-07-18T03:00:04Z', '{"fixture":"duplicate-different"}');

CREATE SCHEMA validation;
CREATE TABLE validation.pre_upgrade_fingerprints (
  relation_name TEXT PRIMARY KEY,
  row_count BIGINT NOT NULL,
  business_hash TEXT NOT NULL
);

INSERT INTO validation.pre_upgrade_fingerprints
SELECT 'approval_records', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, project_id, source_entity_type, source_entity_id, approval_state,
  requested_by_user_id, requested_at, reviewed_by_user_id, reviewed_at,
  reason, created_at, metadata
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex') FROM approval_records;

INSERT INTO validation.pre_upgrade_fingerprints
SELECT 'export_batches', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, project_id, project_snapshot_id, status, preview_created_at,
  approved_at, approved_by_user_id, generated_at, generated_by_user_id,
  verified_at, verified_by_user_id, export_file_uri, export_file_hash,
  failure_reason, superseded_by_export_batch_id, created_at, metadata
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex') FROM export_batches;

INSERT INTO validation.pre_upgrade_fingerprints
SELECT 'export_batch_lines', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, export_batch_id, project_id, project_snapshot_id, imported_task_id,
  source_entity_type, source_entity_id, field_name, old_value, new_value,
  source_actor_user_id, source_timestamp, reason, is_leaf_task,
  is_export_eligible, created_at, metadata
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex') FROM export_batch_lines;

INSERT INTO validation.pre_upgrade_fingerprints
SELECT 'imported_tasks', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, project_id, project_snapshot_id, external_uid, external_id, name, wbs,
  outline_number, outline_level, is_summary, parent_external_uid,
  parent_imported_task_id, planned_start, planned_finish, actual_start,
  actual_finish, percent_complete, physical_percent_complete, notes,
  raw_data, created_at
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex') FROM imported_tasks;
