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

CREATE OR REPLACE FUNCTION validation.expect_failure(
  label TEXT,
  statement TEXT,
  expected_state TEXT,
  expected_message_fragment TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
  actual_state TEXT;
  actual_message TEXT;
  succeeded BOOLEAN := false;
BEGIN
  BEGIN
    EXECUTE statement;
    succeeded := true;
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS
      actual_state = RETURNED_SQLSTATE,
      actual_message = MESSAGE_TEXT;
    IF actual_state IS DISTINCT FROM expected_state THEN
      RAISE EXCEPTION '% returned SQLSTATE %, expected %: %',
        label, actual_state, expected_state, actual_message;
    END IF;
    IF expected_message_fragment IS NOT NULL
       AND strpos(actual_message, expected_message_fragment) = 0 THEN
      RAISE EXCEPTION '% returned unexpected message: %', label, actual_message;
    END IF;
  END;

  IF succeeded THEN
    RAISE EXCEPTION '% unexpectedly succeeded', label;
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
  'V006 business rows or values changed during the populated V007 upgrade'
);

SELECT validation.assert_true(
  (SELECT count(*) = 9 AND count(DISTINCT status) = 9
   FROM export_batches
   WHERE integrity_policy_version IS NULL),
  'All nine V006 export-batch lifecycle states must remain readable'
);

SELECT validation.assert_true(
  NOT EXISTS (
    SELECT 1
    FROM approval_records
    WHERE approval_event_order IS NOT NULL
       OR authoritative_export_candidate_id IS NOT NULL
       OR candidate_binding_policy_version IS NOT NULL
  ),
  'V006 approval records must retain null ordering and candidate markers'
);

SELECT validation.assert_true(
  NOT EXISTS (
    SELECT 1
    FROM export_batches
    WHERE integrity_policy_version IS NOT NULL
       OR line_set_sealed IS NOT NULL
  ),
  'V006 export batches must retain null policy and seal markers'
);

SELECT validation.assert_true(
  NOT EXISTS (
    SELECT 1
    FROM export_batch_lines
    WHERE integrity_policy_version IS NOT NULL
       OR captured_approval_record_id IS NOT NULL
       OR captured_approval_state IS NOT NULL
       OR authoritative_export_candidate_id IS NOT NULL
       OR captured_source_event_or_payload_hash IS NOT NULL
       OR captured_source_version IS NOT NULL
       OR captured_task_external_uid IS NOT NULL
       OR captured_task_external_id IS NOT NULL
       OR captured_task_name IS NOT NULL
  ),
  'V006 export lines must retain null policy and capture markers'
);

SELECT validation.assert_true(
  (SELECT count(*) = 0 FROM export_candidate_records),
  'V007 must not invent authoritative candidates for V006 history'
);

SELECT validation.assert_true(
  (SELECT is_export_eligible
   FROM export_batch_lines
   WHERE id = '10000000-0000-0000-0000-000000000501'),
  'Historical physical-percent eligibility must remain readable and unchanged'
);

SELECT validation.assert_true(
  (SELECT count(*) = 3 AND count(DISTINCT new_value) = 2
   FROM export_batch_lines
   WHERE export_batch_id = '10000000-0000-0000-0000-000000000401'
     AND imported_task_id = '10000000-0000-0000-0000-000000000101'
     AND field_name = 'percent_complete'),
  'Historical same-value and different-value duplicate lines must remain intact'
);

SELECT validation.assert_true(
  (SELECT count(*) = 2 AND count(DISTINCT approval_state) = 2
   FROM approval_records
   WHERE source_entity_id = '10000000-0000-0000-0000-000000000305'
     AND created_at = '2026-07-18T02:04:00Z'
     AND approval_event_order IS NULL),
  'Conflicting tied legacy approvals must remain ambiguous and unsequenced'
);

SELECT validation.expect_failure(
  'legacy draft progression',
  $$UPDATE export_batches
    SET status = 'awaiting_approval'
    WHERE id = '10000000-0000-0000-0000-000000000401'$$,
  '23514',
  'fresh export preview'
);

SELECT validation.expect_failure(
  'legacy approved progression',
  $$UPDATE export_batches
    SET status = 'generated', generated_at = now(),
        export_file_uri = 'validation://must-not-generate.xml',
        export_file_hash = repeat('f', 64)
    WHERE id = '10000000-0000-0000-0000-000000000403'$$,
  '23514',
  'fresh export preview'
);

SELECT validation.expect_failure(
  'legacy line update',
  $$UPDATE export_batch_lines
    SET new_value = '99'
    WHERE id = '10000000-0000-0000-0000-000000000501'$$,
  '23514',
  'append-only'
);

SELECT validation.expect_failure(
  'legacy line delete',
  $$DELETE FROM export_batch_lines
    WHERE id = '10000000-0000-0000-0000-000000000501'$$,
  '23514',
  'append-only'
);

SELECT validation.assert_true(
  (SELECT count(*) = 5
   FROM export_batches
   WHERE id IN (
     '10000000-0000-0000-0000-000000000404',
     '10000000-0000-0000-0000-000000000405',
     '10000000-0000-0000-0000-000000000406',
     '10000000-0000-0000-0000-000000000407',
     '10000000-0000-0000-0000-000000000408'
   )
     AND integrity_policy_version IS NULL),
  'Legacy rejected/generated/opened/verified/superseded history must remain readable'
);

INSERT INTO export_batches (
  id, project_id, project_snapshot_id, status, preview_created_at
)
VALUES (
  '10000000-0000-0000-0000-000000000699',
  '10000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000004',
  'draft_preview',
  '2026-07-18T05:00:00Z'
);

SELECT validation.assert_true(
  (SELECT integrity_policy_version = 1 AND line_set_sealed = false
   FROM export_batches
   WHERE id = '10000000-0000-0000-0000-000000000699'),
  'New export batches must default to an unsealed policy-1 line set'
);

SELECT validation.expect_failure(
  'unsupported future-policy batch',
  $$INSERT INTO export_batches (
      id, project_id, project_snapshot_id, status,
      integrity_policy_version, line_set_sealed
    ) VALUES (
      '10000000-0000-0000-0000-000000000698',
      '10000000-0000-0000-0000-000000000001',
      '10000000-0000-0000-0000-000000000004',
      'draft_preview', 2, false
    )$$,
  '23514',
  'integrity policy version 1'
);

SELECT validation.expect_failure(
  'unbound post-V007 approval event',
  $$INSERT INTO approval_records (
      id, project_id, source_entity_type, source_entity_id, approval_state
    ) VALUES (
      '10000000-0000-0000-0000-000000000697',
      '10000000-0000-0000-0000-000000000001',
      'task_update',
      '10000000-0000-0000-0000-000000000796',
      'approved_for_export'
    )$$,
  '23514',
  'authoritative policy-1'
);

\echo 'Populated V006-to-V007 preservation and legacy freeze assertions passed.'
