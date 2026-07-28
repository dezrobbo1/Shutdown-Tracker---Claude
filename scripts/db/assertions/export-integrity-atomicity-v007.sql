\set ON_ERROR_STOP on

CREATE OR REPLACE FUNCTION validation.assert_true(
  condition BOOLEAN,
  failure_message TEXT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  IF condition IS DISTINCT FROM true THEN
    RAISE EXCEPTION '%', failure_message;
  END IF;
END;
$$;

DO $$
DECLARE
  found_count INTEGER;
BEGIN
  SELECT count(*) INTO found_count
  FROM information_schema.tables
  WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE';
  IF found_count <> 20 THEN
    RAISE EXCEPTION 'Expected exact V006 20-table baseline after failed V007; found %', found_count;
  END IF;

  IF to_regclass('public.export_candidate_records') IS NOT NULL THEN
    RAISE EXCEPTION 'Failed V007 left export_candidate_records behind';
  END IF;

  SELECT count(*) INTO found_count
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND (table_name, column_name) IN (
      ('approval_records', 'approval_event_order'),
      ('approval_records', 'authoritative_export_candidate_id'),
      ('approval_records', 'candidate_binding_policy_version'),
      ('export_batches', 'integrity_policy_version'),
      ('export_batches', 'line_set_sealed'),
      ('export_batch_lines', 'integrity_policy_version'),
      ('export_batch_lines', 'captured_approval_record_id'),
      ('export_batch_lines', 'captured_approval_state'),
      ('export_batch_lines', 'authoritative_export_candidate_id'),
      ('export_batch_lines', 'captured_source_event_or_payload_hash'),
      ('export_batch_lines', 'captured_source_version'),
      ('export_batch_lines', 'captured_task_external_uid'),
      ('export_batch_lines', 'captured_task_external_id'),
      ('export_batch_lines', 'captured_task_name')
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % added columns behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_proc
  WHERE pronamespace = 'public'::regnamespace
    AND proname IN (
      'canonical_export_candidate_instant',
      'normalize_export_candidate_new_value',
      'calculate_export_candidate_fingerprint',
      'prepare_export_candidate_record',
      'freeze_export_candidate_record_history',
      'prepare_current_approval_event',
      'freeze_approval_record_history',
      'validate_current_export_batch_integrity',
      'enforce_export_batch_integrity_policy',
      'freeze_legacy_export_history',
      'enforce_export_batch_line_integrity_policy',
      'freeze_export_batch_line_history'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % integrity functions behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_trigger
  WHERE NOT tgisinternal
    AND tgname IN (
      'export_candidate_records_prepare',
      'export_candidate_records_freeze_history',
      'approval_records_prepare_current_event',
      'approval_records_freeze_history',
      'export_batches_enforce_integrity_policy',
      'export_batches_freeze_legacy_history',
      'export_batch_lines_enforce_integrity_policy',
      'export_batch_lines_freeze_history'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % integrity triggers behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_class
  WHERE relnamespace = 'public'::regnamespace
    AND relname IN (
      'approval_records_event_order_seq',
      'approval_records_event_order_unique',
      'approval_records_source_event_order',
      'approval_records_candidate_event_order',
      'export_candidate_records',
      'export_candidate_records_project_snapshot',
      'export_candidate_records_task_field',
      'export_candidate_records_source',
      'export_batch_lines_current_policy_task_field_unique',
      'export_batch_lines_current_policy_candidate_unique',
      'export_batch_lines_authoritative_candidate'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % sequences or indexes behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_constraint
  WHERE connamespace = 'public'::regnamespace
    AND conname IN (
      'project_snapshots_id_project_unique',
      'imported_tasks_id_project_snapshot_unique',
      'export_candidate_records_policy_check',
      'export_candidate_records_field_check',
      'export_candidate_records_source_type_check',
      'export_candidate_records_source_version_check',
      'export_candidate_records_source_hash_check',
      'export_candidate_records_metadata_object_check',
      'export_candidate_records_snapshot_fkey',
      'export_candidate_records_task_fkey',
      'export_candidate_records_id_policy_unique',
      'export_candidate_records_id_policy_project_unique',
      'approval_records_event_order_positive_check',
      'approval_records_candidate_binding_pair_check',
      'approval_records_candidate_fkey',
      'approval_records_candidate_capture_unique',
      'export_batches_integrity_policy_version_check',
      'export_batches_policy_capture_check',
      'export_batches_line_identity_unique',
      'export_batch_lines_integrity_policy_version_check',
      'export_batch_lines_current_candidate_capture_check',
      'export_batch_lines_current_eligibility_check',
      'export_batch_lines_batch_identity_fkey',
      'export_batch_lines_candidate_fkey',
      'export_batch_lines_captured_approval_fkey'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % constraints behind', found_count;
  END IF;
END;
$$;

CREATE TEMP TABLE actual_fingerprints (
  relation_name TEXT PRIMARY KEY,
  row_count BIGINT NOT NULL,
  business_hash TEXT NOT NULL
);

INSERT INTO actual_fingerprints
SELECT 'approval_records', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, project_id, source_entity_type, source_entity_id, approval_state,
  requested_by_user_id, requested_at, reviewed_by_user_id, reviewed_at,
  reason, created_at, metadata
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex')
FROM approval_records;

INSERT INTO actual_fingerprints
SELECT 'export_batches', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, project_id, project_snapshot_id, status, preview_created_at,
  approved_at, approved_by_user_id, generated_at, generated_by_user_id,
  verified_at, verified_by_user_id, export_file_uri, export_file_hash,
  failure_reason, superseded_by_export_batch_id, created_at, metadata
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex')
FROM export_batches;

INSERT INTO actual_fingerprints
SELECT 'export_batch_lines', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, export_batch_id, project_id, project_snapshot_id, imported_task_id,
  source_entity_type, source_entity_id, field_name, old_value, new_value,
  source_actor_user_id, source_timestamp, reason, is_leaf_task,
  is_export_eligible, created_at, metadata
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex')
FROM export_batch_lines;

INSERT INTO actual_fingerprints
SELECT 'imported_tasks', count(*), encode(digest(coalesce(string_agg(jsonb_build_array(
  id, project_id, project_snapshot_id, external_uid, external_id, name, wbs,
  outline_number, outline_level, is_summary, parent_external_uid,
  parent_imported_task_id, planned_start, planned_finish, actual_start,
  actual_finish, percent_complete, physical_percent_complete, notes,
  raw_data, created_at
)::text, E'\n' ORDER BY id), ''), 'sha256'), 'hex')
FROM imported_tasks;

SELECT validation.assert_true(
  NOT EXISTS (
    SELECT 1
    FROM validation.pre_upgrade_fingerprints expected
    FULL JOIN actual_fingerprints actual USING (relation_name)
    WHERE expected.row_count IS DISTINCT FROM actual.row_count
       OR expected.business_hash IS DISTINCT FROM actual.business_hash
       OR expected.relation_name IS NULL
       OR actual.relation_name IS NULL
  ),
  'Failed V007 changed populated V006 business rows despite --single-transaction'
);

\echo 'Late failing V007 left no migration objects or V006 business-data changes.'
