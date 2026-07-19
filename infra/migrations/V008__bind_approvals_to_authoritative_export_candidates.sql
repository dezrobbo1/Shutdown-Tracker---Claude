-- Bind every newly approved export fact to an immutable, normalized candidate.
-- V006 rows (null policy) and V007 policy-1 rows remain unchanged history.

ALTER TABLE project_snapshots
  ADD CONSTRAINT project_snapshots_id_project_unique
  UNIQUE (id, project_id);

ALTER TABLE imported_tasks
  ADD CONSTRAINT imported_tasks_id_project_snapshot_unique
  UNIQUE (id, project_id, project_snapshot_id);

ALTER TABLE approval_records
  ADD COLUMN authoritative_export_candidate_id UUID,
  ADD COLUMN candidate_binding_policy_version INTEGER;

ALTER TABLE approval_records
  ADD CONSTRAINT approval_records_candidate_binding_pair_check
  CHECK (
    (authoritative_export_candidate_id IS NULL AND candidate_binding_policy_version IS NULL)
    OR (
      authoritative_export_candidate_id IS NOT NULL
      AND candidate_binding_policy_version = 2
    )
  ),
  ADD CONSTRAINT approval_records_candidate_binding_identity_unique
  UNIQUE (
    id,
    authoritative_export_candidate_id,
    candidate_binding_policy_version,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state
  );

CREATE OR REPLACE FUNCTION enforce_approval_candidate_binding_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.approval_state = 'approved_for_export'::approval_state
     AND (
       NEW.authoritative_export_candidate_id IS NULL
       OR NEW.candidate_binding_policy_version IS DISTINCT FROM 2
     ) THEN
    RAISE EXCEPTION 'new approved-for-export events require an authoritative policy-2 candidate binding'
      USING ERRCODE = '23514';
  END IF;

  IF (NEW.authoritative_export_candidate_id IS NULL)
     IS DISTINCT FROM (NEW.candidate_binding_policy_version IS NULL) THEN
    RAISE EXCEPTION 'approval candidate ID and binding policy version must be supplied together'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.candidate_binding_policy_version IS NOT NULL
     AND NEW.candidate_binding_policy_version IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'new approval candidate bindings require policy version 2'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER approval_records_enforce_candidate_binding_policy
BEFORE INSERT ON approval_records
FOR EACH ROW
EXECUTE FUNCTION enforce_approval_candidate_binding_policy();

CREATE TABLE export_candidate_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  approval_record_id UUID NOT NULL UNIQUE,
  binding_policy_version INTEGER NOT NULL DEFAULT 2,
  project_id UUID NOT NULL REFERENCES projects(id),
  project_snapshot_id UUID NOT NULL,
  imported_task_id UUID NOT NULL,
  source_entity_type TEXT NOT NULL,
  source_entity_id UUID NOT NULL,
  approval_state approval_state NOT NULL DEFAULT 'approved_for_export',
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
    CHECK (binding_policy_version = 2),
  CONSTRAINT export_candidate_records_approval_state_check
    CHECK (approval_state = 'approved_for_export'::approval_state),
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
    CHECK (btrim(source_entity_type) <> ''),
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
  CONSTRAINT export_candidate_records_reciprocal_identity_unique
    UNIQUE (
      id,
      binding_policy_version,
      project_id,
      source_entity_type,
      source_entity_id,
      approval_state,
      approval_record_id
    )
);

ALTER TABLE export_candidate_records
  ADD CONSTRAINT export_candidate_records_approval_binding_fkey
  FOREIGN KEY (
    approval_record_id,
    id,
    binding_policy_version,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state
  )
  REFERENCES approval_records (
    id,
    authoritative_export_candidate_id,
    candidate_binding_policy_version,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state
  )
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE approval_records
  ADD CONSTRAINT approval_records_authoritative_candidate_fkey
  FOREIGN KEY (
    authoritative_export_candidate_id,
    candidate_binding_policy_version,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state,
    id
  )
  REFERENCES export_candidate_records (
    id,
    binding_policy_version,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state,
    approval_record_id
  )
  DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX approval_records_candidate_event_order
  ON approval_records (
    project_id,
    authoritative_export_candidate_id,
    approval_event_order DESC
  )
  WHERE authoritative_export_candidate_id IS NOT NULL;

CREATE INDEX export_candidate_records_project_snapshot
  ON export_candidate_records (project_id, project_snapshot_id);

CREATE INDEX export_candidate_records_task_field
  ON export_candidate_records (imported_task_id, field_name);

CREATE INDEX export_candidate_records_source
  ON export_candidate_records (project_id, source_entity_type, source_entity_id);

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

  IF right(microseconds, 3) = '000' THEN
    RETURN base_value || '.' || left(microseconds, 3) || 'Z';
  END IF;

  RETURN base_value || '.' || microseconds || 'Z';
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
  numeric_value NUMERIC;
BEGIN
  IF candidate_value IS NULL OR btrim(candidate_value) = '' THEN
    RAISE EXCEPTION 'export candidate new value is required'
      USING ERRCODE = '23514';
  END IF;

  IF candidate_field_name IN ('percent_complete', 'physical_percent_complete') THEN
    IF btrim(candidate_value) !~ '^[0-9]+([.][0-9]+)?$' THEN
      RAISE EXCEPTION 'export candidate percentage must be numeric'
        USING ERRCODE = '23514';
    END IF;

    numeric_value := btrim(candidate_value)::NUMERIC;
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
    IF btrim(candidate_value) !~
       '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})$' THEN
      RAISE EXCEPTION 'export candidate actual date must be an ISO-8601 offset timestamp'
        USING ERRCODE = '23514';
    END IF;

    RETURN canonical_export_candidate_instant(btrim(candidate_value)::TIMESTAMPTZ);
  END IF;

  RAISE EXCEPTION 'unsupported export candidate field: %', candidate_field_name
    USING ERRCODE = '23514';
END;
$$;

CREATE OR REPLACE FUNCTION prepare_export_candidate_record()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  task_record RECORD;
BEGIN
  IF NEW.binding_policy_version IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'new export candidate records require binding policy version 2'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.approval_state IS DISTINCT FROM 'approved_for_export'::approval_state THEN
    RAISE EXCEPTION 'export candidate records require an approved-for-export approval event'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.source_event_or_payload_hash !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'source event or payload hash must be lowercase SHA-256 hex'
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

  IF task_record.external_uid IS NULL OR btrim(task_record.external_uid) = ''
     OR task_record.external_id IS NULL OR btrim(task_record.external_id) = ''
     OR task_record.name IS NULL OR btrim(task_record.name) = '' THEN
    RAISE EXCEPTION 'export candidate requires captured Microsoft Project UID, ID, and task name'
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
    ELSE NULL
  END;

  NEW.normalized_new_value := normalize_export_candidate_new_value(
    NEW.field_name,
    NEW.normalized_new_value
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

ALTER TABLE export_batches
  DROP CONSTRAINT export_batches_integrity_policy_version_check,
  DROP CONSTRAINT export_batches_current_policy_line_set_check;

ALTER TABLE export_batches
  ALTER COLUMN integrity_policy_version SET DEFAULT 2;

ALTER TABLE export_batches
  ADD CONSTRAINT export_batches_integrity_policy_version_check
  CHECK (integrity_policy_version IS NULL OR integrity_policy_version IN (1, 2)),
  ADD CONSTRAINT export_batches_current_policy_line_set_check
  CHECK (
    integrity_policy_version IS DISTINCT FROM 2
    OR (
      line_set_sealed IS NOT NULL
      AND (line_set_sealed = true OR status = 'draft_preview'::export_batch_state)
    )
  );

CREATE OR REPLACE FUNCTION validate_policy2_export_batch_integrity(
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
    RAISE EXCEPTION 'policy-2 export batch requires an accepted project snapshot'
      USING ERRCODE = '23514';
  END IF;

  FOR line_record IN
    SELECT ebl.*
    FROM export_batch_lines ebl
    WHERE ebl.export_batch_id = candidate_batch_id
      AND ebl.project_id = candidate_project_id
      AND ebl.project_snapshot_id = candidate_project_snapshot_id
    ORDER BY ebl.imported_task_id, ebl.field_name, ebl.id
    FOR SHARE
  LOOP
    batch_line_count := batch_line_count + 1;

    IF line_record.integrity_policy_version IS DISTINCT FROM 2 THEN
      RAISE EXCEPTION 'policy-2 export batch contains a non-policy-2 line'
        USING ERRCODE = '23514';
    END IF;

    SELECT candidate.*
      INTO candidate_record
      FROM export_candidate_records candidate
      WHERE candidate.id = line_record.authoritative_export_candidate_id
        AND candidate.binding_policy_version = 2
      FOR SHARE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'policy-2 export batch line % has no authoritative candidate', line_record.id
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
      FROM imported_tasks it
      WHERE it.id = candidate_record.imported_task_id
        AND it.project_id = candidate_record.project_id
        AND it.project_snapshot_id = candidate_record.project_snapshot_id
      FOR SHARE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'policy-2 export candidate task is missing from its accepted snapshot'
        USING ERRCODE = '23514';
    END IF;

    canonical_new_value := normalize_export_candidate_new_value(
      candidate_record.field_name,
      candidate_record.normalized_new_value
    );

    current_old_value := CASE candidate_record.field_name
      WHEN 'percent_complete' THEN
        CASE WHEN task_record.percent_complete IS NULL
          THEN NULL ELSE trim_scale(task_record.percent_complete)::TEXT END
      WHEN 'physical_percent_complete' THEN
        CASE WHEN task_record.physical_percent_complete IS NULL
          THEN NULL ELSE trim_scale(task_record.physical_percent_complete)::TEXT END
      WHEN 'actual_start' THEN canonical_export_candidate_instant(task_record.actual_start)
      WHEN 'actual_finish' THEN canonical_export_candidate_instant(task_record.actual_finish)
      ELSE NULL
    END;

    SELECT ar.id,
           ar.approval_state,
           ar.authoritative_export_candidate_id,
           ar.candidate_binding_policy_version
      INTO current_approval
      FROM approval_records ar
      WHERE ar.project_id = candidate_record.project_id
        AND ar.source_entity_type = candidate_record.source_entity_type
        AND ar.source_entity_id = candidate_record.source_entity_id
        AND ar.approval_event_order = (
          SELECT max(latest.approval_event_order)
          FROM approval_records latest
          WHERE latest.project_id = candidate_record.project_id
            AND latest.source_entity_type = candidate_record.source_entity_type
            AND latest.source_entity_id = candidate_record.source_entity_id
        )
      FOR SHARE OF ar;

    IF NOT FOUND
       OR current_approval.id IS DISTINCT FROM candidate_record.approval_record_id
       OR current_approval.approval_state IS DISTINCT FROM candidate_record.approval_state
       OR current_approval.authoritative_export_candidate_id IS DISTINCT FROM candidate_record.id
       OR current_approval.candidate_binding_policy_version IS DISTINCT FROM 2 THEN
      RAISE EXCEPTION 'policy-2 export candidate approval is no longer current'
        USING ERRCODE = '23514';
    END IF;

    expected_eligibility :=
      candidate_record.approval_state = 'approved_for_export'::approval_state
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
       OR candidate_record.source_event_or_payload_hash !~ '^[0-9a-f]{64}$'
       OR line_record.authoritative_export_candidate_id IS DISTINCT FROM candidate_record.id
       OR line_record.project_id IS DISTINCT FROM candidate_record.project_id
       OR line_record.project_snapshot_id IS DISTINCT FROM candidate_record.project_snapshot_id
       OR line_record.imported_task_id IS DISTINCT FROM candidate_record.imported_task_id
       OR line_record.source_entity_type IS DISTINCT FROM candidate_record.source_entity_type
       OR line_record.source_entity_id IS DISTINCT FROM candidate_record.source_entity_id
       OR line_record.captured_approval_record_id IS DISTINCT FROM candidate_record.approval_record_id
       OR line_record.captured_approval_state IS DISTINCT FROM candidate_record.approval_state
       OR line_record.field_name IS DISTINCT FROM candidate_record.field_name
       OR line_record.old_value IS DISTINCT FROM candidate_record.normalized_old_value
       OR line_record.new_value IS DISTINCT FROM candidate_record.normalized_new_value
       OR line_record.captured_source_event_or_payload_hash IS DISTINCT FROM candidate_record.source_event_or_payload_hash
       OR line_record.captured_task_external_uid IS DISTINCT FROM candidate_record.captured_task_external_uid
       OR line_record.captured_task_external_id IS DISTINCT FROM candidate_record.captured_task_external_id
       OR line_record.captured_task_name IS DISTINCT FROM candidate_record.captured_task_name
       OR line_record.source_actor_user_id IS DISTINCT FROM candidate_record.source_actor_user_id
       OR line_record.source_timestamp IS DISTINCT FROM candidate_record.source_timestamp
       OR line_record.reason IS DISTINCT FROM candidate_record.reason
       OR line_record.is_leaf_task IS DISTINCT FROM candidate_record.captured_is_leaf_task
       OR line_record.is_export_eligible IS DISTINCT FROM expected_eligibility THEN
      RAISE EXCEPTION 'policy-2 export batch line % no longer exactly matches its authoritative candidate and baseline', line_record.id
        USING ERRCODE = '23514';
    END IF;

    IF expected_eligibility THEN
      eligible_line_count := eligible_line_count + 1;
    END IF;
  END LOOP;

  IF batch_line_count = 0 THEN
    RAISE EXCEPTION 'policy-2 export batch must contain at least one validated line before sealing'
      USING ERRCODE = '23514';
  END IF;

  IF require_eligible_line AND eligible_line_count = 0 THEN
    RAISE EXCEPTION 'policy-2 export batch must contain at least one currently eligible line'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_export_batch_integrity_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.integrity_policy_version IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'new export batches require integrity policy version 2'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT' AND NEW.line_set_sealed IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'new export batches must begin with an unsealed line set'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'UPDATE'
     AND NEW.integrity_policy_version IS DISTINCT FROM OLD.integrity_policy_version THEN
    RAISE EXCEPTION 'export batch integrity policy version is immutable'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'UPDATE'
     AND NEW.line_set_sealed IS DISTINCT FROM OLD.line_set_sealed
     AND NOT (
       OLD.integrity_policy_version = 2
       AND OLD.status = 'draft_preview'::export_batch_state
       AND NEW.status = 'draft_preview'::export_batch_state
       AND OLD.line_set_sealed = false
       AND NEW.line_set_sealed = true
     ) THEN
    RAISE EXCEPTION 'export batch line-set seal may only transition from false to true for a policy-2 draft preview'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'UPDATE'
     AND OLD.integrity_policy_version = 2
     AND OLD.line_set_sealed = false
     AND NEW.line_set_sealed = true THEN
    PERFORM validate_policy2_export_batch_integrity(
      NEW.id,
      NEW.project_id,
      NEW.project_snapshot_id,
      false
    );
  END IF;

  IF TG_OP = 'UPDATE'
     AND NEW.integrity_policy_version = 2
     AND NEW.status IS DISTINCT FROM OLD.status
     AND NEW.status IN (
       'approved'::export_batch_state,
       'generated'::export_batch_state
     ) THEN
    IF NEW.line_set_sealed IS DISTINCT FROM true THEN
      RAISE EXCEPTION 'policy-2 export batch must have a sealed line set before approval or generation'
        USING ERRCODE = '23514';
    END IF;

    PERFORM validate_policy2_export_batch_integrity(
      NEW.id,
      NEW.project_id,
      NEW.project_snapshot_id,
      true
    );
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER export_batches_enforce_integrity_policy ON export_batches;

CREATE TRIGGER export_batches_enforce_integrity_policy
BEFORE INSERT OR UPDATE OF integrity_policy_version, line_set_sealed, status ON export_batches
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

  IF OLD.integrity_policy_version IS DISTINCT FROM 2 THEN
    RAISE EXCEPTION 'pre-policy-2 export history is read-only; create a fresh export preview'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

ALTER TABLE export_batch_lines
  ADD COLUMN authoritative_export_candidate_id UUID,
  ADD COLUMN captured_source_event_or_payload_hash TEXT,
  ADD COLUMN captured_task_external_uid TEXT,
  ADD COLUMN captured_task_external_id TEXT,
  ADD COLUMN captured_task_name TEXT;

ALTER TABLE export_batch_lines
  ALTER COLUMN integrity_policy_version SET DEFAULT 2;

ALTER TABLE export_batch_lines
  DROP CONSTRAINT export_batch_lines_integrity_policy_version_check,
  DROP CONSTRAINT export_batch_lines_current_policy_field_authority_check,
  DROP CONSTRAINT export_batch_lines_current_policy_approval_capture_check,
  DROP CONSTRAINT export_batch_lines_current_policy_eligible_approval_check;

ALTER TABLE export_batch_lines
  ADD CONSTRAINT export_batch_lines_integrity_policy_version_check
  CHECK (integrity_policy_version IS NULL OR integrity_policy_version IN (1, 2)),
  ADD CONSTRAINT export_batch_lines_current_policy_field_authority_check
  CHECK (
    integrity_policy_version IS NULL
    OR is_export_eligible = false
    OR field_name IN ('percent_complete', 'actual_start', 'actual_finish')
  ),
  ADD CONSTRAINT export_batch_lines_current_policy_approval_capture_check
  CHECK (
    integrity_policy_version IS NULL
    OR (
      captured_approval_record_id IS NOT NULL
      AND captured_approval_state IS NOT NULL
    )
  ),
  ADD CONSTRAINT export_batch_lines_current_policy_eligible_approval_check
  CHECK (
    integrity_policy_version IS NULL
    OR is_export_eligible = false
    OR captured_approval_state = 'approved_for_export'::approval_state
  ),
  ADD CONSTRAINT export_batch_lines_policy2_candidate_capture_check
  CHECK (
    integrity_policy_version IS DISTINCT FROM 2
    OR (
      authoritative_export_candidate_id IS NOT NULL
      AND captured_source_event_or_payload_hash IS NOT NULL
      AND captured_task_external_uid IS NOT NULL
      AND captured_task_external_id IS NOT NULL
      AND captured_task_name IS NOT NULL
      AND captured_approval_state = 'approved_for_export'::approval_state
    )
  ),
  ADD CONSTRAINT export_batch_lines_authoritative_candidate_fkey
  FOREIGN KEY (authoritative_export_candidate_id, integrity_policy_version)
  REFERENCES export_candidate_records (id, binding_policy_version);

CREATE UNIQUE INDEX export_batch_lines_policy2_task_field_unique
  ON export_batch_lines (export_batch_id, imported_task_id, field_name)
  WHERE integrity_policy_version = 2;

CREATE INDEX export_batch_lines_authoritative_candidate
  ON export_batch_lines (authoritative_export_candidate_id)
  WHERE authoritative_export_candidate_id IS NOT NULL;

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
  IF TG_OP = 'INSERT' THEN
    IF NEW.integrity_policy_version IS DISTINCT FROM 2 THEN
      RAISE EXCEPTION 'new export batch lines require integrity policy version 2'
        USING ERRCODE = '23514';
    END IF;

    SELECT integrity_policy_version, status, line_set_sealed
      INTO batch_record
      FROM export_batches
      WHERE id = NEW.export_batch_id
        AND project_id = NEW.project_id
        AND project_snapshot_id = NEW.project_snapshot_id
      FOR SHARE;

    IF NOT FOUND OR batch_record.integrity_policy_version IS DISTINCT FROM 2 THEN
      RAISE EXCEPTION 'policy-2 lines require a matching policy-2 export batch'
        USING ERRCODE = '23514';
    END IF;

    IF batch_record.status IS DISTINCT FROM 'draft_preview'::export_batch_state THEN
      RAISE EXCEPTION 'export preview lines may be created only while the batch is draft preview'
        USING ERRCODE = '23514';
    END IF;

    IF batch_record.line_set_sealed IS DISTINCT FROM false THEN
      RAISE EXCEPTION 'export preview lines may be created only before the batch line set is sealed'
        USING ERRCODE = '23514';
    END IF;

    SELECT *
      INTO candidate_record
      FROM export_candidate_records
      WHERE id = NEW.authoritative_export_candidate_id
        AND binding_policy_version = 2
      FOR SHARE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'policy-2 export lines require an authoritative candidate'
        USING ERRCODE = '23514';
    END IF;

    SELECT ar.id,
           ar.approval_state,
           ar.authoritative_export_candidate_id,
           ar.candidate_binding_policy_version
      INTO current_approval
      FROM approval_records ar
      WHERE ar.project_id = candidate_record.project_id
        AND ar.source_entity_type = candidate_record.source_entity_type
        AND ar.source_entity_id = candidate_record.source_entity_id
        AND ar.approval_event_order = (
          SELECT max(latest.approval_event_order)
          FROM approval_records latest
          WHERE latest.project_id = candidate_record.project_id
            AND latest.source_entity_type = candidate_record.source_entity_type
            AND latest.source_entity_id = candidate_record.source_entity_id
        );

    IF NOT FOUND
       OR current_approval.id IS DISTINCT FROM candidate_record.approval_record_id
       OR current_approval.approval_state IS DISTINCT FROM candidate_record.approval_state
       OR current_approval.authoritative_export_candidate_id IS DISTINCT FROM candidate_record.id
       OR current_approval.candidate_binding_policy_version IS DISTINCT FROM 2 THEN
      RAISE EXCEPTION 'authoritative export candidate approval is no longer current'
        USING ERRCODE = '23514';
    END IF;

    expected_eligibility :=
      candidate_record.approval_state = 'approved_for_export'::approval_state
      AND candidate_record.captured_is_leaf_task
      AND candidate_record.field_name IN ('percent_complete', 'actual_start', 'actual_finish');

    IF NEW.project_id IS DISTINCT FROM candidate_record.project_id
       OR NEW.project_snapshot_id IS DISTINCT FROM candidate_record.project_snapshot_id
       OR NEW.imported_task_id IS DISTINCT FROM candidate_record.imported_task_id
       OR NEW.source_entity_type IS DISTINCT FROM candidate_record.source_entity_type
       OR NEW.source_entity_id IS DISTINCT FROM candidate_record.source_entity_id
       OR NEW.captured_approval_record_id IS DISTINCT FROM candidate_record.approval_record_id
       OR NEW.captured_approval_state IS DISTINCT FROM candidate_record.approval_state
       OR NEW.field_name IS DISTINCT FROM candidate_record.field_name
       OR NEW.old_value IS DISTINCT FROM candidate_record.normalized_old_value
       OR NEW.new_value IS DISTINCT FROM candidate_record.normalized_new_value
       OR NEW.captured_source_event_or_payload_hash IS DISTINCT FROM candidate_record.source_event_or_payload_hash
       OR NEW.captured_task_external_uid IS DISTINCT FROM candidate_record.captured_task_external_uid
       OR NEW.captured_task_external_id IS DISTINCT FROM candidate_record.captured_task_external_id
       OR NEW.captured_task_name IS DISTINCT FROM candidate_record.captured_task_name
       OR NEW.source_actor_user_id IS DISTINCT FROM candidate_record.source_actor_user_id
       OR NEW.source_timestamp IS DISTINCT FROM candidate_record.source_timestamp
       OR NEW.reason IS DISTINCT FROM candidate_record.reason
       OR NEW.is_leaf_task IS DISTINCT FROM candidate_record.captured_is_leaf_task
       OR NEW.is_export_eligible IS DISTINCT FROM expected_eligibility THEN
      RAISE EXCEPTION 'export batch line does not exactly match its authoritative candidate'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  IF TG_OP = 'UPDATE'
     AND NEW.integrity_policy_version IS DISTINCT FROM OLD.integrity_policy_version THEN
    RAISE EXCEPTION 'export batch line integrity policy version is immutable'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION lock_active_export_batches_for_approval_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM eb.id
  FROM export_batches eb
  JOIN export_batch_lines ebl
    ON ebl.export_batch_id = eb.id
  WHERE eb.project_id = NEW.project_id
    AND ebl.project_id = NEW.project_id
    AND (
      (
        ebl.source_entity_type = NEW.source_entity_type
        AND ebl.source_entity_id = NEW.source_entity_id
      )
      OR (
        NEW.authoritative_export_candidate_id IS NOT NULL
        AND ebl.authoritative_export_candidate_id = NEW.authoritative_export_candidate_id
      )
    )
    AND eb.integrity_policy_version = 2
    AND ebl.integrity_policy_version = 2
    AND eb.status IN (
      'draft_preview'::export_batch_state,
      'awaiting_approval'::export_batch_state,
      'approved'::export_batch_state
    )
  ORDER BY eb.id
  FOR SHARE OF eb;

  RETURN NEW;
END;
$$;

COMMENT ON COLUMN approval_records.authoritative_export_candidate_id
  IS 'Policy-2 candidate bound atomically to this approval event. Legacy and non-approved later events may remain null.';

COMMENT ON COLUMN approval_records.candidate_binding_policy_version
  IS 'Candidate-binding policy marker. New approved-for-export events require version 2.';

COMMENT ON TABLE export_candidate_records
  IS 'Immutable server-authoritative task/field/value records bound atomically to exact planner approval events.';

COMMENT ON COLUMN export_candidate_records.normalized_old_value
  IS 'Canonical imported snapshot value captured by the database when the candidate is created.';

COMMENT ON COLUMN export_candidate_records.normalized_new_value
  IS 'Canonical proposed value approved for this exact task and field.';

COMMENT ON COLUMN export_candidate_records.source_event_or_payload_hash
  IS 'Lowercase SHA-256 fingerprint of the immutable upstream source event or payload.';

COMMENT ON COLUMN export_batch_lines.authoritative_export_candidate_id
  IS 'Exact policy-2 candidate selected by ID; null for V006 and V007 history.';

COMMENT ON COLUMN export_batch_lines.captured_source_event_or_payload_hash
  IS 'Source event or payload hash copied from the authoritative candidate.';

COMMENT ON COLUMN export_batch_lines.captured_task_external_uid
  IS 'Microsoft Project task UID captured from the authoritative candidate for policy-2 worker handoff.';

COMMENT ON COLUMN export_batch_lines.captured_task_external_id
  IS 'Microsoft Project task ID captured from the authoritative candidate for policy-2 worker handoff.';

COMMENT ON COLUMN export_batch_lines.captured_task_name
  IS 'Imported task name captured from the authoritative candidate for policy-2 worker handoff.';

COMMENT ON INDEX export_batch_lines_policy2_task_field_unique
  IS 'Allows at most one policy-2 candidate per imported task and field within an export batch.';
