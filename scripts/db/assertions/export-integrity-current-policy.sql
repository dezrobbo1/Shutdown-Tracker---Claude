\set ON_ERROR_STOP on

CREATE SCHEMA validation;

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

CREATE OR REPLACE FUNCTION validation.create_candidate_pair(
  candidate_id UUID,
  approval_id UUID,
  source_id UUID,
  task_id UUID,
  candidate_field TEXT,
  candidate_new_value TEXT,
  hash_character TEXT,
  event_timestamp TIMESTAMPTZ,
  candidate_reason TEXT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO export_candidate_records (
    id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
    source_entity_type, source_entity_id, field_name, normalized_new_value,
    source_event_or_payload_hash, source_timestamp, reason, metadata
  )
  VALUES (
    candidate_id,
    approval_id,
    '20000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000004',
    task_id,
    'task_update',
    source_id,
    candidate_field,
    candidate_new_value,
    repeat(hash_character, 64),
    event_timestamp,
    candidate_reason,
    jsonb_build_object('fixture', candidate_id)
  );

  INSERT INTO approval_records (
    id, project_id, source_entity_type, source_entity_id, approval_state,
    authoritative_export_candidate_id, candidate_binding_policy_version,
    requested_at, reviewed_at, reason, created_at
  )
  VALUES (
    approval_id,
    '20000000-0000-0000-0000-000000000001',
    'task_update',
    source_id,
    'approved_for_export',
    candidate_id,
    2,
    event_timestamp - interval '1 minute',
    event_timestamp,
    candidate_reason,
    event_timestamp
  );
END;
$$;

CREATE OR REPLACE FUNCTION validation.insert_candidate_line(
  line_id UUID,
  batch_id UUID,
  candidate_id UUID,
  snapshot_override UUID DEFAULT NULL,
  task_override UUID DEFAULT NULL,
  source_id_override UUID DEFAULT NULL,
  approval_override UUID DEFAULT NULL,
  field_override TEXT DEFAULT NULL,
  old_value_override TEXT DEFAULT NULL,
  new_value_override TEXT DEFAULT NULL,
  hash_override TEXT DEFAULT NULL,
  task_uid_override TEXT DEFAULT NULL,
  task_id_override TEXT DEFAULT NULL,
  task_name_override TEXT DEFAULT NULL,
  leaf_override BOOLEAN DEFAULT NULL,
  eligibility_override BOOLEAN DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO export_batch_lines (
    id, export_batch_id, project_id, project_snapshot_id, imported_task_id,
    source_entity_type, source_entity_id, field_name, old_value, new_value,
    source_actor_user_id, source_timestamp, reason, is_leaf_task,
    is_export_eligible, captured_approval_record_id, captured_approval_state,
    authoritative_export_candidate_id, captured_source_event_or_payload_hash,
    captured_task_external_uid, captured_task_external_id, captured_task_name
  )
  SELECT
    line_id,
    batch_id,
    candidate.project_id,
    coalesce(snapshot_override, candidate.project_snapshot_id),
    coalesce(task_override, candidate.imported_task_id),
    candidate.source_entity_type,
    coalesce(source_id_override, candidate.source_entity_id),
    coalesce(field_override, candidate.field_name),
    coalesce(old_value_override, candidate.normalized_old_value),
    coalesce(new_value_override, candidate.normalized_new_value),
    candidate.source_actor_user_id,
    candidate.source_timestamp,
    candidate.reason,
    coalesce(leaf_override, candidate.captured_is_leaf_task),
    coalesce(
      eligibility_override,
      candidate.captured_is_leaf_task
        AND candidate.field_name IN ('percent_complete', 'actual_start', 'actual_finish')
    ),
    coalesce(approval_override, candidate.approval_record_id),
    candidate.approval_state,
    candidate.id,
    coalesce(hash_override, candidate.source_event_or_payload_hash),
    coalesce(task_uid_override, candidate.captured_task_external_uid),
    coalesce(task_id_override, candidate.captured_task_external_id),
    coalesce(task_name_override, candidate.captured_task_name)
  FROM export_candidate_records candidate
  WHERE candidate.id = candidate_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Validation candidate % was not found', candidate_id;
  END IF;
END;
$$;

INSERT INTO projects (id, name, timezone)
VALUES ('20000000-0000-0000-0000-000000000001', 'Synthetic current-policy project', 'UTC');

INSERT INTO source_files (
  id, project_id, original_filename, file_kind, storage_uri
)
VALUES (
  '20000000-0000-0000-0000-000000000002',
  '20000000-0000-0000-0000-000000000001',
  'synthetic-current.xml',
  'mspdi_xml',
  'validation://synthetic/current'
);

INSERT INTO import_batches (
  id, project_id, source_file_id, status, parser_name, parser_version
)
VALUES
  ('20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 'accepted', 'synthetic-validator', '2'),
  ('20000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 'superseded', 'synthetic-validator', '2');

INSERT INTO project_snapshots (
  id, project_id, import_batch_id, status, external_project_uid,
  external_project_name, snapshot_version, accepted_at
)
VALUES
  ('20000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000003', 'accepted', '9101', 'Synthetic current schedule', 1, '2026-07-18T06:00:00Z'),
  ('20000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000005', 'superseded', '9100', 'Synthetic stale schedule', 2, NULL);

INSERT INTO imported_tasks (
  id, project_id, project_snapshot_id, external_uid, external_id, name,
  is_summary, actual_start, actual_finish, percent_complete, physical_percent_complete
)
VALUES
  ('20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '201', '21', 'Current leaf task', false, '2026-07-18T06:00:00Z', NULL, 25, 40),
  ('20000000-0000-0000-0000-000000000102', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '200', '20', 'Current summary task', true, NULL, NULL, 10, 10),
  ('20000000-0000-0000-0000-000000000103', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000006', '301', '31', 'Stale leaf task', false, NULL, NULL, 0, 0);

SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000201', '20000000-0000-0000-0000-000000000301', '20000000-0000-0000-0000-000000000401', '20000000-0000-0000-0000-000000000101', 'percent_complete', '50.0', '1', '2026-07-18T07:01:00Z', 'Current percent candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000202', '20000000-0000-0000-0000-000000000302', '20000000-0000-0000-0000-000000000402', '20000000-0000-0000-0000-000000000101', 'actual_start', '2026-07-18T06:30:00+00:00', '2', '2026-07-18T07:02:00Z', 'Current actual-start candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000203', '20000000-0000-0000-0000-000000000303', '20000000-0000-0000-0000-000000000403', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T08:00:00Z', '3', '2026-07-18T07:03:00Z', 'Current actual-finish candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000204', '20000000-0000-0000-0000-000000000304', '20000000-0000-0000-0000-000000000404', '20000000-0000-0000-0000-000000000101', 'physical_percent_complete', '60', '4', '2026-07-18T07:04:00Z', 'Internal physical-percent candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000205', '20000000-0000-0000-0000-000000000305', '20000000-0000-0000-0000-000000000405', '20000000-0000-0000-0000-000000000101', 'percent_complete', '75', '5', '2026-07-18T07:05:00Z', 'Different-value duplicate candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000206', '20000000-0000-0000-0000-000000000306', '20000000-0000-0000-0000-000000000406', '20000000-0000-0000-0000-000000000102', 'actual_finish', '2026-07-18T09:00:00Z', '6', '2026-07-18T07:06:00Z', 'Summary-task internal candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000210', '20000000-0000-0000-0000-000000000310', '20000000-0000-0000-0000-000000000410', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T10:00:00Z', 'a', '2026-07-18T07:10:00Z', 'Line-seal concurrency candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000211', '20000000-0000-0000-0000-000000000311', '20000000-0000-0000-0000-000000000411', '20000000-0000-0000-0000-000000000101', 'percent_complete', '55', 'b', '2026-07-18T07:11:00Z', 'Duplicate concurrency candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000212', '20000000-0000-0000-0000-000000000312', '20000000-0000-0000-0000-000000000412', '20000000-0000-0000-0000-000000000101', 'actual_start', '2026-07-18T10:30:00Z', 'c', '2026-07-18T07:12:00Z', 'Generation concurrency candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000213', '20000000-0000-0000-0000-000000000313', '20000000-0000-0000-0000-000000000413', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T11:00:00Z', 'd', '2026-07-18T07:13:00Z', 'Rollback concurrency candidate');

SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

SELECT validation.assert_true(
  (SELECT normalized_old_value = '25'
       AND normalized_new_value = '50'
       AND captured_task_external_uid = '201'
       AND captured_task_external_id = '21'
       AND captured_task_name = 'Current leaf task'
       AND captured_is_leaf_task
   FROM export_candidate_records
   WHERE id = '20000000-0000-0000-0000-000000000201'),
  'Candidate preparation must capture and normalize the exact task baseline'
);

SELECT validation.assert_true(
  (SELECT normalized_old_value = '2026-07-18T06:00:00Z'
       AND normalized_new_value = '2026-07-18T06:30:00Z'
   FROM export_candidate_records
   WHERE id = '20000000-0000-0000-0000-000000000202'),
  'Actual-start candidate values must use canonical UTC instants'
);

DO $$
BEGIN
  BEGIN
    INSERT INTO export_candidate_records (
      id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
      source_entity_type, source_entity_id, field_name, normalized_new_value,
      source_event_or_payload_hash
    ) VALUES (
      '20000000-0000-0000-0000-000000000998',
      '20000000-0000-0000-0000-000000000998',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      '20000000-0000-0000-0000-000000000101',
      'task_update',
      '20000000-0000-0000-0000-000000000498',
      'percent_complete',
      '80',
      repeat('e', 64)
    );
    SET CONSTRAINTS export_candidate_records_approval_binding_fkey IMMEDIATE;
    RAISE EXCEPTION 'Unattached candidate unexpectedly passed its deferred foreign key';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
END;
$$;

INSERT INTO export_batches (
  id, project_id, project_snapshot_id, status, preview_created_at
)
VALUES
  ('20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T08:00:00Z'),
  ('20000000-0000-0000-0000-000000000510', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T08:10:00Z'),
  ('20000000-0000-0000-0000-000000000511', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T08:11:00Z'),
  ('20000000-0000-0000-0000-000000000512', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T08:12:00Z'),
  ('20000000-0000-0000-0000-000000000513', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T08:13:00Z');

SELECT validation.assert_true(
  (SELECT count(*) = 5
   FROM export_batches
   WHERE id IN (
     '20000000-0000-0000-0000-000000000501',
     '20000000-0000-0000-0000-000000000510',
     '20000000-0000-0000-0000-000000000511',
     '20000000-0000-0000-0000-000000000512',
     '20000000-0000-0000-0000-000000000513'
   )
     AND integrity_policy_version = 2
     AND line_set_sealed = false),
  'New export batches must default to unsealed policy 2'
);

SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000601', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000201');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000602', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000202');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000603', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000203');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000604', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000204');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000605', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000206');

SELECT validation.expect_failure(
  'candidate line with a different task',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000620',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      task_override => '20000000-0000-0000-0000-000000000102'::UUID
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different snapshot',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000635',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      snapshot_override => '20000000-0000-0000-0000-000000000006'::UUID
    )$$,
  '23514',
  'matching policy-2 export batch'
);

SELECT validation.expect_failure(
  'candidate line with a different field',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000621',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      field_override => 'actual_finish'
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different new value',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000622',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      new_value_override => '99'
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different old value',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000623',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      old_value_override => '24'
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different source',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000624',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      source_id_override => '20000000-0000-0000-0000-000000000499'::UUID
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different approval',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000625',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      approval_override => '20000000-0000-0000-0000-000000000302'::UUID
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different payload hash',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000626',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      hash_override => repeat('f', 64)
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different task UID',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000627',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      task_uid_override => '999'
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different task ID',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000633',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      task_id_override => '999'
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'candidate line with a different task name',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000634',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201',
      task_name_override => 'Wrong task'
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'physical percent marked export eligible',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000628',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000204',
      eligibility_override => true
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'summary task marked export eligible',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000629',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000206',
      eligibility_override => true
    )$$,
  '23514',
  'does not exactly match'
);

SELECT validation.expect_failure(
  'same-value duplicate task field',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000630',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000201'
    )$$,
  '23505',
  'export_batch_lines_policy2_task_field_unique'
);

SELECT validation.expect_failure(
  'different-value duplicate task field',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000631',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000205'
    )$$,
  '23505',
  'export_batch_lines_policy2_task_field_unique'
);

SELECT validation.assert_true(
  (SELECT count(*) = 3
   FROM export_batch_lines
   WHERE export_batch_id = '20000000-0000-0000-0000-000000000501'
     AND is_export_eligible
     AND field_name IN ('percent_complete', 'actual_start', 'actual_finish')),
  'The three authorized fields must remain export eligible'
);

SELECT validation.assert_true(
  (SELECT count(*) = 2
   FROM export_batch_lines
   WHERE export_batch_id = '20000000-0000-0000-0000-000000000501'
     AND NOT is_export_eligible
     AND field_name IN ('physical_percent_complete', 'actual_finish')),
  'Physical percent and summary-task facts must remain readable but ineligible'
);

SELECT validation.expect_failure(
  'candidate update',
  $$UPDATE export_candidate_records
    SET normalized_new_value = '90'
    WHERE id = '20000000-0000-0000-0000-000000000201'$$,
  '23514',
  'append-only'
);

SELECT validation.expect_failure(
  'candidate delete',
  $$DELETE FROM export_candidate_records
    WHERE id = '20000000-0000-0000-0000-000000000201'$$,
  '23514',
  'append-only'
);

UPDATE export_batches
SET line_set_sealed = true
WHERE id = '20000000-0000-0000-0000-000000000501';

SELECT validation.expect_failure(
  'late line insertion',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000632',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000205'
    )$$,
  '23514',
  'before the batch line set is sealed'
);

SELECT validation.expect_failure(
  'batch unsealing',
  $$UPDATE export_batches
    SET line_set_sealed = false
    WHERE id = '20000000-0000-0000-0000-000000000501'$$,
  '23514',
  'seal may only transition'
);

SELECT validation.expect_failure(
  'line update',
  $$UPDATE export_batch_lines
    SET new_value = '99'
    WHERE id = '20000000-0000-0000-0000-000000000601'$$,
  '23514',
  'append-only'
);

SELECT validation.expect_failure(
  'line delete',
  $$DELETE FROM export_batch_lines
    WHERE id = '20000000-0000-0000-0000-000000000601'$$,
  '23514',
  'append-only'
);

INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state, reason, created_at
)
VALUES (
  '20000000-0000-0000-0000-000000000704',
  '20000000-0000-0000-0000-000000000001',
  'task_update',
  '20000000-0000-0000-0000-000000000404',
  'rejected',
  'Physical-percent source rejected after mixed preview creation',
  '2026-07-18T08:20:00Z'
);

SELECT validation.expect_failure(
  'changed approval on an ineligible line blocks the complete mixed batch',
  $$UPDATE export_batches
    SET status = 'approved', approved_at = '2026-07-18T08:21:00Z'
    WHERE id = '20000000-0000-0000-0000-000000000501'$$,
  '23514',
  'approval is no longer current'
);

SELECT validation.assert_true(
  (SELECT status = 'draft_preview'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000501'),
  'A stale ineligible line must leave the complete mixed batch unapproved'
);

SELECT validation.expect_failure(
  'candidate from superseded snapshot',
  $$SELECT validation.create_candidate_pair(
      '20000000-0000-0000-0000-000000000996',
      '20000000-0000-0000-0000-000000000996',
      '20000000-0000-0000-0000-000000000496',
      '20000000-0000-0000-0000-000000000103',
      'percent_complete', '10', 'f', '2026-07-18T07:20:00Z',
      'Stale snapshot candidate'
    )$$,
  '23514',
  'accepted snapshot'
);

SELECT validation.expect_failure(
  'uppercase candidate hash',
  $$INSERT INTO export_candidate_records (
      id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
      source_entity_type, source_entity_id, field_name, normalized_new_value,
      source_event_or_payload_hash
    ) VALUES (
      '20000000-0000-0000-0000-000000000995',
      '20000000-0000-0000-0000-000000000995',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      '20000000-0000-0000-0000-000000000101',
      'task_update', '20000000-0000-0000-0000-000000000495',
      'percent_complete', '80', repeat('A', 64)
    )$$,
  '23514',
  'lowercase SHA-256'
);

SELECT validation.expect_failure(
  'unsupported candidate field',
  $$INSERT INTO export_candidate_records (
      id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
      source_entity_type, source_entity_id, field_name, normalized_new_value,
      source_event_or_payload_hash
    ) VALUES (
      '20000000-0000-0000-0000-000000000994',
      '20000000-0000-0000-0000-000000000994',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      '20000000-0000-0000-0000-000000000101',
      'task_update', '20000000-0000-0000-0000-000000000494',
      'planned_start', '2026-07-18T08:00:00Z', repeat('e', 64)
    )$$,
  '23514',
  'unsupported export candidate field'
);

SELECT validation.expect_failure(
  'fractional percent complete',
  $$INSERT INTO export_candidate_records (
      id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
      source_entity_type, source_entity_id, field_name, normalized_new_value,
      source_event_or_payload_hash
    ) VALUES (
      '20000000-0000-0000-0000-000000000993',
      '20000000-0000-0000-0000-000000000993',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      '20000000-0000-0000-0000-000000000101',
      'task_update', '20000000-0000-0000-0000-000000000493',
      'percent_complete', '75.5', repeat('e', 64)
    )$$,
  '23514',
  'whole number'
);

SELECT validation.expect_failure(
  'new policy-1 batch',
  $$INSERT INTO export_batches (
      id, project_id, project_snapshot_id, status,
      integrity_policy_version, line_set_sealed
    ) VALUES (
      '20000000-0000-0000-0000-000000000591',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      'draft_preview', 1, false
    )$$,
  '23514',
  'require integrity policy version 2'
);

SELECT validation.expect_failure(
  'new unknown-policy batch',
  $$INSERT INTO export_batches (
      id, project_id, project_snapshot_id, status,
      integrity_policy_version, line_set_sealed
    ) VALUES (
      '20000000-0000-0000-0000-000000000592',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      'draft_preview', 3, false
    )$$,
  '23514',
  'require integrity policy version 2'
);

DO $$
BEGIN
  BEGIN
    INSERT INTO approval_records (
      id, project_id, source_entity_type, source_entity_id, approval_state,
      authoritative_export_candidate_id, candidate_binding_policy_version
    ) VALUES (
      '20000000-0000-0000-0000-000000000997',
      '20000000-0000-0000-0000-000000000001',
      'task_update',
      '20000000-0000-0000-0000-000000000497',
      'approved_for_export',
      '20000000-0000-0000-0000-000000000997',
      2
    );
    SET CONSTRAINTS approval_records_authoritative_candidate_fkey IMMEDIATE;
    RAISE EXCEPTION 'Unattached approval unexpectedly passed its deferred foreign key';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
END;
$$;

INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state, created_at
)
VALUES
  ('20000000-0000-0000-0000-000000000981', '20000000-0000-0000-0000-000000000001', 'task_update', '20000000-0000-0000-0000-000000000481', 'awaiting_review', '2026-07-18T08:30:00Z'),
  ('20000000-0000-0000-0000-000000000982', '20000000-0000-0000-0000-000000000001', 'task_update', '20000000-0000-0000-0000-000000000481', 'rejected', '2026-07-18T08:30:00Z');

SELECT validation.assert_true(
  (SELECT newer.approval_event_order > older.approval_event_order
   FROM approval_records older
   JOIN approval_records newer
     ON older.id = '20000000-0000-0000-0000-000000000981'
    AND newer.id = '20000000-0000-0000-0000-000000000982'),
  'Approval event order must be deterministic within one transaction'
);

SELECT validation.assert_true(
  (SELECT id = '20000000-0000-0000-0000-000000000982'
   FROM approval_records
   WHERE project_id = '20000000-0000-0000-0000-000000000001'
     AND source_entity_type = 'task_update'
     AND source_entity_id = '20000000-0000-0000-0000-000000000481'
   ORDER BY approval_event_order DESC
   LIMIT 1),
  'The latest ordered approval event must be selected deterministically'
);

SELECT validation.expect_failure(
  'caller-assigned approval order',
  $$INSERT INTO approval_records (
      id, project_id, source_entity_type, source_entity_id,
      approval_state, approval_event_order
    ) VALUES (
      '20000000-0000-0000-0000-000000000983',
      '20000000-0000-0000-0000-000000000001',
      'task_update',
      '20000000-0000-0000-0000-000000000483',
      'rejected', 999999
    )$$,
  '23514',
  'assigned by the database'
);

SELECT validation.insert_candidate_line(
  '20000000-0000-0000-0000-000000000612',
  '20000000-0000-0000-0000-000000000512',
  '20000000-0000-0000-0000-000000000212'
);
SELECT validation.insert_candidate_line(
  '20000000-0000-0000-0000-000000000613',
  '20000000-0000-0000-0000-000000000513',
  '20000000-0000-0000-0000-000000000213'
);

UPDATE export_batches
SET line_set_sealed = true
WHERE id IN (
  '20000000-0000-0000-0000-000000000512',
  '20000000-0000-0000-0000-000000000513'
);

UPDATE export_batches
SET status = 'approved', approved_at = '2026-07-18T08:40:00Z'
WHERE id IN (
  '20000000-0000-0000-0000-000000000512',
  '20000000-0000-0000-0000-000000000513'
);

SELECT validation.expect_failure(
  'actual timestamp beyond PostgreSQL precision',
  $$INSERT INTO export_candidate_records (
      id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
      source_entity_type, source_entity_id, field_name, normalized_new_value,
      source_event_or_payload_hash
    ) VALUES (
      '20000000-0000-0000-0000-000000000990',
      '20000000-0000-0000-0000-000000000990',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      '20000000-0000-0000-0000-000000000101',
      'task_update', '20000000-0000-0000-0000-000000000490',
      'actual_finish', '2026-07-18T12:00:00.1234567Z', repeat('e', 64)
    )$$,
  '23514',
  'ISO-8601 offset timestamp'
);

SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000214', '20000000-0000-0000-0000-000000000314', '20000000-0000-0000-0000-000000000414', '20000000-0000-0000-0000-000000000101', 'percent_complete', '65', 'e', '2026-07-18T09:14:00Z', 'Stale-before-seal candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000215', '20000000-0000-0000-0000-000000000315', '20000000-0000-0000-0000-000000000415', '20000000-0000-0000-0000-000000000101', 'actual_start', '2026-07-18T12:30:00Z', 'e', '2026-07-18T09:15:00Z', 'Stale-before-approval candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000216', '20000000-0000-0000-0000-000000000316', '20000000-0000-0000-0000-000000000416', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T13:00:00Z', 'e', '2026-07-18T09:16:00Z', 'Stale-before-generation candidate');
SELECT validation.create_candidate_pair('20000000-0000-0000-0000-000000000217', '20000000-0000-0000-0000-000000000317', '20000000-0000-0000-0000-000000000417', '20000000-0000-0000-0000-000000000101', 'percent_complete', '70', 'e', '2026-07-18T09:17:00Z', 'Baseline-drift candidate');

INSERT INTO export_batches (
  id, project_id, project_snapshot_id, status, preview_created_at
)
VALUES
  ('20000000-0000-0000-0000-000000000514', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T09:14:00Z'),
  ('20000000-0000-0000-0000-000000000515', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T09:15:00Z'),
  ('20000000-0000-0000-0000-000000000516', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T09:16:00Z'),
  ('20000000-0000-0000-0000-000000000517', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'draft_preview', '2026-07-18T09:17:00Z');

SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000650', '20000000-0000-0000-0000-000000000514', '20000000-0000-0000-0000-000000000214');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000651', '20000000-0000-0000-0000-000000000515', '20000000-0000-0000-0000-000000000215');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000652', '20000000-0000-0000-0000-000000000516', '20000000-0000-0000-0000-000000000216');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000653', '20000000-0000-0000-0000-000000000517', '20000000-0000-0000-0000-000000000217');

INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state, reason, created_at
)
VALUES (
  '20000000-0000-0000-0000-000000000714',
  '20000000-0000-0000-0000-000000000001',
  'task_update',
  '20000000-0000-0000-0000-000000000414',
  'rejected',
  'Rejected before sealing in the same transaction',
  '2026-07-18T09:20:00Z'
);

SELECT validation.expect_failure(
  'same-transaction rejection before sealing',
  $$UPDATE export_batches
    SET line_set_sealed = true
    WHERE id = '20000000-0000-0000-0000-000000000514'$$,
  '23514',
  'approval is no longer current'
);

UPDATE export_batches
SET line_set_sealed = true
WHERE id = '20000000-0000-0000-0000-000000000515';

INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state, reason, created_at
)
VALUES (
  '20000000-0000-0000-0000-000000000715',
  '20000000-0000-0000-0000-000000000001',
  'task_update',
  '20000000-0000-0000-0000-000000000415',
  'superseded',
  'Superseded before approval in the same transaction',
  '2026-07-18T09:21:00Z'
);

SELECT validation.expect_failure(
  'same-transaction supersession before approval',
  $$UPDATE export_batches
    SET status = 'approved', approved_at = '2026-07-18T09:22:00Z'
    WHERE id = '20000000-0000-0000-0000-000000000515'$$,
  '23514',
  'approval is no longer current'
);

UPDATE export_batches
SET line_set_sealed = true
WHERE id = '20000000-0000-0000-0000-000000000516';

UPDATE export_batches
SET status = 'approved', approved_at = '2026-07-18T09:22:00Z'
WHERE id = '20000000-0000-0000-0000-000000000516';

INSERT INTO approval_records (
  id, project_id, source_entity_type, source_entity_id, approval_state, reason, created_at
)
VALUES (
  '20000000-0000-0000-0000-000000000716',
  '20000000-0000-0000-0000-000000000001',
  'task_update',
  '20000000-0000-0000-0000-000000000416',
  'rejected',
  'Rejected before generation in the same transaction',
  '2026-07-18T09:23:00Z'
);

SELECT validation.expect_failure(
  'same-transaction rejection before generation',
  $$UPDATE export_batches
    SET status = 'generated', generated_at = '2026-07-18T09:24:00Z'
    WHERE id = '20000000-0000-0000-0000-000000000516'$$,
  '23514',
  'approval is no longer current'
);

DO $$
BEGIN
  BEGIN
    UPDATE project_snapshots
    SET status = 'superseded'
    WHERE id = '20000000-0000-0000-0000-000000000004';
    UPDATE export_batches
    SET line_set_sealed = true
    WHERE id = '20000000-0000-0000-0000-000000000517';
    RAISE EXCEPTION 'Snapshot-status drift unexpectedly permitted sealing';
  EXCEPTION WHEN check_violation THEN
    IF strpos(SQLERRM, 'accepted project snapshot') = 0 THEN
      RAISE;
    END IF;
  END;
END;
$$;

DO $$
BEGIN
  BEGIN
    UPDATE imported_tasks
    SET external_uid = '999'
    WHERE id = '20000000-0000-0000-0000-000000000101';
    UPDATE export_batches
    SET line_set_sealed = true
    WHERE id = '20000000-0000-0000-0000-000000000517';
    RAISE EXCEPTION 'Task-identity drift unexpectedly permitted sealing';
  EXCEPTION WHEN check_violation THEN
    IF strpos(SQLERRM, 'authoritative candidate and baseline') = 0 THEN
      RAISE;
    END IF;
  END;
END;
$$;

DO $$
BEGIN
  BEGIN
    UPDATE imported_tasks
    SET percent_complete = 26
    WHERE id = '20000000-0000-0000-0000-000000000101';
    UPDATE export_batches
    SET line_set_sealed = true
    WHERE id = '20000000-0000-0000-0000-000000000517';
    RAISE EXCEPTION 'Old-value drift unexpectedly permitted sealing';
  EXCEPTION WHEN check_violation THEN
    IF strpos(SQLERRM, 'authoritative candidate and baseline') = 0 THEN
      RAISE;
    END IF;
  END;
END;
$$;

DO $$
BEGIN
  BEGIN
    INSERT INTO export_candidate_records (
      id, approval_record_id, project_id, project_snapshot_id, imported_task_id,
      source_entity_type, source_entity_id, field_name, normalized_new_value,
      source_event_or_payload_hash
    ) VALUES (
      '20000000-0000-0000-0000-000000000989',
      '20000000-0000-0000-0000-000000000989',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      '20000000-0000-0000-0000-000000000101',
      'task_update',
      '20000000-0000-0000-0000-000000000489',
      'percent_complete',
      '80',
      repeat('e', 64)
    );
    INSERT INTO approval_records (
      id, project_id, source_entity_type, source_entity_id, approval_state,
      authoritative_export_candidate_id, candidate_binding_policy_version
    ) VALUES (
      '20000000-0000-0000-0000-000000000989',
      '20000000-0000-0000-0000-000000000001',
      'task_update',
      '20000000-0000-0000-0000-000000000488',
      'approved_for_export',
      '20000000-0000-0000-0000-000000000989',
      2
    );
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'Mismatched reciprocal source identity unexpectedly succeeded';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;
  SET CONSTRAINTS ALL DEFERRED;
END;
$$;

\echo 'Current policy-2 candidate, line-binding, and freeze assertions passed.'
