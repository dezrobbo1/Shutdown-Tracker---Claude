\set ON_ERROR_STOP on

DO $$
DECLARE
  found_count INTEGER;
  batch_default TEXT;
  line_default TEXT;
  function_definition TEXT;
BEGIN
  SELECT count(*) INTO found_count
  FROM information_schema.tables
  WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE';
  IF found_count <> 20 THEN
    RAISE EXCEPTION 'Expected 20 V007 tables after failed V008; found %', found_count;
  END IF;

  IF to_regclass('public.export_candidate_records') IS NOT NULL THEN
    RAISE EXCEPTION 'Failed V008 left export_candidate_records behind';
  END IF;

  SELECT count(*) INTO found_count
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND (table_name, column_name) IN (
      ('approval_records', 'authoritative_export_candidate_id'),
      ('approval_records', 'candidate_binding_policy_version'),
      ('export_batch_lines', 'authoritative_export_candidate_id'),
      ('export_batch_lines', 'captured_source_event_or_payload_hash'),
      ('export_batch_lines', 'captured_task_external_uid'),
      ('export_batch_lines', 'captured_task_external_id'),
      ('export_batch_lines', 'captured_task_name')
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V008 left % added columns behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_class
  WHERE relnamespace = 'public'::regnamespace
    AND relname IN (
      'approval_records_candidate_event_order',
      'export_candidate_records_project_snapshot',
      'export_candidate_records_task_field',
      'export_candidate_records_source',
      'export_batch_lines_policy2_task_field_unique',
      'export_batch_lines_authoritative_candidate'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V008 left % indexes behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_proc
  WHERE pronamespace = 'public'::regnamespace
    AND proname IN (
      'enforce_approval_candidate_binding_policy',
      'canonical_export_candidate_instant',
      'normalize_export_candidate_new_value',
      'prepare_export_candidate_record',
      'freeze_export_candidate_record_history',
      'validate_policy2_export_batch_integrity'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V008 left % new functions behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_trigger
  WHERE NOT tgisinternal
    AND tgname IN (
      'approval_records_enforce_candidate_binding_policy',
      'export_candidate_records_prepare',
      'export_candidate_records_freeze_history'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V008 left % new triggers behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_constraint
  WHERE connamespace = 'public'::regnamespace
    AND conname IN (
      'project_snapshots_id_project_unique',
      'imported_tasks_id_project_snapshot_unique',
      'approval_records_candidate_binding_pair_check',
      'approval_records_candidate_binding_identity_unique',
      'approval_records_authoritative_candidate_fkey',
      'export_batch_lines_policy2_candidate_capture_check',
      'export_batch_lines_authoritative_candidate_fkey'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V008 left % constraints on existing tables behind', found_count;
  END IF;

  SELECT column_default INTO batch_default
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'export_batches'
    AND column_name = 'integrity_policy_version';
  SELECT column_default INTO line_default
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'export_batch_lines'
    AND column_name = 'integrity_policy_version';
  IF batch_default IS NULL OR batch_default NOT LIKE '%1%'
     OR line_default IS NULL OR line_default NOT LIKE '%1%' THEN
    RAISE EXCEPTION 'Failed V008 did not restore policy-1 defaults: batch %, line %',
      batch_default, line_default;
  END IF;

  SELECT pg_get_functiondef('enforce_export_batch_integrity_policy()'::regprocedure)
    INTO function_definition;
  IF function_definition NOT LIKE '%version 1%' THEN
    RAISE EXCEPTION 'Failed V008 did not restore the V007 batch-policy function';
  END IF;

  SELECT pg_get_functiondef('enforce_export_batch_line_integrity_policy()'::regprocedure)
    INTO function_definition;
  IF function_definition NOT LIKE '%version 1%' THEN
    RAISE EXCEPTION 'Failed V008 did not restore the V007 line-policy function';
  END IF;
END;
$$;

\echo 'Late V008 failure rolled back all migration objects and restored V007 behavior.'
