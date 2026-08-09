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

CREATE OR REPLACE FUNCTION validation.expect_failure_after(
  label TEXT,
  setup_statement TEXT,
  target_statement TEXT,
  expected_state TEXT,
  expected_message_fragment TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
  actual_state TEXT;
  actual_message TEXT;
  failed_as_expected BOOLEAN := false;
BEGIN
  BEGIN
    EXECUTE setup_statement;
    EXECUTE target_statement;
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
    failed_as_expected := true;
  END;

  IF NOT failed_as_expected THEN
    RAISE EXCEPTION '% unexpectedly succeeded', label;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION validation.create_candidate(
  candidate_id UUID,
  task_id UUID,
  candidate_field TEXT,
  candidate_new_value TEXT,
  candidate_reason TEXT,
  candidate_source_version TEXT DEFAULT 'source-v1'
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO export_candidate_records (
    id,
    project_id,
    project_snapshot_id,
    imported_task_id,
    source_entity_type,
    source_entity_id,
    source_version,
    field_name,
    normalized_new_value,
    source_timestamp,
    reason,
    metadata
  )
  VALUES (
    candidate_id,
    '20000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000004',
    task_id,
    'synthetic_task_update',
    candidate_id,
    candidate_source_version,
    candidate_field,
    candidate_new_value,
    '2026-07-18T07:00:00Z',
    candidate_reason,
    jsonb_build_object('fixture', candidate_id)
  );
END;
$$;

CREATE OR REPLACE FUNCTION validation.create_approval(
  approval_id UUID,
  candidate_id UUID,
  candidate_state approval_state,
  approval_reason TEXT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO approval_records (
    id,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state,
    authoritative_export_candidate_id,
    candidate_binding_policy_version,
    requested_at,
    reviewed_at,
    reason,
    created_at,
    metadata
  )
  VALUES (
    approval_id,
    '20000000-0000-0000-0000-000000000001',
    'export_candidate',
    candidate_id,
    candidate_state,
    candidate_id,
    1,
    '2026-07-18T07:00:00Z',
    '2026-07-18T07:01:00Z',
    approval_reason,
    '2026-07-18T07:01:00Z',
    jsonb_build_object('fixture', approval_id)
  );
END;
$$;

CREATE OR REPLACE FUNCTION validation.create_batch(batch_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO export_batches (
    id, project_id, project_snapshot_id, status, preview_created_at, metadata
  )
  VALUES (
    batch_id,
    '20000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000004',
    'draft_preview',
    '2026-07-18T08:00:00Z',
    jsonb_build_object('fixture', batch_id)
  );
END;
$$;

CREATE OR REPLACE FUNCTION validation.insert_candidate_line(
  line_id UUID,
  batch_id UUID,
  candidate_id UUID,
  field_override TEXT DEFAULT NULL,
  old_value_override TEXT DEFAULT NULL,
  new_value_override TEXT DEFAULT NULL,
  eligibility_override BOOLEAN DEFAULT NULL,
  source_version_override TEXT DEFAULT NULL,
  task_uid_override TEXT DEFAULT NULL,
  task_id_override TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO export_batch_lines (
    id,
    export_batch_id,
    project_id,
    project_snapshot_id,
    imported_task_id,
    source_entity_type,
    source_entity_id,
    field_name,
    old_value,
    new_value,
    source_actor_user_id,
    source_timestamp,
    reason,
    is_leaf_task,
    is_export_eligible,
    captured_approval_record_id,
    captured_approval_state,
    authoritative_export_candidate_id,
    captured_source_event_or_payload_hash,
    captured_source_version,
    captured_task_external_uid,
    captured_task_external_id,
    captured_task_name,
    integrity_policy_version,
    metadata
  )
  SELECT
    line_id,
    batch_id,
    candidate.project_id,
    candidate.project_snapshot_id,
    candidate.imported_task_id,
    candidate.source_entity_type,
    candidate.source_entity_id,
    coalesce(field_override, candidate.field_name),
    coalesce(old_value_override, candidate.normalized_old_value),
    coalesce(new_value_override, candidate.normalized_new_value),
    candidate.source_actor_user_id,
    candidate.source_timestamp,
    candidate.reason,
    candidate.captured_is_leaf_task,
    coalesce(
      eligibility_override,
      approval.approval_state = 'approved_for_export'::approval_state
        AND candidate.captured_is_leaf_task
        AND candidate.field_name IN ('percent_complete', 'actual_start', 'actual_finish')
    ),
    approval.id,
    approval.approval_state,
    candidate.id,
    candidate.source_event_or_payload_hash,
    coalesce(source_version_override, candidate.source_version),
    coalesce(task_uid_override, candidate.captured_task_external_uid),
    coalesce(task_id_override, candidate.captured_task_external_id),
    candidate.captured_task_name,
    candidate.binding_policy_version,
    candidate.metadata
  FROM export_candidate_records candidate
  JOIN LATERAL (
    SELECT id, approval_state
    FROM approval_records
    WHERE authoritative_export_candidate_id = candidate.id
      AND candidate_binding_policy_version = candidate.binding_policy_version
    ORDER BY approval_event_order DESC
    LIMIT 1
  ) approval ON true
  WHERE candidate.id = candidate_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Validation candidate or approval % was not found', candidate_id;
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
  ('20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 'accepted', 'synthetic-validator', '1'),
  ('20000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 'superseded', 'synthetic-validator', '1');

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
  ('20000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '201', '21', 'Current leaf task A', false, '2026-07-18T06:00:00.123456Z', NULL, 25, 40.5),
  ('20000000-0000-0000-0000-000000000102', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '202', '22', 'Current leaf task B', false, NULL, NULL, 10, 15),
  ('20000000-0000-0000-0000-000000000103', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '200', '20', 'Current summary task', true, NULL, NULL, 10, 10),
  ('20000000-0000-0000-0000-000000000104', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '201', '23', 'Duplicate UID leaf task', false, NULL, NULL, 0, 0),
  ('20000000-0000-0000-0000-000000000105', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '203', '21', 'Duplicate ID leaf task', false, NULL, NULL, 0, 0),
  ('20000000-0000-0000-0000-000000000106', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000006', '301', '31', 'Stale leaf task', false, NULL, NULL, 0, 0),
  ('20000000-0000-0000-0000-000000000107', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '01', '27', 'Noncanonical UID task', false, NULL, NULL, 0, 0),
  ('20000000-0000-0000-0000-000000000108', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', '208', '0', 'Noncanonical ID task', false, NULL, NULL, 0, 0);

SELECT validation.create_candidate('20000000-0000-0000-0000-000000000201', '20000000-0000-0000-0000-000000000101', 'percent_complete', '075.0', 'Canonical percent candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000202', '20000000-0000-0000-0000-000000000101', 'actual_start', '2026-07-18T06:30+00', 'Canonical actual-start candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000203', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T14:00+08', 'Canonical actual-finish candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000204', '20000000-0000-0000-0000-000000000101', 'physical_percent_complete', '60.5', 'Internal physical-percent candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000205', '20000000-0000-0000-0000-000000000103', 'actual_finish', '2026-07-18T16:00Z', 'Summary task candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000206', '20000000-0000-0000-0000-000000000102', 'percent_complete', '25', 'Awaiting-review candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000207', '20000000-0000-0000-0000-000000000101', 'percent_complete', '75', 'Same-value duplicate candidate', 'source-v2');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000208', '20000000-0000-0000-0000-000000000101', 'percent_complete', '80', 'Different-value duplicate candidate', 'source-v3');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000209', '20000000-0000-0000-0000-000000000104', 'actual_start', '2026-07-18T09:00Z', 'Duplicate UID candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000210', '20000000-0000-0000-0000-000000000105', 'actual_finish', '2026-07-18T10:00Z', 'Duplicate ID candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000229', '20000000-0000-0000-0000-000000000102', 'percent_complete', '36', 'Approval snapshot freshness');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000230', '20000000-0000-0000-0000-000000000102', 'percent_complete', '37', 'Approval baseline freshness');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000231', '20000000-0000-0000-0000-000000000102', 'actual_start', '2026-07-18T09:30Z', 'Approval task UID freshness');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000232', '20000000-0000-0000-0000-000000000102', 'actual_finish', '2026-07-18T12:00Z', 'Approval task ID freshness');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000233', '20000000-0000-0000-0000-000000000102', 'percent_complete', '38', 'Approval task name freshness');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000234', '20000000-0000-0000-0000-000000000102', 'percent_complete', '39', 'Approval leaf freshness');

SELECT validation.expect_failure_after(
  'stale snapshot approval',
  $$UPDATE project_snapshots
    SET status = 'superseded'
    WHERE id = '20000000-0000-0000-0000-000000000004'$$,
  $$SELECT validation.create_approval(
      '20000000-0000-0000-0000-000000000360',
      '20000000-0000-0000-0000-000000000229',
      'approved_for_export',
      'Must reject a stale snapshot approval'
    )$$,
  '23514',
  'accepted project snapshot'
);

SELECT validation.expect_failure_after(
  'stale baseline approval',
  $$UPDATE imported_tasks
    SET percent_complete = 11
    WHERE id = '20000000-0000-0000-0000-000000000102'$$,
  $$SELECT validation.create_approval(
      '20000000-0000-0000-0000-000000000361',
      '20000000-0000-0000-0000-000000000230',
      'approved_for_export',
      'Must reject a stale baseline approval'
    )$$,
  '23514',
  'task identity or baseline'
);

SELECT validation.expect_failure_after(
  'stale task UID approval',
  $$UPDATE imported_tasks
    SET external_uid = '299'
    WHERE id = '20000000-0000-0000-0000-000000000102'$$,
  $$SELECT validation.create_approval(
      '20000000-0000-0000-0000-000000000362',
      '20000000-0000-0000-0000-000000000231',
      'approved_for_export',
      'Must reject a stale task UID approval'
    )$$,
  '23514',
  'task identity or baseline'
);

SELECT validation.expect_failure_after(
  'stale task ID approval',
  $$UPDATE imported_tasks
    SET external_id = '29'
    WHERE id = '20000000-0000-0000-0000-000000000102'$$,
  $$SELECT validation.create_approval(
      '20000000-0000-0000-0000-000000000363',
      '20000000-0000-0000-0000-000000000232',
      'approved_for_export',
      'Must reject a stale task ID approval'
    )$$,
  '23514',
  'task identity or baseline'
);

SELECT validation.expect_failure_after(
  'stale task name approval',
  $$UPDATE imported_tasks
    SET name = 'Changed before approval'
    WHERE id = '20000000-0000-0000-0000-000000000102'$$,
  $$SELECT validation.create_approval(
      '20000000-0000-0000-0000-000000000364',
      '20000000-0000-0000-0000-000000000233',
      'approved_for_export',
      'Must reject a stale task name approval'
    )$$,
  '23514',
  'task identity or baseline'
);

SELECT validation.expect_failure_after(
  'stale leaf-state approval',
  $$UPDATE imported_tasks
    SET is_summary = true
    WHERE id = '20000000-0000-0000-0000-000000000102'$$,
  $$SELECT validation.create_approval(
      '20000000-0000-0000-0000-000000000365',
      '20000000-0000-0000-0000-000000000234',
      'approved_for_export',
      'Must reject a stale leaf-state approval'
    )$$,
  '23514',
  'task identity or baseline'
);

SELECT validation.assert_true(
  (SELECT count(*) = 0
   FROM approval_records
   WHERE id BETWEEN '20000000-0000-0000-0000-000000000360'
                AND '20000000-0000-0000-0000-000000000365'),
  'Stale approved-for-export events must not be persisted'
);

SELECT validation.create_approval('20000000-0000-0000-0000-000000000301', '20000000-0000-0000-0000-000000000201', 'approved_for_export', 'Approve percent');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000302', '20000000-0000-0000-0000-000000000202', 'approved_for_export', 'Approve actual start');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000303', '20000000-0000-0000-0000-000000000203', 'approved_for_export', 'Approve actual finish');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000304', '20000000-0000-0000-0000-000000000204', 'approved_for_export', 'Approve internal physical percent');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000305', '20000000-0000-0000-0000-000000000205', 'approved_for_export', 'Approve summary candidate');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000306', '20000000-0000-0000-0000-000000000206', 'awaiting_review', 'Awaiting review');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000307', '20000000-0000-0000-0000-000000000207', 'approved_for_export', 'Approve duplicate candidate');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000308', '20000000-0000-0000-0000-000000000208', 'approved_for_export', 'Approve different duplicate');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000309', '20000000-0000-0000-0000-000000000209', 'approved_for_export', 'Approve UID alias');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000310', '20000000-0000-0000-0000-000000000210', 'approved_for_export', 'Approve ID alias');

SELECT validation.assert_true(
  (SELECT binding_policy_version = 1
       AND normalized_old_value = '25'
       AND normalized_new_value = '75'
       AND captured_task_external_uid = '201'
       AND captured_task_external_id = '21'
       AND captured_task_name = 'Current leaf task A'
       AND captured_is_leaf_task
       AND source_event_or_payload_hash ~ '^[0-9a-f]{64}$'
   FROM export_candidate_records
   WHERE id = '20000000-0000-0000-0000-000000000201'),
  'Candidate creation must capture and normalize the authoritative task fact'
);

SELECT validation.assert_true(
  (SELECT normalized_old_value = '2026-07-18T06:00:00.123456Z'
       AND normalized_new_value = '2026-07-18T06:30:00Z'
   FROM export_candidate_records
   WHERE id = '20000000-0000-0000-0000-000000000202'),
  'Imported actual precision and proposed whole-second normalization must stay distinct'
);

SELECT validation.assert_true(
  (SELECT normalized_new_value = '2026-07-18T14:00:00+08:00'
   FROM export_candidate_records
   WHERE id = '20000000-0000-0000-0000-000000000203'),
  'Proposed actual normalization must preserve the reviewed wall-clock and offset'
);

SELECT validation.assert_true(
  (SELECT count(DISTINCT source_event_or_payload_hash) = count(*)
   FROM export_candidate_records
   WHERE id IN (
     '20000000-0000-0000-0000-000000000201',
     '20000000-0000-0000-0000-000000000207',
     '20000000-0000-0000-0000-000000000208'
   )),
  'Candidate fingerprint must bind candidate identity, source version, and normalized value'
);

SELECT validation.expect_failure(
  'candidate update',
  $$UPDATE export_candidate_records
    SET normalized_new_value = '99'
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

SELECT validation.expect_failure(
  'approval update',
  $$UPDATE approval_records
    SET approval_state = 'rejected'
    WHERE id = '20000000-0000-0000-0000-000000000301'$$,
  '23514',
  'append-only'
);

SELECT validation.expect_failure(
  'caller-assigned approval order',
  $$INSERT INTO approval_records (
      id, project_id, source_entity_type, source_entity_id, approval_state,
      authoritative_export_candidate_id, candidate_binding_policy_version,
      approval_event_order
    ) VALUES (
      '20000000-0000-0000-0000-000000000398',
      '20000000-0000-0000-0000-000000000001',
      'export_candidate',
      '20000000-0000-0000-0000-000000000201',
      'rejected',
      '20000000-0000-0000-0000-000000000201',
      1,
      999999
    )$$,
  '23514',
  'database'
);

SELECT validation.expect_failure(
  'mismatched candidate approval source identity',
  $$INSERT INTO approval_records (
      id, project_id, source_entity_type, source_entity_id, approval_state,
      authoritative_export_candidate_id, candidate_binding_policy_version
    ) VALUES (
      '20000000-0000-0000-0000-000000000399',
      '20000000-0000-0000-0000-000000000001',
      'task_update',
      '20000000-0000-0000-0000-000000000208',
      'approved_for_export',
      '20000000-0000-0000-0000-000000000201',
      1
    )$$,
  '23514',
  'exact export candidate'
);

SELECT validation.expect_failure(
  'unsupported candidate field',
  $$SELECT validation.create_candidate(
      '20000000-0000-0000-0000-000000000298',
      '20000000-0000-0000-0000-000000000101',
      'planned_finish',
      '2026-07-19T00:00Z',
      'Unsupported field'
    )$$,
  '23514',
  'unsupported export candidate field'
);

SELECT validation.expect_failure(
  'fractional proposed percent',
  $$SELECT validation.create_candidate(
      '20000000-0000-0000-0000-000000000297',
      '20000000-0000-0000-0000-000000000101',
      'percent_complete',
      '75.5',
      'Fractional percent'
    )$$,
  '23514',
  'whole number'
);

SELECT validation.expect_failure(
  'offset-free proposed actual',
  $$SELECT validation.create_candidate(
      '20000000-0000-0000-0000-000000000296',
      '20000000-0000-0000-0000-000000000101',
      'actual_finish',
      '2026-07-18T12:00:00',
      'Offset-free actual'
    )$$,
  '23514',
  'offset'
);

SELECT validation.expect_failure(
  'fractional proposed actual',
  $$SELECT validation.create_candidate(
      '20000000-0000-0000-0000-000000000295',
      '20000000-0000-0000-0000-000000000101',
      'actual_finish',
      '2026-07-18T12:00:00.1Z',
      'Fractional actual'
    )$$,
  '23514',
  'whole-second'
);

SELECT validation.expect_failure(
  'noncanonical Project UID',
  $$SELECT validation.create_candidate(
      '20000000-0000-0000-0000-000000000294',
      '20000000-0000-0000-0000-000000000107',
      'percent_complete',
      '10',
      'Noncanonical UID'
    )$$,
  '23514',
  'canonical positive'
);

SELECT validation.expect_failure(
  'noncanonical Project ID',
  $$SELECT validation.create_candidate(
      '20000000-0000-0000-0000-000000000293',
      '20000000-0000-0000-0000-000000000108',
      'percent_complete',
      '10',
      'Noncanonical ID'
    )$$,
  '23514',
  'canonical positive'
);

SELECT validation.create_batch('20000000-0000-0000-0000-000000000501');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000601', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000201');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000602', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000202');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000603', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000203');
SELECT validation.expect_failure(
  'caller-spoofed candidate line',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000699',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000204',
      'percent_complete', '999', '999', true, 'spoofed-version', '999', '999'
    )$$,
  '23514',
  'exactly match'
);
SELECT validation.expect_failure(
  'physical-percent line forced eligible',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000696',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000204',
      eligibility_override => true
    )$$,
  '23514',
  'exactly match'
);
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000604', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000204');
SELECT validation.expect_failure(
  'summary line forced eligible',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000698',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000205',
      eligibility_override => true
    )$$,
  '23514',
  'exactly match'
);
SELECT validation.expect_failure(
  'awaiting-review line forced eligible',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000697',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000206',
      eligibility_override => true
    )$$,
  '23514',
  'exactly match'
);
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000605', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000205');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000606', '20000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000206');

SELECT validation.assert_true(
  (SELECT field_name = 'physical_percent_complete'
       AND old_value = '40.5'
       AND new_value = '60.5'
       AND NOT is_export_eligible
       AND captured_source_version = 'source-v1'
       AND captured_task_external_uid = '201'
       AND captured_task_external_id = '21'
   FROM export_batch_lines
   WHERE id = '20000000-0000-0000-0000-000000000604'),
  'Line creation must derive the exact candidate fact and keep physical percent ineligible'
);

SELECT validation.assert_true(
  (SELECT bool_and(NOT is_export_eligible)
   FROM export_batch_lines
   WHERE id IN (
     '20000000-0000-0000-0000-000000000605',
     '20000000-0000-0000-0000-000000000606'
   )
   GROUP BY export_batch_id
   HAVING count(*) = 2),
  'Summary and non-approved lines must remain visible but ineligible'
);

SELECT validation.expect_failure(
  'same-value duplicate task/field line',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000607',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000207'
    )$$,
  '23505',
  'task_field'
);

SELECT validation.expect_failure(
  'different-value duplicate task/field line',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000608',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000208'
    )$$,
  '23505',
  'task_field'
);

SELECT validation.expect_failure(
  'duplicate Project UID alias',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000609',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000209'
    )$$,
  '23514',
  'Project UID'
);

SELECT validation.expect_failure(
  'duplicate Project ID alias',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000610',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000210'
    )$$,
  '23514',
  'Project ID'
);

UPDATE export_batches
SET line_set_sealed = true
WHERE id = '20000000-0000-0000-0000-000000000501';

SELECT validation.expect_failure(
  'sealed preview membership insertion',
  $$SELECT validation.insert_candidate_line(
      '20000000-0000-0000-0000-000000000611',
      '20000000-0000-0000-0000-000000000501',
      '20000000-0000-0000-0000-000000000207'
    )$$,
  '23514',
  'sealed'
);

UPDATE export_batches
SET status = 'approved', approved_at = '2026-07-18T08:01:00Z'
WHERE id = '20000000-0000-0000-0000-000000000501';

UPDATE export_batches
SET status = 'generated',
    generated_at = '2026-07-18T08:02:00Z',
    export_file_uri = 'validation://synthetic/authorized-fields.xml',
    export_file_hash = repeat('e', 64)
WHERE id = '20000000-0000-0000-0000-000000000501';

SELECT validation.assert_true(
  (SELECT count(*) = 3
       AND array_agg(field_name ORDER BY field_name)
           = ARRAY['actual_finish', 'actual_start', 'percent_complete']
   FROM export_batch_lines
   WHERE export_batch_id = '20000000-0000-0000-0000-000000000501'
     AND is_export_eligible),
  'Only the three authorized fields may remain eligible for worker handoff'
);

SELECT validation.create_batch('20000000-0000-0000-0000-000000000502');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000612', '20000000-0000-0000-0000-000000000502', '20000000-0000-0000-0000-000000000204');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000613', '20000000-0000-0000-0000-000000000502', '20000000-0000-0000-0000-000000000205');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000614', '20000000-0000-0000-0000-000000000502', '20000000-0000-0000-0000-000000000206');

UPDATE export_batches
SET line_set_sealed = true
WHERE id = '20000000-0000-0000-0000-000000000502';

SELECT validation.assert_true(
  (SELECT status = 'draft_preview' AND line_set_sealed
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000502'),
  'A stable all-ineligible preview must remain readable and seal successfully'
);

SELECT validation.expect_failure(
  'all-ineligible batch approval',
  $$UPDATE export_batches
    SET status = 'approved', approved_at = '2026-07-18T08:03:00Z'
    WHERE id = '20000000-0000-0000-0000-000000000502'$$,
  '23514',
  'eligible line'
);

SELECT validation.expect_failure(
  'all-ineligible generation-time validation',
  $$SELECT validate_current_export_batch_integrity(
      '20000000-0000-0000-0000-000000000502',
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000004',
      true
    )$$,
  '23514',
  'eligible line'
);

-- Stale identity/state cases, including false-to-false changes on all-ineligible batches.
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000220', '20000000-0000-0000-0000-000000000101', 'percent_complete', '30', 'Same-state drift');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000221', '20000000-0000-0000-0000-000000000101', 'actual_start', '2026-07-18T07:00Z', 'Approval drift before approval');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000222', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T18:00+08', 'Approval drift before generation');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000223', '20000000-0000-0000-0000-000000000101', 'physical_percent_complete', '70', 'Physical false-to-false drift');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000224', '20000000-0000-0000-0000-000000000103', 'actual_start', '2026-07-18T08:00Z', 'Summary false-to-false drift');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000225', '20000000-0000-0000-0000-000000000102', 'percent_complete', '35', 'Awaiting false-to-false drift');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000226', '20000000-0000-0000-0000-000000000101', 'percent_complete', '40', 'Baseline drift');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000227', '20000000-0000-0000-0000-000000000102', 'actual_finish', '2026-07-18T09:00Z', 'Snapshot drift');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000228', '20000000-0000-0000-0000-000000000102', 'actual_start', '2026-07-18T09:00Z', 'Task identity drift');

SELECT validation.create_approval('20000000-0000-0000-0000-000000000320', '20000000-0000-0000-0000-000000000220', 'approved_for_export', 'Initial approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000321', '20000000-0000-0000-0000-000000000221', 'approved_for_export', 'Initial approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000322', '20000000-0000-0000-0000-000000000222', 'approved_for_export', 'Initial approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000323', '20000000-0000-0000-0000-000000000223', 'approved_for_export', 'Initial physical approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000324', '20000000-0000-0000-0000-000000000224', 'approved_for_export', 'Initial summary approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000325', '20000000-0000-0000-0000-000000000225', 'awaiting_review', 'Initial awaiting state');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000326', '20000000-0000-0000-0000-000000000226', 'approved_for_export', 'Initial approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000327', '20000000-0000-0000-0000-000000000227', 'approved_for_export', 'Initial approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000328', '20000000-0000-0000-0000-000000000228', 'approved_for_export', 'Initial approval');

SELECT validation.create_batch('20000000-0000-0000-0000-000000000520');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000521');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000522');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000523');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000524');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000525');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000526');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000527');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000528');

SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000620', '20000000-0000-0000-0000-000000000520', '20000000-0000-0000-0000-000000000220');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000621', '20000000-0000-0000-0000-000000000521', '20000000-0000-0000-0000-000000000221');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000622', '20000000-0000-0000-0000-000000000522', '20000000-0000-0000-0000-000000000222');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000623', '20000000-0000-0000-0000-000000000523', '20000000-0000-0000-0000-000000000223');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000624', '20000000-0000-0000-0000-000000000524', '20000000-0000-0000-0000-000000000224');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000625', '20000000-0000-0000-0000-000000000525', '20000000-0000-0000-0000-000000000225');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000626', '20000000-0000-0000-0000-000000000526', '20000000-0000-0000-0000-000000000226');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000627', '20000000-0000-0000-0000-000000000527', '20000000-0000-0000-0000-000000000227');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000628', '20000000-0000-0000-0000-000000000528', '20000000-0000-0000-0000-000000000228');

SELECT validation.create_approval('20000000-0000-0000-0000-000000000330', '20000000-0000-0000-0000-000000000220', 'approved_for_export', 'Same-state newer event');
SELECT validation.expect_failure('same-state approval identity drift at sealing', $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000520'$$, '23514', 'fresh export preview');

UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000521';
SELECT validation.create_approval('20000000-0000-0000-0000-000000000331', '20000000-0000-0000-0000-000000000221', 'rejected', 'Rejected after sealing');
SELECT validation.expect_failure('approval drift at batch approval', $$UPDATE export_batches SET status = 'approved', approved_at = '2026-07-18T08:10:00Z' WHERE id = '20000000-0000-0000-0000-000000000521'$$, '23514', 'fresh export preview');

UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000522';
UPDATE export_batches SET status = 'approved', approved_at = '2026-07-18T08:10:00Z' WHERE id = '20000000-0000-0000-0000-000000000522';
SELECT validation.create_approval('20000000-0000-0000-0000-000000000332', '20000000-0000-0000-0000-000000000222', 'superseded', 'Superseded after approval');
SELECT validation.expect_failure('approval drift at generation', $$UPDATE export_batches SET status = 'generated', generated_at = '2026-07-18T08:11:00Z', export_file_uri = 'validation://stale.xml', export_file_hash = repeat('f', 64) WHERE id = '20000000-0000-0000-0000-000000000522'$$, '23514', 'fresh export preview');

SELECT validation.create_approval('20000000-0000-0000-0000-000000000333', '20000000-0000-0000-0000-000000000223', 'rejected', 'Physical approved to rejected');
SELECT validation.expect_failure('physical false-to-false approval drift', $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000523'$$, '23514', 'fresh export preview');

SELECT validation.create_approval('20000000-0000-0000-0000-000000000334', '20000000-0000-0000-0000-000000000224', 'superseded', 'Summary approved to superseded');
SELECT validation.expect_failure('summary false-to-false approval drift', $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000524'$$, '23514', 'fresh export preview');

SELECT validation.create_approval('20000000-0000-0000-0000-000000000335', '20000000-0000-0000-0000-000000000225', 'rejected', 'Awaiting to rejected');
SELECT validation.expect_failure('awaiting false-to-false approval drift', $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000525'$$, '23514', 'fresh export preview');

SELECT validation.expect_failure_after(
  'old-value baseline drift',
  $$UPDATE imported_tasks SET percent_complete = 26 WHERE id = '20000000-0000-0000-0000-000000000101'$$,
  $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000526'$$,
  '23514',
  'fresh export preview'
);

SELECT validation.expect_failure_after(
  'accepted snapshot drift',
  $$UPDATE project_snapshots SET status = 'superseded' WHERE id = '20000000-0000-0000-0000-000000000004'$$,
  $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000527'$$,
  '23514',
  'fresh export preview'
);

SELECT validation.expect_failure_after(
  'task identity drift',
  $$UPDATE imported_tasks SET name = 'Changed task identity' WHERE id = '20000000-0000-0000-0000-000000000102'$$,
  $$UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000528'$$,
  '23514',
  'fresh export preview'
);

-- Lifecycle history and metadata provenance are database-owned and append-only.
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000235', '20000000-0000-0000-0000-000000000102', 'percent_complete', '41', 'Lifecycle immutability candidate');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000336', '20000000-0000-0000-0000-000000000235', 'approved_for_export', 'Lifecycle immutability approval');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000529');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000629', '20000000-0000-0000-0000-000000000529', '20000000-0000-0000-0000-000000000235');

SELECT validation.expect_failure(
  'same-state draft metadata rewrite',
  $$UPDATE export_batches
    SET metadata = jsonb_build_object('preview', jsonb_build_object('createdAt', 'client-rewrite'))
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'same-state'
);

SELECT validation.expect_failure(
  'line sealing with unrelated lifecycle mutation',
  $$UPDATE export_batches
    SET line_set_sealed = true,
        approved_by_user_id = '20000000-0000-0000-0000-000000000901'
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'without any unrelated mutation'
);

UPDATE export_batches
SET line_set_sealed = true
WHERE id = '20000000-0000-0000-0000-000000000529';

UPDATE export_batches
SET status = 'approved',
    approved_at = '1999-01-01T00:00:00Z',
    approved_by_user_id = '20000000-0000-0000-0000-000000000901',
    metadata = jsonb_build_object(
      'reason', 'Approved after explicit review',
      'clientMetadata', jsonb_build_object(
        'approvedAt', 'client-collision',
        'approvedByUserId', 'client-collision',
        'preview', 'client-collision'
      )
    )
WHERE id = '20000000-0000-0000-0000-000000000529';

SELECT validation.assert_true(
  (SELECT approved_at <> '1999-01-01T00:00:00Z'::timestamptz
       AND approved_by_user_id = '20000000-0000-0000-0000-000000000901'
       AND metadata #>> '{approval,approvedByUserId}' = '20000000-0000-0000-0000-000000000901'
       AND metadata #>> '{approval,clientMetadata,approvedAt}' = 'client-collision'
       AND metadata #>> '{approval,clientMetadata,approvedByUserId}' = 'client-collision'
       AND metadata #>> '{approval,clientMetadata,preview}' = 'client-collision'
       AND metadata ? 'preview'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000529'),
  'Approval must preserve authoritative columns and nest colliding client metadata'
);

SELECT validation.expect_failure(
  'approval actor and time same-state rewrite',
  $$UPDATE export_batches
    SET approved_at = approved_at + interval '1 second',
        approved_by_user_id = '20000000-0000-0000-0000-000000000902'
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'same-state'
);

SELECT validation.expect_failure(
  'approval metadata section replacement',
  $$UPDATE export_batches
    SET metadata = jsonb_build_object(
      'approval', jsonb_build_object('approvedByUserId', 'client-replacement')
    )
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'same-state'
);

SELECT validation.expect_failure(
  'approval rewrite piggybacked on generated transition',
  $$UPDATE export_batches
    SET status = 'generated',
        approved_at = approved_at + interval '1 second',
        generated_at = '1999-01-01T00:00:00Z',
        generated_by_user_id = '20000000-0000-0000-0000-000000000903',
        export_file_uri = 'validation://synthetic/piggyback.xml',
        export_file_hash = repeat('a', 64),
        metadata = jsonb_build_object('clientMetadata', jsonb_build_object('attempt', 'piggyback'))
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'approval facts'
);

UPDATE export_batches
SET status = 'generated',
    generated_at = '1999-01-01T00:00:00Z',
    generated_by_user_id = '20000000-0000-0000-0000-000000000903',
    export_file_uri = 'validation://synthetic/lifecycle.xml',
    export_file_hash = repeat('b', 64),
    metadata = jsonb_build_object(
      'reason', 'Worker artifact persisted',
      'clientMetadata', jsonb_build_object(
        'generatedAt', 'client-collision',
        'exportFileUri', 'client-collision',
        'provenance', 'client-collision'
      ),
      'provenance', jsonb_build_object(
        'worker', 'python-worker',
        'artifactDigestSource', 'server-storage'
      )
    )
WHERE id = '20000000-0000-0000-0000-000000000529';

SELECT validation.assert_true(
  (SELECT generated_at <> '1999-01-01T00:00:00Z'::timestamptz
       AND generated_by_user_id = '20000000-0000-0000-0000-000000000903'
       AND export_file_uri = 'validation://synthetic/lifecycle.xml'
       AND export_file_hash = repeat('b', 64)
       AND metadata #>> '{generation,generatedByUserId}' = '20000000-0000-0000-0000-000000000903'
       AND metadata #>> '{generation,clientMetadata,generatedAt}' = 'client-collision'
       AND metadata #>> '{generation,clientMetadata,exportFileUri}' = 'client-collision'
       AND metadata #>> '{generation,clientMetadata,provenance}' = 'client-collision'
       AND metadata #>> '{generation,provenance,worker}' = 'python-worker'
       AND metadata #>> '{generation,provenance,artifactDigestSource}' = 'server-storage'
       AND metadata ? 'approval'
       AND metadata ? 'preview'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000529'),
  'Generation must preserve authoritative artifact facts, server provenance, and earlier lifecycle sections'
);

SELECT validation.expect_failure(
  'generated URI hash actor and time rewrite',
  $$UPDATE export_batches
    SET generated_at = generated_at + interval '1 second',
        generated_by_user_id = '20000000-0000-0000-0000-000000000904',
        export_file_uri = 'validation://synthetic/rewrite.xml',
        export_file_hash = repeat('c', 64)
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'same-state'
);

UPDATE export_batches
SET status = 'opened_in_microsoft_project',
    opened_in_microsoft_project_at = '1999-01-01T00:00:00Z',
    opened_in_microsoft_project_by_user_id = '20000000-0000-0000-0000-000000000905',
    metadata = jsonb_build_object(
      'reason', 'Opened manually in Microsoft Project',
      'clientMetadata', jsonb_build_object(
        'openedAt', 'client-collision',
        'openedByUserId', 'client-collision',
        'generation', 'client-collision'
      )
    )
WHERE id = '20000000-0000-0000-0000-000000000529';

SELECT validation.assert_true(
  (SELECT opened_in_microsoft_project_at <> '1999-01-01T00:00:00Z'::timestamptz
       AND opened_in_microsoft_project_at >= generated_at
       AND opened_in_microsoft_project_by_user_id = '20000000-0000-0000-0000-000000000905'
       AND metadata #>> '{microsoftProjectOpen,openedByUserId}' = '20000000-0000-0000-0000-000000000905'
       AND metadata #>> '{microsoftProjectOpen,clientMetadata,openedAt}' = 'client-collision'
       AND metadata #>> '{microsoftProjectOpen,clientMetadata,openedByUserId}' = 'client-collision'
       AND metadata #>> '{microsoftProjectOpen,clientMetadata,generation}' = 'client-collision'
       AND metadata ? 'generation'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000529'),
  'Microsoft Project open must use authoritative columns without replacing generation provenance'
);

SELECT validation.expect_failure(
  'Microsoft Project open actor and time rewrite',
  $$UPDATE export_batches
    SET opened_in_microsoft_project_at = opened_in_microsoft_project_at + interval '1 second',
        opened_in_microsoft_project_by_user_id = '20000000-0000-0000-0000-000000000906'
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'same-state'
);

UPDATE export_batches
SET status = 'verified',
    verified_at = '1999-01-01T00:00:00Z',
    verified_by_user_id = '20000000-0000-0000-0000-000000000907',
    metadata = jsonb_build_object(
      'reason', 'Manual round-trip verification passed',
      'clientMetadata', jsonb_build_object(
        'verifiedAt', 'client-collision',
        'verifiedByUserId', 'client-collision',
        'microsoftProjectOpen', 'client-collision'
      )
    )
WHERE id = '20000000-0000-0000-0000-000000000529';

SELECT validation.assert_true(
  (SELECT verified_at <> '1999-01-01T00:00:00Z'::timestamptz
       AND verified_at >= opened_in_microsoft_project_at
       AND verified_by_user_id = '20000000-0000-0000-0000-000000000907'
       AND metadata #>> '{verification,verifiedByUserId}' = '20000000-0000-0000-0000-000000000907'
       AND metadata #>> '{verification,clientMetadata,verifiedAt}' = 'client-collision'
       AND metadata #>> '{verification,clientMetadata,verifiedByUserId}' = 'client-collision'
       AND metadata #>> '{verification,clientMetadata,microsoftProjectOpen}' = 'client-collision'
       AND metadata ? 'approval'
       AND metadata ? 'generation'
       AND metadata ? 'microsoftProjectOpen'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000529'),
  'Verification must use authoritative columns and retain the full lifecycle provenance chain'
);

SELECT validation.expect_failure(
  'verification actor and time rewrite',
  $$UPDATE export_batches
    SET verified_at = verified_at + interval '1 second',
        verified_by_user_id = '20000000-0000-0000-0000-000000000908'
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'same-state'
);

SELECT validation.expect_failure(
  'verified terminal lifecycle transition',
  $$UPDATE export_batches
    SET status = 'failed', failure_reason = 'Must remain verified'
    WHERE id = '20000000-0000-0000-0000-000000000529'$$,
  '23514',
  'invalid current-policy export batch lifecycle transition'
);

SELECT validation.create_batch('20000000-0000-0000-0000-000000000530');
UPDATE export_batches
SET status = 'failed',
    failure_reason = 'Synthetic controlled failure',
    metadata = jsonb_build_object(
      'clientMetadata', jsonb_build_object('failureReason', 'client-collision')
    )
WHERE id = '20000000-0000-0000-0000-000000000530';

SELECT validation.assert_true(
  (SELECT failure_reason = 'Synthetic controlled failure'
       AND metadata #>> '{failure,failureReason}' = 'Synthetic controlled failure'
       AND metadata #>> '{failure,clientMetadata,failureReason}' = 'client-collision'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000530'),
  'Failure reason must remain authoritative while colliding caller metadata is nested'
);

SELECT validation.expect_failure(
  'failed terminal reason rewrite',
  $$UPDATE export_batches
    SET failure_reason = 'Rewritten failure', metadata = '{}'::jsonb
    WHERE id = '20000000-0000-0000-0000-000000000530'$$,
  '23514',
  'same-state'
);

SELECT validation.create_batch('20000000-0000-0000-0000-000000000531');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000532');
UPDATE export_batches
SET status = 'superseded',
    superseded_by_export_batch_id = '20000000-0000-0000-0000-000000000532',
    metadata = jsonb_build_object(
      'clientMetadata', jsonb_build_object('supersededByExportBatchId', 'client-collision')
    )
WHERE id = '20000000-0000-0000-0000-000000000531';

SELECT validation.assert_true(
  (SELECT superseded_by_export_batch_id = '20000000-0000-0000-0000-000000000532'
       AND metadata #>> '{supersession,supersededByExportBatchId}' = '20000000-0000-0000-0000-000000000532'
       AND metadata #>> '{supersession,clientMetadata,supersededByExportBatchId}' = 'client-collision'
   FROM export_batches
   WHERE id = '20000000-0000-0000-0000-000000000531'),
  'Superseding batch identity must remain authoritative while colliding caller metadata is nested'
);

SELECT validation.expect_failure(
  'superseded terminal identity rewrite',
  $$UPDATE export_batches
    SET superseded_by_export_batch_id = '20000000-0000-0000-0000-000000000530'
    WHERE id = '20000000-0000-0000-0000-000000000531'$$,
  '23514',
  'same-state'
);

-- Concurrency fixtures. The runner exercises these in separate sessions.
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000240', '20000000-0000-0000-0000-000000000101', 'actual_finish', '2026-07-18T19:00+08', 'Line/seal candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000241', '20000000-0000-0000-0000-000000000102', 'percent_complete', '45', 'Duplicate holder candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000242', '20000000-0000-0000-0000-000000000102', 'percent_complete', '50', 'Duplicate waiter candidate', 'source-v2');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000243', '20000000-0000-0000-0000-000000000101', 'actual_start', '2026-07-18T07:30Z', 'Approval/preview candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000244', '20000000-0000-0000-0000-000000000101', 'percent_complete', '55', 'Approval/batch candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000245', '20000000-0000-0000-0000-000000000102', 'actual_start', '2026-07-18T10:00Z', 'Approval/generation candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000246', '20000000-0000-0000-0000-000000000102', 'actual_finish', '2026-07-18T11:00Z', 'Snapshot/generation candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000247', '20000000-0000-0000-0000-000000000101', 'percent_complete', '60', 'Task/generation candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000248', '20000000-0000-0000-0000-000000000102', 'percent_complete', '65', 'Rollback candidate');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000249', '20000000-0000-0000-0000-000000000101', 'physical_percent_complete', '75', 'Reversed candidate A');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000250', '20000000-0000-0000-0000-000000000102', 'physical_percent_complete', '80', 'Reversed candidate B');
SELECT validation.create_candidate('20000000-0000-0000-0000-000000000251', '20000000-0000-0000-0000-000000000102', 'percent_complete', '70', 'Task mutation versus approval candidate');

SELECT validation.create_approval('20000000-0000-0000-0000-000000000340', '20000000-0000-0000-0000-000000000240', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000341', '20000000-0000-0000-0000-000000000241', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000342', '20000000-0000-0000-0000-000000000242', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000343', '20000000-0000-0000-0000-000000000243', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000344', '20000000-0000-0000-0000-000000000244', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000345', '20000000-0000-0000-0000-000000000245', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000346', '20000000-0000-0000-0000-000000000246', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000347', '20000000-0000-0000-0000-000000000247', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000348', '20000000-0000-0000-0000-000000000248', 'approved_for_export', 'Concurrency approval');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000349', '20000000-0000-0000-0000-000000000249', 'approved_for_export', 'Initial reversed A');
SELECT validation.create_approval('20000000-0000-0000-0000-000000000350', '20000000-0000-0000-0000-000000000250', 'approved_for_export', 'Initial reversed B');

SELECT validation.create_batch('20000000-0000-0000-0000-000000000540');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000541');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000543');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000544');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000545');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000546');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000547');
SELECT validation.create_batch('20000000-0000-0000-0000-000000000548');

SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000644', '20000000-0000-0000-0000-000000000544', '20000000-0000-0000-0000-000000000244');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000645', '20000000-0000-0000-0000-000000000545', '20000000-0000-0000-0000-000000000245');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000646', '20000000-0000-0000-0000-000000000546', '20000000-0000-0000-0000-000000000246');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000647', '20000000-0000-0000-0000-000000000547', '20000000-0000-0000-0000-000000000247');
SELECT validation.insert_candidate_line('20000000-0000-0000-0000-000000000648', '20000000-0000-0000-0000-000000000548', '20000000-0000-0000-0000-000000000248');

UPDATE export_batches SET line_set_sealed = true WHERE id IN (
  '20000000-0000-0000-0000-000000000544',
  '20000000-0000-0000-0000-000000000545',
  '20000000-0000-0000-0000-000000000546',
  '20000000-0000-0000-0000-000000000547',
  '20000000-0000-0000-0000-000000000548'
);

UPDATE export_batches
SET status = 'approved', approved_at = '2026-07-18T08:30:00Z'
WHERE id IN (
  '20000000-0000-0000-0000-000000000545',
  '20000000-0000-0000-0000-000000000546',
  '20000000-0000-0000-0000-000000000547',
  '20000000-0000-0000-0000-000000000548'
);

SELECT validation.assert_true(
  (SELECT bool_and(approval_event_order IS NOT NULL)
       AND count(DISTINCT approval_event_order) = count(*)
   FROM approval_records
   WHERE candidate_binding_policy_version = 1),
  'Current approval-event ordering must be non-null and unique'
);

\echo 'Current policy-1 candidate, approval, line, freshness, and fixture assertions passed.'
