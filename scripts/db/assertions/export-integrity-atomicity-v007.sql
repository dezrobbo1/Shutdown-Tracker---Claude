\set ON_ERROR_STOP on

DO $$
DECLARE
  found_count INTEGER;
BEGIN
  SELECT count(*) INTO found_count
  FROM information_schema.tables
  WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE';
  IF found_count <> 20 THEN
    RAISE EXCEPTION 'Expected 20 V006 tables after failed V007; found %', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND (table_name, column_name) IN (
      ('approval_records', 'approval_event_order'),
      ('export_batches', 'integrity_policy_version'),
      ('export_batches', 'line_set_sealed'),
      ('export_batch_lines', 'integrity_policy_version'),
      ('export_batch_lines', 'captured_approval_record_id'),
      ('export_batch_lines', 'captured_approval_state')
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % added columns behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_class
  WHERE relnamespace = 'public'::regnamespace
    AND relname IN (
      'approval_records_event_order_seq',
      'approval_records_event_order_unique',
      'approval_records_source_event_order',
      'export_batch_lines_current_policy_task_field_unique'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % sequences or indexes behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_proc
  WHERE pronamespace = 'public'::regnamespace
    AND proname IN (
      'assign_approval_event_order',
      'freeze_approval_record_history',
      'enforce_export_batch_integrity_policy',
      'enforce_export_batch_line_integrity_policy',
      'lock_active_export_batches_for_approval_event',
      'freeze_legacy_export_history',
      'freeze_export_batch_line_history'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % functions behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_trigger
  WHERE NOT tgisinternal
    AND tgname IN (
      'approval_records_assign_event_order',
      'approval_records_freeze_history',
      'export_batches_enforce_integrity_policy',
      'export_batch_lines_enforce_integrity_policy',
      'approval_records_lock_active_export_batches',
      'export_batches_freeze_legacy_history',
      'export_batch_lines_freeze_history'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % triggers behind', found_count;
  END IF;

  SELECT count(*) INTO found_count
  FROM pg_constraint
  WHERE connamespace = 'public'::regnamespace
    AND conname IN (
      'approval_records_event_order_positive_check',
      'approval_records_capture_identity_unique',
      'export_batches_integrity_policy_version_check',
      'export_batches_current_policy_line_set_check',
      'export_batches_line_identity_unique',
      'export_batch_lines_integrity_policy_version_check',
      'export_batch_lines_captured_approval_fkey',
      'export_batch_lines_batch_identity_fkey',
      'export_batch_lines_current_policy_field_authority_check',
      'export_batch_lines_current_policy_approval_capture_check',
      'export_batch_lines_current_policy_eligible_approval_check'
    );
  IF found_count <> 0 THEN
    RAISE EXCEPTION 'Failed V007 left % constraints behind', found_count;
  END IF;
END;
$$;

\echo 'Late V007 failure rolled back all migration objects.'
