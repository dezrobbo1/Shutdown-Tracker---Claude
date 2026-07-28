-- Grandfather the V006 export/approval history and enforce the current,
-- candidate-bound export integrity policy only for records created after V007.
-- Existing rows receive null policy/capture columns and remain unchanged history.

ALTER TABLE project_snapshots
  ADD CONSTRAINT project_snapshots_id_project_unique
  UNIQUE (id, project_id);

ALTER TABLE imported_tasks
  ADD CONSTRAINT imported_tasks_id_project_snapshot_unique
  UNIQUE (id, project_id, project_snapshot_id);

CREATE OR REPLACE FUNCTION canonical_export_candidate_instant(value TIMESTAMPTZ)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
  base_value TEXT;
  microseconds TEXT;
BEGIN
  base_value := to_char(value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS');
  microseconds := to_char(value AT TIME ZONE 'UTC', 'US');

  IF microseconds = '000000' THEN
    RETURN base_value || 'Z';
  END IF;

  RETURN base_value || '.' || regexp_replace(microseconds, '0+$', '') || 'Z';
END;
$$;

CREATE OR REPLACE FUNCTION normalize_export_candidate_new_value(
  candidate_field_name TEXT,
  candidate_value TEXT
)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
  trimmed_value TEXT;
  numeric_value NUMERIC;
  local_value TEXT;
  offset_value TEXT;
  fractional_value TEXT;
  offset_hour INTEGER;
  offset_minute INTEGER;
BEGIN
  IF candidate_value IS NULL OR btrim(candidate_value) = '' THEN
    RAISE EXCEPTION 'export candidate new value is required'
      USING ERRCODE = '23514';
  END IF;

  trimmed_value := btrim(candidate_value);

  IF candidate_field_name IN ('percent_complete', 'physical_percent_complete') THEN
    IF trimmed_value !~ '^[0-9]+([.][0-9]+)?$' THEN
      RAISE EXCEPTION 'export candidate percentage must be numeric'
        USING ERRCODE = '23514';
    END IF;

    numeric_value := trimmed_value::NUMERIC;
    IF numeric_value < 0 OR numeric_value > 100 THEN
      RAISE EXCEPTION 'export candidate percentage must be between 0 and 100'
        USING ERRCODE = '23514';
    END IF;

    IF candidate_field_name = 'percent_complete'
       AND numeric_value <> trunc(numeric_value) THEN
      RAISE EXCEPTION 'percent complete must be a whole number between 0 and 100'
        USING ERRCODE = '23514';
    END IF;

    RETURN trim_scale(numeric_value)::TEXT;
  END IF;

  IF candidate_field_name IN ('actual_start', 'actual_finish') THEN
    IF trimmed_value !~
       '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}([.][0-9]{1,6})?)?(Z|[+-][0-9]{2}(:[0-9]{2})?)$' THEN
      RAISE EXCEPTION 'export candidate actual date must be an ISO-8601 offset timestamp'
        USING ERRCODE = '23514';
    END IF;

    IF left(trimmed_value, 4) = '0000' THEN
      RAISE EXCEPTION 'export candidate actual date must use a positive four-digit year'
        USING ERRCODE = '23514';
    END IF;

    IF right(trimmed_value, 1) = 'Z' THEN
      offset_value := 'Z';
      local_value := left(trimmed_value, length(trimmed_value) - 1);
    ELSE
      offset_value := substring(trimmed_value FROM '[+-][0-9]{2}:[0-9]{2}$');
      IF offset_value IS NULL THEN
        offset_value := substring(trimmed_value FROM '[+-][0-9]{2}$');
      END IF;
      local_value := left(trimmed_value, length(trimmed_value) - length(offset_value));

      offset_hour := substring(offset_value FROM 2 FOR 2)::INTEGER;
      offset_minute := CASE
        WHEN length(offset_value) = 3 THEN 0
        ELSE right(offset_value, 2)::INTEGER
      END;
      IF offset_hour > 18 OR offset_minute > 59
         OR (offset_hour = 18 AND offset_minute <> 0) THEN
        RAISE EXCEPTION 'export candidate actual date has an unsupported UTC offset'
          USING ERRCODE = '23514';
      END IF;
      IF length(offset_value) = 3 THEN
        offset_value := offset_value || ':00';
      END IF;
      IF offset_hour = 0 AND offset_minute = 0 THEN
        offset_value := 'Z';
      END IF;
    END IF;

    IF strpos(local_value, '.') > 0 THEN
      fractional_value := substring(local_value FROM strpos(local_value, '.') + 1);
      IF fractional_value !~ '^0+$' THEN
        RAISE EXCEPTION 'export candidate actual dates support whole-second precision'
          USING ERRCODE = '23514';
      END IF;
      local_value := left(local_value, strpos(local_value, '.') - 1);
    END IF;

    IF substring(local_value FROM 12 FOR 2)::INTEGER > 23
       OR substring(local_value FROM 15 FOR 2)::INTEGER > 59
       OR (
         length(local_value) = 19
         AND substring(local_value FROM 18 FOR 2)::INTEGER > 59
       ) THEN
      RAISE EXCEPTION 'export candidate actual date must be a valid ISO-8601 offset timestamp'
        USING ERRCODE = '23514';
    END IF;

    BEGIN
      -- Validate the reviewed wall-clock value independently of PostgreSQL's
      -- narrower time-zone-offset parser; offset bounds are checked above to
      -- match java.time.ZoneOffset (through +/-18:00).
      PERFORM local_value::TIMESTAMP;
    EXCEPTION
      WHEN OTHERS THEN
        RAISE EXCEPTION 'export candidate actual date must be a valid ISO-8601 offset timestamp'
          USING ERRCODE = '23514';
    END;

    IF length(local_value) = 16 THEN
      local_value := local_value || ':00';
    END IF;

    RETURN local_value || offset_value;
  END IF;

  RAISE EXCEPTION 'unsupported export candidate field: %', candidate_field_name
    USING ERRCODE = '23514';
END;
$$;

CREATE OR REPLACE FUNCTION calculate_export_candidate_fingerprint(
  candidate_binding_policy_version INTEGER,
  candidate_project_id UUID,
  candidate_project_snapshot_id UUID,
  candidate_imported_task_id UUID,
  candidate_source_entity_type TEXT,
  candidate_source_entity_id UUID,
  candidate_source_version TEXT,
  candidate_field_name TEXT,
  candidate_normalized_old_value TEXT,
  candidate_normalized_new_value TEXT,
  candidate_task_external_uid TEXT,
  candidate_task_external_id TEXT,
  candidate_task_name TEXT,
  candidate_is_leaf_task BOOLEAN,
  candidate_source_actor_user_id UUID,
  candidate_source_timestamp TIMESTAMPTZ,
  candidate_reason TEXT,
  candidate_metadata JSONB
)
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
  SELECT encode(
    digest(
      convert_to(
        jsonb_build_object(
          'bindingPolicyVersion', candidate_binding_policy_version,
          'projectId', candidate_project_id,
          'projectSnapshotId', candidate_project_snapshot_id,
          'importedTaskId', candidate_imported_task_id,
          'sourceEntityType', candidate_source_entity_type,
          'sourceEntityId', candidate_source_entity_id,
          'sourceVersion', candidate_source_version,
          'fieldName', candidate_field_name,
          'normalizedOldValue', candidate_normalized_old_value,
          'normalizedNewValue', candidate_normalized_new_value,
          'capturedTaskExternalUid', candidate_task_external_uid,
          'capturedTaskExternalId', candidate_task_external_id,
          'capturedTaskName', candidate_task_name,
          'capturedIsLeafTask', candidate_is_leaf_task,
          'sourceActorUserId', candidate_source_actor_user_id,
          'sourceTimestamp', canonical_export_candidate_instant(candidate_source_timestamp),
          'reason', candidate_reason,
          'metadata', candidate_metadata
        )::TEXT,
        'UTF8'
      ),
      'sha256'
    ),
    'hex'
  );
$$;

CREATE TABLE export_candidate_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  binding_policy_version INTEGER NOT NULL DEFAULT 1,
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL,
  imported_task_id UUID NOT NULL,
  source_entity_type TEXT NOT NULL,
  source_entity_id UUID NOT NULL,
  source_version TEXT NOT NULL,
  field_name TEXT NOT NULL,
  normalized_old_value TEXT,
  normalized_new_value TEXT NOT NULL,
  source_event_or_payload_hash TEXT NOT NULL,
  captured_task_external_uid TEXT NOT NULL,
  captured_task_external_id TEXT NOT NULL,
  captured_task_name TEXT NOT NULL,
  captured_is_leaf_task BOOLEAN NOT NULL,
  source_actor_user_id UUID,
  source_timestamp TIMESTAMPTZ,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT export_candidate_records_policy_check
    CHECK (binding_policy_version = 1),
  CONSTRAINT export_candidate_records_field_check
    CHECK (
      field_name IN (
        'percent_complete',
        'physical_percent_complete',
        'actual_start',
        'actual_finish'
      )
    ),
  CONSTRAINT export_candidate_records_source_type_check
    CHECK (source_entity_type = btrim(source_entity_type) AND source_entity_type <> ''),
  CONSTRAINT export_candidate_records_source_version_check
    CHECK (source_version = btrim(source_version) AND source_version <> ''),
  CONSTRAINT export_candidate_records_source_hash_check
    CHECK (source_event_or_payload_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT export_candidate_records_metadata_object_check
    CHECK (jsonb_typeof(metadata) = 'object'),
  CONSTRAINT export_candidate_records_snapshot_fkey
    FOREIGN KEY (project_snapshot_id, project_id)
    REFERENCES project_snapshots (id, project_id),
  CONSTRAINT export_candidate_records_task_fkey
    FOREIGN KEY (imported_task_id, project_id, project_snapshot_id)
    REFERENCES imported_tasks (id, project_id, project_snapshot_id),
  CONSTRAINT export_candidate_records_id_policy_unique
    UNIQUE (id, binding_policy_version),
  CONSTRAINT export_candidate_records_id_policy_project_unique
    UNIQUE (id, binding_policy_version, project_id)
);

CREATE INDEX export_candidate_records_project_snapshot
  ON export_candidate_records (project_id, project_snapshot_id);

CREATE INDEX export_candidate_records_task_field
  ON export_candidate_records (imported_task_id, field_name);

CREATE INDEX export_candidate_records_source
  ON export_candidate_records (
    project_id,
    source_entity_type,
    source_entity_id,
    source_version
  );

CREATE OR REPLACE FUNCTION prepare_export_candidate_record()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  task_record RECORD;
BEGIN
  IF NEW.binding_policy_version IS DISTINCT FROM 1 THEN
    RAISE EXCEPTION 'new export candidate records require integrity policy version 1'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.source_entity_type IS NULL
     OR NEW.source_entity_type = ''
     OR NEW.source_entity_type IS DISTINCT FROM btrim(NEW.source_entity_type)
     OR NEW.source_version IS NULL
     OR NEW.source_version = ''
     OR NEW.source_version IS DISTINCT FROM btrim(NEW.source_version) THEN
    RAISE EXCEPTION 'export candidate source type and source version must be nonblank exact values'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.field_name NOT IN (
    'percent_complete',
    'physical_percent_complete',
    'actual_start',
    'actual_finish'
  ) THEN
    RAISE EXCEPTION 'unsupported export candidate field: %', NEW.field_name
      USING ERRCODE = '23514';
  END IF;

  SELECT it.external_uid,
         it.external_id,
         it.name,
         it.is_summary,
         it.percent_complete,
         it.physical_percent_complete,
         it.actual_start,
         it.actual_finish
    INTO task_record
    FROM project_snapshots ps
    JOIN imported_tasks it
      ON it.project_snapshot_id = ps.id
     AND it.project_id = ps.project_id
    WHERE ps.id = NEW.project_snapshot_id
      AND ps.project_id = NEW.project_id
      AND ps.status = 'accepted'::project_snapshot_status
      AND it.id = NEW.imported_task_id
    FOR SHARE OF ps, it;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'export candidate requires an accepted snapshot and matching imported task'
      USING ERRCODE = '23514';
  END IF;

  IF task_record.external_uid IS NULL
     OR task_record.external_uid IS DISTINCT FROM btrim(task_record.external_uid)
     OR task_record.external_uid !~ '^[1-9][0-9]*$'
     OR length(task_record.external_uid) > 10 THEN
    RAISE EXCEPTION 'export candidate requires a canonical positive 32-bit Microsoft Project task UID'
      USING ERRCODE = '23514';
  END IF;
  IF task_record.external_uid::BIGINT > 2147483647 THEN
    RAISE EXCEPTION 'export candidate requires a canonical positive 32-bit Microsoft Project task UID'
      USING ERRCODE = '23514';
  END IF;

  IF task_record.external_id IS NULL
     OR task_record.external_id IS DISTINCT FROM btrim(task_record.external_id)
     OR task_record.external_id !~ '^[1-9][0-9]*$'
     OR length(task_record.external_id) > 10 THEN
    RAISE EXCEPTION 'export candidate requires a canonical positive 32-bit Microsoft Project task ID'
      USING ERRCODE = '23514';
  END IF;
  IF task_record.external_id::BIGINT > 2147483647 THEN
    RAISE EXCEPTION 'export candidate requires a canonical positive 32-bit Microsoft Project task ID'
      USING ERRCODE = '23514';
  END IF;

  IF task_record.name IS NULL OR btrim(task_record.name) = '' THEN
    RAISE EXCEPTION 'export candidate requires a nonblank imported task name'
      USING ERRCODE = '23514';
  END IF;

  NEW.captured_task_external_uid := task_record.external_uid;
  NEW.captured_task_external_id := task_record.external_id;
  NEW.captured_task_name := task_record.name;
  NEW.captured_is_leaf_task := NOT task_record.is_summary;

  NEW.normalized_old_value := CASE NEW.field_name
    WHEN 'percent_complete' THEN
      CASE WHEN task_record.percent_complete IS NULL
        THEN NULL ELSE trim_scale(task_record.percent_complete)::TEXT END
    WHEN 'physical_percent_complete' THEN
      CASE WHEN task_record.physical_percent_complete IS NULL
        THEN NULL ELSE trim_scale(task_record.physical_percent_complete)::TEXT END
    WHEN 'actual_start' THEN canonical_export_candidate_instant(task_record.actual_start)
    WHEN 'actual_finish' THEN canonical_export_candidate_instant(task_record.actual_finish)
  END;

  NEW.normalized_new_value := normalize_export_candidate_new_value(
    NEW.field_name,
    NEW.normalized_new_value
  );

  NEW.source_event_or_payload_hash := calculate_export_candidate_fingerprint(
    NEW.binding_policy_version,
    NEW.project_id,
    NEW.project_snapshot_id,
    NEW.imported_task_id,
    NEW.source_entity_type,
    NEW.source_entity_id,
    NEW.source_version,
    NEW.field_name,
    NEW.normalized_old_value,
    NEW.normalized_new_value,
    NEW.captured_task_external_uid,
    NEW.captured_task_external_id,
    NEW.captured_task_name,
    NEW.captured_is_leaf_task,
    NEW.source_actor_user_id,
    NEW.source_timestamp,
    NEW.reason,
    NEW.metadata
  );

  RETURN NEW;
END;
$$;

CREATE TRIGGER export_candidate_records_prepare
BEFORE INSERT ON export_candidate_records
FOR EACH ROW
EXECUTE FUNCTION prepare_export_candidate_record();

CREATE OR REPLACE FUNCTION freeze_export_candidate_record_history()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'export candidate records are append-only; create a new reviewed candidate'
    USING ERRCODE = '23514';
  RETURN NULL;
END;
$$;

CREATE TRIGGER export_candidate_records_freeze_history
BEFORE UPDATE OR DELETE ON export_candidate_records
FOR EACH ROW
EXECUTE FUNCTION freeze_export_candidate_record_history();

ALTER TABLE approval_records
  ADD COLUMN approval_event_order BIGINT,
  ADD COLUMN authoritative_export_candidate_id UUID,
  ADD COLUMN candidate_binding_policy_version INTEGER;

ALTER TABLE approval_records
  ALTER COLUMN candidate_binding_policy_version SET DEFAULT 1;

CREATE SEQUENCE approval_records_event_order_seq AS BIGINT;

ALTER SEQUENCE approval_records_event_order_seq
  OWNED BY approval_records.approval_event_order;

ALTER TABLE approval_records
  ADD CONSTRAINT approval_records_event_order_positive_check
    CHECK (approval_event_order IS NULL OR approval_event_order > 0),
  ADD CONSTRAINT approval_records_candidate_binding_pair_check
    CHECK (
      (authoritative_export_candidate_id IS NULL AND candidate_binding_policy_version IS NULL)
      OR (
        authoritative_export_candidate_id IS NOT NULL
        AND candidate_binding_policy_version = 1
      )
    ),
  ADD CONSTRAINT approval_records_candidate_fkey
    FOREIGN KEY (
      authoritative_export_candidate_id,
      candidate_binding_policy_version,
      project_id
    )
    REFERENCES export_candidate_records (id, binding_policy_version, project_id),
  ADD CONSTRAINT approval_records_candidate_capture_unique
    UNIQUE (
      id,
      authoritative_export_candidate_id,
      candidate_binding_policy_version,
      project_id,
      approval_state
    );

CREATE UNIQUE INDEX approval_records_event_order_unique
  ON approval_records (approval_event_order)
  WHERE approval_event_order IS NOT NULL;

CREATE INDEX approval_records_source_event_order
  ON approval_records (
    project_id,
    source_entity_type,
    source_entity_id,
    approval_event_order DESC
  )
  WHERE approval_event_order IS NOT NULL;

CREATE INDEX approval_records_candidate_event_order
  ON approval_records (
    project_id,
    authoritative_export_candidate_id,
    approval_event_order DESC
  )
  WHERE authoritative_export_candidate_id IS NOT NULL;

CREATE OR REPLACE FUNCTION freeze_approval_record_history()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'approval records are append-only; create a new approval event'
    USING ERRCODE = '23514';
  RETURN NULL;
END;
$$;

CREATE TRIGGER approval_records_freeze_history
BEFORE UPDATE OR DELETE ON approval_records
FOR EACH ROW
EXECUTE FUNCTION freeze_approval_record_history();

ALTER TABLE export_batches
  ADD COLUMN integrity_policy_version INTEGER,
  ADD COLUMN line_set_sealed BOOLEAN;

ALTER TABLE export_batches
  ALTER COLUMN integrity_policy_version SET DEFAULT 1,
  ALTER COLUMN line_set_sealed SET DEFAULT false;

ALTER TABLE export_batches
  ADD CONSTRAINT export_batches_integrity_policy_version_check
    CHECK (integrity_policy_version IS NULL OR integrity_policy_version = 1),
  ADD CONSTRAINT export_batches_policy_capture_check
    CHECK (
      (integrity_policy_version IS NULL AND line_set_sealed IS NULL)
      OR (integrity_policy_version = 1 AND line_set_sealed IS NOT NULL)
    ),
  ADD CONSTRAINT export_batches_line_identity_unique
    UNIQUE (id, project_id, project_snapshot_id, integrity_policy_version);

ALTER TABLE export_batch_lines
  ADD COLUMN integrity_policy_version INTEGER,
  ADD COLUMN captured_approval_record_id UUID,
  ADD COLUMN captured_approval_state approval_state,
  ADD COLUMN authoritative_export_candidate_id UUID,
  ADD COLUMN captured_source_event_or_payload_hash TEXT,
  ADD COLUMN captured_source_version TEXT,
  ADD COLUMN captured_task_external_uid TEXT,
  ADD COLUMN captured_task_external_id TEXT,
  ADD COLUMN captured_task_name TEXT;

ALTER TABLE export_batch_lines
  ALTER COLUMN integrity_policy_version SET DEFAULT 1;

ALTER TABLE export_batch_lines
  ADD CONSTRAINT export_batch_lines_integrity_policy_version_check
    CHECK (integrity_policy_version IS NULL OR integrity_policy_version = 1),
  ADD CONSTRAINT export_batch_lines_current_candidate_capture_check
    CHECK (
      integrity_policy_version IS NULL
      OR (
        authoritative_export_candidate_id IS NOT NULL
        AND captured_approval_record_id IS NOT NULL
        AND captured_approval_state IS NOT NULL
        AND captured_source_event_or_payload_hash ~ '^[0-9a-f]{64}$'
        AND captured_source_version IS NOT NULL
        AND captured_source_version <> ''
        AND captured_task_external_uid IS NOT NULL
        AND captured_task_external_id IS NOT NULL
        AND captured_task_name IS NOT NULL
        AND field_name IN (
          'percent_complete',
          'physical_percent_complete',
          'actual_start',
          'actual_finish'
        )
      )
    ),
  ADD CONSTRAINT export_batch_lines_current_eligibility_check
    CHECK (
      integrity_policy_version IS NULL
      OR is_export_eligible = false
      OR (
        captured_approval_state = 'approved_for_export'::approval_state
        AND is_leaf_task = true
        AND field_name IN ('percent_complete', 'actual_start', 'actual_finish')
      )
    ),
  ADD CONSTRAINT export_batch_lines_batch_identity_fkey
    FOREIGN KEY (
      export_batch_id,
      project_id,
      project_snapshot_id,
      integrity_policy_version
    )
    REFERENCES export_batches (
      id,
      project_id,
      project_snapshot_id,
      integrity_policy_version
    ),
  ADD CONSTRAINT export_batch_lines_candidate_fkey
    FOREIGN KEY (
      authoritative_export_candidate_id,
      integrity_policy_version,
      project_id
    )
    REFERENCES export_candidate_records (id, binding_policy_version, project_id),
  ADD CONSTRAINT export_batch_lines_captured_approval_fkey
    FOREIGN KEY (
      captured_approval_record_id,
      authoritative_export_candidate_id,
      integrity_policy_version,
      project_id,
      captured_approval_state
    )
    REFERENCES approval_records (
      id,
      authoritative_export_candidate_id,
      candidate_binding_policy_version,
      project_id,
      approval_state
    );

CREATE UNIQUE INDEX export_batch_lines_current_policy_task_field_unique
  ON export_batch_lines (export_batch_id, imported_task_id, field_name)
  WHERE integrity_policy_version = 1;

CREATE UNIQUE INDEX export_batch_lines_current_policy_candidate_unique
  ON export_batch_lines (export_batch_id, authoritative_export_candidate_id)
  WHERE integrity_policy_version = 1;

CREATE INDEX export_batch_lines_authoritative_candidate
  ON export_batch_lines (authoritative_export_candidate_id)
  WHERE authoritative_export_candidate_id IS NOT NULL;

CREATE OR REPLACE FUNCTION validate_current_export_batch_integrity(
  candidate_batch_id UUID,
  candidate_project_id UUID,
  candidate_project_snapshot_id UUID,
  require_eligible_line BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
  line_record RECORD;
  candidate_record RECORD;
  task_record RECORD;
  current_approval RECORD;
  current_old_value TEXT;
  canonical_new_value TEXT;
  expected_hash TEXT;
  expected_eligibility BOOLEAN;
  batch_line_count INTEGER := 0;
  eligible_line_count INTEGER := 0;
BEGIN
  PERFORM ps.id
  FROM project_snapshots ps
  WHERE ps.id = candidate_project_snapshot_id
    AND ps.project_id = candidate_project_id
    AND ps.status = 'accepted'::project_snapshot_status
  FOR SHARE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'current-policy export batch requires an accepted project snapshot; create a fresh export preview'
      USING ERRCODE = '23514';
  END IF;

  PERFORM candidate.id
  FROM export_candidate_records candidate
  JOIN export_batch_lines line
    ON line.authoritative_export_candidate_id = candidate.id
   AND line.integrity_policy_version = candidate.binding_policy_version
  WHERE line.export_batch_id = candidate_batch_id
  ORDER BY candidate.id
  FOR SHARE OF candidate;

  PERFORM task.id
  FROM imported_tasks task
  JOIN export_batch_lines line ON line.imported_task_id = task.id
  WHERE line.export_batch_id = candidate_batch_id
  ORDER BY task.id
  FOR SHARE OF task;

  PERFORM approval.id
  FROM approval_records approval
  JOIN (
    SELECT candidate.id AS candidate_id,
           max(candidate_approval.approval_event_order) AS approval_event_order
    FROM export_candidate_records candidate
    JOIN export_batch_lines line
      ON line.authoritative_export_candidate_id = candidate.id
     AND line.integrity_policy_version = candidate.binding_policy_version
    JOIN approval_records candidate_approval
      ON candidate_approval.project_id = candidate.project_id
     AND candidate_approval.authoritative_export_candidate_id = candidate.id
     AND candidate_approval.candidate_binding_policy_version = candidate.binding_policy_version
    WHERE line.export_batch_id = candidate_batch_id
    GROUP BY candidate.id
  ) latest
    ON latest.candidate_id = approval.authoritative_export_candidate_id
   AND latest.approval_event_order = approval.approval_event_order
  ORDER BY approval.authoritative_export_candidate_id
  FOR SHARE OF approval;

  FOR line_record IN
    SELECT line.*
    FROM export_batch_lines line
    WHERE line.export_batch_id = candidate_batch_id
    ORDER BY line.imported_task_id, line.field_name, line.id
    FOR SHARE
  LOOP
    batch_line_count := batch_line_count + 1;

    IF line_record.integrity_policy_version IS DISTINCT FROM 1
       OR line_record.project_id IS DISTINCT FROM candidate_project_id
       OR line_record.project_snapshot_id IS DISTINCT FROM candidate_project_snapshot_id THEN
      RAISE EXCEPTION 'current-policy export batch contains a mismatched or legacy line; create a fresh export preview'
        USING ERRCODE = '23514';
    END IF;

    SELECT candidate.*
      INTO candidate_record
      FROM export_candidate_records candidate
      WHERE candidate.id = line_record.authoritative_export_candidate_id
        AND candidate.binding_policy_version = 1;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'current-policy export line has no authoritative candidate; create a fresh export preview'
        USING ERRCODE = '23514';
    END IF;

    SELECT task.external_uid,
           task.external_id,
           task.name,
           task.is_summary,
           task.percent_complete,
           task.physical_percent_complete,
           task.actual_start,
           task.actual_finish
      INTO task_record
      FROM imported_tasks task
      WHERE task.id = candidate_record.imported_task_id
        AND task.project_id = candidate_record.project_id
        AND task.project_snapshot_id = candidate_record.project_snapshot_id;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'authoritative export candidate task is missing; create a fresh export preview'
        USING ERRCODE = '23514';
    END IF;

    current_old_value := CASE candidate_record.field_name
      WHEN 'percent_complete' THEN
        CASE WHEN task_record.percent_complete IS NULL
          THEN NULL ELSE trim_scale(task_record.percent_complete)::TEXT END
      WHEN 'physical_percent_complete' THEN
        CASE WHEN task_record.physical_percent_complete IS NULL
          THEN NULL ELSE trim_scale(task_record.physical_percent_complete)::TEXT END
      WHEN 'actual_start' THEN canonical_export_candidate_instant(task_record.actual_start)
      WHEN 'actual_finish' THEN canonical_export_candidate_instant(task_record.actual_finish)
    END;

    canonical_new_value := normalize_export_candidate_new_value(
      candidate_record.field_name,
      candidate_record.normalized_new_value
    );

    expected_hash := calculate_export_candidate_fingerprint(
      candidate_record.binding_policy_version,
      candidate_record.project_id,
      candidate_record.project_snapshot_id,
      candidate_record.imported_task_id,
      candidate_record.source_entity_type,
      candidate_record.source_entity_id,
      candidate_record.source_version,
      candidate_record.field_name,
      candidate_record.normalized_old_value,
      candidate_record.normalized_new_value,
      candidate_record.captured_task_external_uid,
      candidate_record.captured_task_external_id,
      candidate_record.captured_task_name,
      candidate_record.captured_is_leaf_task,
      candidate_record.source_actor_user_id,
      candidate_record.source_timestamp,
      candidate_record.reason,
      candidate_record.metadata
    );

    SELECT ar.id,
           ar.approval_state,
           ar.authoritative_export_candidate_id,
           ar.candidate_binding_policy_version
      INTO current_approval
      FROM approval_records ar
      WHERE ar.project_id = candidate_record.project_id
        AND ar.authoritative_export_candidate_id = candidate_record.id
        AND ar.candidate_binding_policy_version = 1
      ORDER BY ar.approval_event_order DESC
      LIMIT 1;

    IF NOT FOUND
       OR current_approval.id IS DISTINCT FROM line_record.captured_approval_record_id
       OR current_approval.approval_state IS DISTINCT FROM line_record.captured_approval_state
       OR current_approval.authoritative_export_candidate_id IS DISTINCT FROM candidate_record.id
       OR current_approval.candidate_binding_policy_version IS DISTINCT FROM 1 THEN
      RAISE EXCEPTION 'export candidate approval changed; create a fresh export preview'
        USING ERRCODE = '23514';
    END IF;

    expected_eligibility :=
      current_approval.approval_state = 'approved_for_export'::approval_state
      AND candidate_record.captured_is_leaf_task
      AND candidate_record.field_name IN ('percent_complete', 'actual_start', 'actual_finish');

    IF candidate_record.project_id IS DISTINCT FROM candidate_project_id
       OR candidate_record.project_snapshot_id IS DISTINCT FROM candidate_project_snapshot_id
       OR candidate_record.captured_task_external_uid IS DISTINCT FROM task_record.external_uid
       OR candidate_record.captured_task_external_id IS DISTINCT FROM task_record.external_id
       OR candidate_record.captured_task_name IS DISTINCT FROM task_record.name
       OR candidate_record.captured_is_leaf_task IS DISTINCT FROM (NOT task_record.is_summary)
       OR candidate_record.normalized_old_value IS DISTINCT FROM current_old_value
       OR candidate_record.normalized_new_value IS DISTINCT FROM canonical_new_value
       OR candidate_record.source_event_or_payload_hash IS DISTINCT FROM expected_hash
       OR line_record.project_id IS DISTINCT FROM candidate_record.project_id
       OR line_record.project_snapshot_id IS DISTINCT FROM candidate_record.project_snapshot_id
       OR line_record.imported_task_id IS DISTINCT FROM candidate_record.imported_task_id
       OR line_record.source_entity_type IS DISTINCT FROM candidate_record.source_entity_type
       OR line_record.source_entity_id IS DISTINCT FROM candidate_record.source_entity_id
       OR line_record.field_name IS DISTINCT FROM candidate_record.field_name
       OR line_record.old_value IS DISTINCT FROM candidate_record.normalized_old_value
       OR line_record.new_value IS DISTINCT FROM candidate_record.normalized_new_value
       OR line_record.captured_source_event_or_payload_hash IS DISTINCT FROM candidate_record.source_event_or_payload_hash
       OR line_record.captured_source_version IS DISTINCT FROM candidate_record.source_version
       OR line_record.captured_task_external_uid IS DISTINCT FROM candidate_record.captured_task_external_uid
       OR line_record.captured_task_external_id IS DISTINCT FROM candidate_record.captured_task_external_id
       OR line_record.captured_task_name IS DISTINCT FROM candidate_record.captured_task_name
       OR line_record.source_actor_user_id IS DISTINCT FROM candidate_record.source_actor_user_id
       OR line_record.source_timestamp IS DISTINCT FROM candidate_record.source_timestamp
       OR line_record.reason IS DISTINCT FROM candidate_record.reason
       OR line_record.is_leaf_task IS DISTINCT FROM candidate_record.captured_is_leaf_task
       OR line_record.is_export_eligible IS DISTINCT FROM expected_eligibility
       OR line_record.metadata IS DISTINCT FROM candidate_record.metadata THEN
      RAISE EXCEPTION 'export batch line no longer exactly matches its authoritative candidate and baseline; create a fresh export preview'
        USING ERRCODE = '23514';
    END IF;

    IF expected_eligibility THEN
      eligible_line_count := eligible_line_count + 1;
    END IF;
  END LOOP;

  IF batch_line_count = 0 THEN
    RAISE EXCEPTION 'current-policy export batch must contain at least one validated line before sealing'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM export_batch_lines first_line
    JOIN export_batch_lines second_line
      ON second_line.export_batch_id = first_line.export_batch_id
     AND second_line.imported_task_id <> first_line.imported_task_id
     AND second_line.captured_task_external_uid = first_line.captured_task_external_uid
    WHERE first_line.export_batch_id = candidate_batch_id
      AND first_line.integrity_policy_version = 1
      AND second_line.integrity_policy_version = 1
  ) THEN
    RAISE EXCEPTION 'different imported tasks cannot share a Microsoft Project UID in one export batch'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM export_batch_lines first_line
    JOIN export_batch_lines second_line
      ON second_line.export_batch_id = first_line.export_batch_id
     AND second_line.imported_task_id <> first_line.imported_task_id
     AND second_line.captured_task_external_id = first_line.captured_task_external_id
    WHERE first_line.export_batch_id = candidate_batch_id
      AND first_line.integrity_policy_version = 1
      AND second_line.integrity_policy_version = 1
  ) THEN
    RAISE EXCEPTION 'different imported tasks cannot share a Microsoft Project ID in one export batch'
      USING ERRCODE = '23514';
  END IF;

  IF require_eligible_line AND eligible_line_count = 0 THEN
    RAISE EXCEPTION 'current-policy export batch must contain at least one currently eligible line'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_export_batch_integrity_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.integrity_policy_version IS DISTINCT FROM 1 THEN
      RAISE EXCEPTION 'new export batches require integrity policy version 1'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.status IS DISTINCT FROM 'draft_preview'::export_batch_state
       OR NEW.line_set_sealed IS DISTINCT FROM false THEN
      RAISE EXCEPTION 'new export batches must begin as an unsealed draft preview'
        USING ERRCODE = '23514';
    END IF;
    PERFORM ps.id
    FROM project_snapshots ps
    WHERE ps.id = NEW.project_snapshot_id
      AND ps.project_id = NEW.project_id
      AND ps.status = 'accepted'::project_snapshot_status
    FOR SHARE;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'new export batches require an accepted project snapshot'
        USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  -- The legacy-history trigger supplies the explicit fresh-preview conflict.
  IF OLD.integrity_policy_version IS NULL THEN
    RETURN NEW;
  END IF;

  IF NEW.integrity_policy_version IS DISTINCT FROM OLD.integrity_policy_version
     OR NEW.project_id IS DISTINCT FROM OLD.project_id
     OR NEW.project_snapshot_id IS DISTINCT FROM OLD.project_snapshot_id THEN
    RAISE EXCEPTION 'export batch policy, project, and snapshot identity are immutable'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.line_set_sealed IS DISTINCT FROM OLD.line_set_sealed THEN
    IF NOT (
      OLD.integrity_policy_version = 1
      AND OLD.status = 'draft_preview'::export_batch_state
      AND NEW.status = 'draft_preview'::export_batch_state
      AND OLD.line_set_sealed = false
      AND NEW.line_set_sealed = true
    ) THEN
      RAISE EXCEPTION 'export batch line set may only seal once while in draft preview'
        USING ERRCODE = '23514';
    END IF;
    PERFORM validate_current_export_batch_integrity(
      NEW.id,
      NEW.project_id,
      NEW.project_snapshot_id,
      false
    );
  END IF;

  IF NEW.status IS DISTINCT FROM OLD.status THEN
    IF NOT (
      (OLD.status = 'draft_preview'::export_batch_state AND NEW.status IN (
        'awaiting_approval'::export_batch_state,
        'approved'::export_batch_state,
        'rejected'::export_batch_state,
        'failed'::export_batch_state,
        'superseded'::export_batch_state
      ))
      OR (OLD.status = 'awaiting_approval'::export_batch_state AND NEW.status IN (
        'approved'::export_batch_state,
        'rejected'::export_batch_state,
        'failed'::export_batch_state,
        'superseded'::export_batch_state
      ))
      OR (OLD.status = 'approved'::export_batch_state AND NEW.status IN (
        'generated'::export_batch_state,
        'failed'::export_batch_state,
        'superseded'::export_batch_state
      ))
      OR (OLD.status = 'generated'::export_batch_state AND NEW.status IN (
        'opened_in_microsoft_project'::export_batch_state,
        'failed'::export_batch_state,
        'superseded'::export_batch_state
      ))
      OR (OLD.status = 'opened_in_microsoft_project'::export_batch_state AND NEW.status IN (
        'verified'::export_batch_state,
        'failed'::export_batch_state,
        'superseded'::export_batch_state
      ))
    ) THEN
      RAISE EXCEPTION 'invalid current-policy export batch lifecycle transition'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.status IN ('approved'::export_batch_state, 'generated'::export_batch_state) THEN
      IF NEW.line_set_sealed IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'export batch must have a sealed line set before approval or generation'
          USING ERRCODE = '23514';
      END IF;
      PERFORM validate_current_export_batch_integrity(
        NEW.id,
        NEW.project_id,
        NEW.project_snapshot_id,
        true
      );
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER export_batches_enforce_integrity_policy
BEFORE INSERT OR UPDATE ON export_batches
FOR EACH ROW
EXECUTE FUNCTION enforce_export_batch_integrity_policy();

CREATE OR REPLACE FUNCTION freeze_legacy_export_history()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'export batch history cannot be deleted'
      USING ERRCODE = '23514';
  END IF;

  IF OLD.integrity_policy_version IS NULL THEN
    RAISE EXCEPTION 'legacy export history is read-only; create a fresh export preview'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER export_batches_freeze_legacy_history
BEFORE UPDATE OR DELETE ON export_batches
FOR EACH ROW
EXECUTE FUNCTION freeze_legacy_export_history();

CREATE OR REPLACE FUNCTION enforce_export_batch_line_integrity_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  batch_record RECORD;
  candidate_record RECORD;
  current_approval RECORD;
  expected_eligibility BOOLEAN;
BEGIN
  IF NEW.integrity_policy_version IS DISTINCT FROM 1 THEN
    RAISE EXCEPTION 'new export batch lines require integrity policy version 1'
      USING ERRCODE = '23514';
  END IF;

  SELECT batch.integrity_policy_version,
         batch.status,
         batch.line_set_sealed
    INTO batch_record
    FROM export_batches batch
    WHERE batch.id = NEW.export_batch_id
      AND batch.project_id = NEW.project_id
      AND batch.project_snapshot_id = NEW.project_snapshot_id
    FOR UPDATE;

  IF NOT FOUND OR batch_record.integrity_policy_version IS DISTINCT FROM 1 THEN
    RAISE EXCEPTION 'current-policy lines require a matching current-policy export batch'
      USING ERRCODE = '23514';
  END IF;
  IF batch_record.status IS DISTINCT FROM 'draft_preview'::export_batch_state
     OR batch_record.line_set_sealed IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'export preview lines may be inserted only into an unsealed draft preview'
      USING ERRCODE = '23514';
  END IF;

  SELECT candidate.*
    INTO candidate_record
    FROM export_candidate_records candidate
    WHERE candidate.id = NEW.authoritative_export_candidate_id
      AND candidate.binding_policy_version = 1
    FOR SHARE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'current-policy export lines require an authoritative candidate'
      USING ERRCODE = '23514';
  END IF;

  SELECT ar.id,
         ar.approval_state,
         ar.authoritative_export_candidate_id,
         ar.candidate_binding_policy_version
    INTO current_approval
    FROM approval_records ar
    WHERE ar.project_id = candidate_record.project_id
      AND ar.authoritative_export_candidate_id = candidate_record.id
      AND ar.candidate_binding_policy_version = 1
    ORDER BY ar.approval_event_order DESC
    LIMIT 1
    FOR SHARE;

  IF NOT FOUND
     OR current_approval.id IS DISTINCT FROM NEW.captured_approval_record_id
     OR current_approval.approval_state IS DISTINCT FROM NEW.captured_approval_state
     OR current_approval.authoritative_export_candidate_id IS DISTINCT FROM candidate_record.id
     OR current_approval.candidate_binding_policy_version IS DISTINCT FROM 1 THEN
    RAISE EXCEPTION 'authoritative export candidate approval is missing or changed; create a fresh export preview'
      USING ERRCODE = '23514';
  END IF;

  expected_eligibility :=
    current_approval.approval_state = 'approved_for_export'::approval_state
    AND candidate_record.captured_is_leaf_task
    AND candidate_record.field_name IN ('percent_complete', 'actual_start', 'actual_finish');

  IF NEW.project_id IS DISTINCT FROM candidate_record.project_id
     OR NEW.project_snapshot_id IS DISTINCT FROM candidate_record.project_snapshot_id
     OR NEW.imported_task_id IS DISTINCT FROM candidate_record.imported_task_id
     OR NEW.source_entity_type IS DISTINCT FROM candidate_record.source_entity_type
     OR NEW.source_entity_id IS DISTINCT FROM candidate_record.source_entity_id
     OR NEW.field_name IS DISTINCT FROM candidate_record.field_name
     OR NEW.old_value IS DISTINCT FROM candidate_record.normalized_old_value
     OR NEW.new_value IS DISTINCT FROM candidate_record.normalized_new_value
     OR NEW.captured_source_event_or_payload_hash IS DISTINCT FROM candidate_record.source_event_or_payload_hash
     OR NEW.captured_source_version IS DISTINCT FROM candidate_record.source_version
     OR NEW.captured_task_external_uid IS DISTINCT FROM candidate_record.captured_task_external_uid
     OR NEW.captured_task_external_id IS DISTINCT FROM candidate_record.captured_task_external_id
     OR NEW.captured_task_name IS DISTINCT FROM candidate_record.captured_task_name
     OR NEW.source_actor_user_id IS DISTINCT FROM candidate_record.source_actor_user_id
     OR NEW.source_timestamp IS DISTINCT FROM candidate_record.source_timestamp
     OR NEW.reason IS DISTINCT FROM candidate_record.reason
     OR NEW.is_leaf_task IS DISTINCT FROM candidate_record.captured_is_leaf_task
     OR NEW.is_export_eligible IS DISTINCT FROM expected_eligibility
     OR NEW.metadata IS DISTINCT FROM candidate_record.metadata THEN
    RAISE EXCEPTION 'export batch line must exactly match its authoritative candidate'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM export_batch_lines existing_line
    WHERE existing_line.export_batch_id = NEW.export_batch_id
      AND existing_line.integrity_policy_version = 1
      AND existing_line.imported_task_id <> NEW.imported_task_id
      AND existing_line.captured_task_external_uid = NEW.captured_task_external_uid
  ) THEN
    RAISE EXCEPTION 'different imported tasks cannot share a Microsoft Project UID in one export batch'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM export_batch_lines existing_line
    WHERE existing_line.export_batch_id = NEW.export_batch_id
      AND existing_line.integrity_policy_version = 1
      AND existing_line.imported_task_id <> NEW.imported_task_id
      AND existing_line.captured_task_external_id = NEW.captured_task_external_id
  ) THEN
    RAISE EXCEPTION 'different imported tasks cannot share a Microsoft Project ID in one export batch'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER export_batch_lines_enforce_integrity_policy
BEFORE INSERT ON export_batch_lines
FOR EACH ROW
EXECUTE FUNCTION enforce_export_batch_line_integrity_policy();

CREATE OR REPLACE FUNCTION freeze_export_batch_line_history()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'export preview lines are append-only; create a fresh export preview'
    USING ERRCODE = '23514';
  RETURN NULL;
END;
$$;

CREATE TRIGGER export_batch_lines_freeze_history
BEFORE UPDATE OR DELETE ON export_batch_lines
FOR EACH ROW
EXECUTE FUNCTION freeze_export_batch_line_history();

CREATE OR REPLACE FUNCTION prepare_current_approval_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  candidate_record RECORD;
BEGIN
  IF NEW.approval_event_order IS NOT NULL THEN
    RAISE EXCEPTION 'approval event order is assigned by the database'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.authoritative_export_candidate_id IS NULL
     OR NEW.candidate_binding_policy_version IS DISTINCT FROM 1 THEN
    RAISE EXCEPTION 'new approval events require an authoritative policy-1 export candidate binding'
      USING ERRCODE = '23514';
  END IF;

  -- Serialize approval chronology per project before taking batch/candidate locks.
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.project_id::TEXT, 20260719));

  PERFORM batch.id
  FROM export_batches batch
  JOIN export_batch_lines line ON line.export_batch_id = batch.id
  WHERE batch.project_id = NEW.project_id
    AND line.authoritative_export_candidate_id = NEW.authoritative_export_candidate_id
    AND batch.integrity_policy_version = 1
    AND line.integrity_policy_version = 1
    AND batch.status IN (
      'draft_preview'::export_batch_state,
      'awaiting_approval'::export_batch_state,
      'approved'::export_batch_state
    )
  ORDER BY batch.id
  FOR SHARE OF batch;

  SELECT candidate.*
    INTO candidate_record
    FROM export_candidate_records candidate
    WHERE candidate.id = NEW.authoritative_export_candidate_id
      AND candidate.binding_policy_version = 1
    FOR UPDATE;

  IF NOT FOUND OR candidate_record.project_id IS DISTINCT FROM NEW.project_id THEN
    RAISE EXCEPTION 'approval event requires a matching authoritative export candidate'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.source_entity_type IS DISTINCT FROM 'export_candidate'
     OR NEW.source_entity_id IS DISTINCT FROM candidate_record.id THEN
    RAISE EXCEPTION 'candidate approval event source identity must be the exact export candidate'
      USING ERRCODE = '23514';
  END IF;

  NEW.approval_event_order := nextval('approval_records_event_order_seq'::regclass);
  RETURN NEW;
END;
$$;

CREATE TRIGGER approval_records_prepare_current_event
BEFORE INSERT ON approval_records
FOR EACH ROW
EXECUTE FUNCTION prepare_current_approval_event();

COMMENT ON TABLE export_candidate_records
  IS 'Approval-neutral immutable server-authoritative task/field/value candidates for policy-1 export review.';

COMMENT ON COLUMN export_candidate_records.source_event_or_payload_hash
  IS 'Database-generated lowercase SHA-256 fingerprint of the normalized immutable candidate payload.';

COMMENT ON COLUMN approval_records.approval_event_order
  IS 'Database-assigned monotonic order for candidate-bound approval events created after V007; legacy rows remain null.';

COMMENT ON COLUMN approval_records.authoritative_export_candidate_id
  IS 'Exact policy-1 candidate for every post-V007 approval state; legacy rows remain null.';

COMMENT ON COLUMN export_batches.integrity_policy_version
  IS 'V006 and earlier batches remain null/read-only; every new batch uses current policy version 1.';

COMMENT ON COLUMN export_batches.line_set_sealed
  IS 'Current-policy preview membership seal; legacy batches remain null and new batches seal once.';

COMMENT ON COLUMN export_batch_lines.integrity_policy_version
  IS 'V006 and earlier lines remain null/readable; every new line uses current policy version 1.';

COMMENT ON COLUMN export_batch_lines.captured_approval_record_id
  IS 'Exact latest candidate-bound approval event captured when the preview line was materialized.';

COMMENT ON COLUMN export_batch_lines.captured_source_version
  IS 'Exact immutable source version copied from the authoritative candidate.';

COMMENT ON INDEX export_batch_lines_current_policy_task_field_unique
  IS 'Rejects duplicate current-policy task/field lines while preserving legacy duplicates.';

COMMENT ON INDEX export_batch_lines_current_policy_candidate_unique
  IS 'A current-policy candidate may appear at most once in an export batch.';
