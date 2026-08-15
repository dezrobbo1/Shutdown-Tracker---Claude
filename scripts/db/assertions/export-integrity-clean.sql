\set ON_ERROR_STOP on

DO $$
DECLARE
  actual_tables TEXT[];
  expected_tables CONSTANT TEXT[] := ARRAY[
    'approval_records',
    'audit_events',
    'critical_update_lines',
    'critical_updates',
    'critical_watchlists',
    'critical_work_package_sources',
    'critical_work_packages',
    'export_batch_lines',
    'export_batches',
    'export_candidate_records',
    'import_batches',
    'imported_assignments',
    'imported_extended_attributes',
    'imported_resources',
    'imported_tasks',
    'project_snapshots',
    'projects',
    'reporting_periods',
    'reporting_policy_versions',
    'source_files',
    'task_lineage_links'
  ];
BEGIN
  SELECT array_agg(table_name ORDER BY table_name)
    INTO actual_tables
  FROM information_schema.tables
  WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE';

  IF actual_tables IS DISTINCT FROM expected_tables THEN
    RAISE EXCEPTION 'Expected exact 21-table V001-V007 baseline; found %', actual_tables;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'export_candidate_records'
      AND column_name = 'binding_policy_version'
      AND column_default LIKE '%1%'
      AND is_nullable = 'NO'
  ) THEN
    RAISE EXCEPTION 'Candidate policy marker/default is missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'export_batches'
      AND column_name = 'integrity_policy_version'
      AND column_default LIKE '%1%'
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'export_batch_lines'
      AND column_name = 'integrity_policy_version'
      AND column_default LIKE '%1%'
  ) THEN
    RAISE EXCEPTION 'Current export batch/line policy defaults are missing';
  END IF;

  IF (SELECT count(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'export_batches'
        AND column_name IN (
          'opened_in_microsoft_project_at',
          'opened_in_microsoft_project_by_user_id'
        )) <> 2 THEN
    RAISE EXCEPTION 'Authoritative Microsoft Project open columns are missing';
  END IF;

  IF (SELECT count(*)
      FROM pg_constraint
      WHERE connamespace = 'public'::regnamespace
        AND conname IN (
          'export_batches_opened_after_generated_check',
          'export_batches_verified_after_opened_check'
        )) <> 2 THEN
    RAISE EXCEPTION 'Microsoft Project open/verification ordering constraints are missing';
  END IF;

  IF (SELECT count(*)
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
        )) <> 12 THEN
    RAISE EXCEPTION 'Expected V007 integrity functions are missing';
  END IF;

  IF (SELECT count(*)
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
        )) <> 8 THEN
    RAISE EXCEPTION 'Expected V007 integrity triggers are missing';
  END IF;
END;
$$;

\echo 'Exact clean-install table and integrity-object inventory passed.'
