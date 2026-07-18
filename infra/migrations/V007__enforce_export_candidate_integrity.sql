-- Grandfather the V006 export/approval history and enforce the current export
-- integrity policy only for records created after this migration.

ALTER TABLE approval_records
  ADD COLUMN approval_event_order BIGINT;

CREATE SEQUENCE approval_records_event_order_seq AS BIGINT;

CREATE OR REPLACE FUNCTION assign_approval_event_order()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.approval_event_order IS NOT NULL THEN
    RAISE EXCEPTION 'approval_event_order is assigned by the database'
      USING ERRCODE = '23514';
  END IF;

  NEW.approval_event_order := nextval('approval_records_event_order_seq'::regclass);
  RETURN NEW;
END;
$$;

CREATE TRIGGER approval_records_assign_event_order
BEFORE INSERT ON approval_records
FOR EACH ROW
EXECUTE FUNCTION assign_approval_event_order();

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

ALTER TABLE approval_records
  ADD CONSTRAINT approval_records_event_order_positive_check
  CHECK (approval_event_order IS NULL OR approval_event_order > 0);

ALTER SEQUENCE approval_records_event_order_seq
  OWNED BY approval_records.approval_event_order;

ALTER TABLE approval_records
  ADD CONSTRAINT approval_records_capture_identity_unique
  UNIQUE (id, project_id, source_entity_type, source_entity_id, approval_state);

ALTER TABLE export_batches
  ADD COLUMN integrity_policy_version INTEGER,
  ADD COLUMN line_set_sealed BOOLEAN;

ALTER TABLE export_batches
  ALTER COLUMN integrity_policy_version SET DEFAULT 1,
  ALTER COLUMN line_set_sealed SET DEFAULT false;

ALTER TABLE export_batches
  ADD CONSTRAINT export_batches_integrity_policy_version_check
  CHECK (integrity_policy_version IS NULL OR integrity_policy_version = 1),
  ADD CONSTRAINT export_batches_current_policy_line_set_check
  CHECK (
    integrity_policy_version IS DISTINCT FROM 1
    OR (
      line_set_sealed IS NOT NULL
      AND (line_set_sealed = true OR status = 'draft_preview')
    )
  ),
  ADD CONSTRAINT export_batches_line_identity_unique
  UNIQUE (id, project_id, project_snapshot_id, integrity_policy_version);

CREATE OR REPLACE FUNCTION enforce_export_batch_integrity_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.integrity_policy_version IS DISTINCT FROM 1 THEN
    RAISE EXCEPTION 'new export batches require integrity policy version 1'
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
       OLD.integrity_policy_version = 1
       AND OLD.status = 'draft_preview'::export_batch_state
       AND OLD.line_set_sealed = false
       AND NEW.line_set_sealed = true
     ) THEN
    RAISE EXCEPTION 'export batch line-set seal may only transition from false to true while draft preview'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER export_batches_enforce_integrity_policy
BEFORE INSERT OR UPDATE OF integrity_policy_version, line_set_sealed ON export_batches
FOR EACH ROW
EXECUTE FUNCTION enforce_export_batch_integrity_policy();

ALTER TABLE export_batch_lines
  ADD COLUMN integrity_policy_version INTEGER,
  ADD COLUMN captured_approval_record_id UUID,
  ADD COLUMN captured_approval_state approval_state;

ALTER TABLE export_batch_lines
  ALTER COLUMN integrity_policy_version SET DEFAULT 1;

ALTER TABLE export_batch_lines
  ADD CONSTRAINT export_batch_lines_integrity_policy_version_check
  CHECK (integrity_policy_version IS NULL OR integrity_policy_version = 1),
  ADD CONSTRAINT export_batch_lines_captured_approval_fkey
  FOREIGN KEY (
    captured_approval_record_id,
    project_id,
    source_entity_type,
    source_entity_id,
    captured_approval_state
  )
  REFERENCES approval_records (
    id,
    project_id,
    source_entity_type,
    source_entity_id,
    approval_state
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
  );

CREATE OR REPLACE FUNCTION enforce_export_batch_line_integrity_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  batch_policy_version INTEGER;
  batch_status export_batch_state;
  batch_line_set_sealed BOOLEAN;
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.integrity_policy_version IS DISTINCT FROM 1 THEN
      RAISE EXCEPTION 'new export batch lines require integrity policy version 1'
        USING ERRCODE = '23514';
    END IF;

    SELECT integrity_policy_version, status, line_set_sealed
      INTO batch_policy_version, batch_status, batch_line_set_sealed
      FROM export_batches
      WHERE id = NEW.export_batch_id
      FOR SHARE;

    IF batch_policy_version IS DISTINCT FROM 1 THEN
      RAISE EXCEPTION 'current-policy lines require a current-policy export batch'
        USING ERRCODE = '23514';
    END IF;

    IF batch_status IS DISTINCT FROM 'draft_preview'::export_batch_state THEN
      RAISE EXCEPTION 'export preview lines may be created only while the batch is draft preview'
        USING ERRCODE = '23514';
    END IF;

    IF batch_line_set_sealed IS DISTINCT FROM false THEN
      RAISE EXCEPTION 'export preview lines may be created only before the batch line set is sealed'
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

CREATE TRIGGER export_batch_lines_enforce_integrity_policy
BEFORE INSERT OR UPDATE OF integrity_policy_version ON export_batch_lines
FOR EACH ROW
EXECUTE FUNCTION enforce_export_batch_line_integrity_policy();

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
    AND ebl.source_entity_type = NEW.source_entity_type
    AND ebl.source_entity_id = NEW.source_entity_id
    AND eb.integrity_policy_version = 1
    AND ebl.integrity_policy_version = 1
    AND eb.line_set_sealed = true
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

CREATE TRIGGER approval_records_lock_active_export_batches
BEFORE INSERT ON approval_records
FOR EACH ROW
EXECUTE FUNCTION lock_active_export_batches_for_approval_event();

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

CREATE UNIQUE INDEX export_batch_lines_current_policy_task_field_unique
  ON export_batch_lines (export_batch_id, imported_task_id, field_name)
  WHERE integrity_policy_version = 1;

ALTER TABLE export_batch_lines
  ADD CONSTRAINT export_batch_lines_current_policy_field_authority_check
  CHECK (
    integrity_policy_version IS DISTINCT FROM 1
    OR is_export_eligible = false
    OR field_name IN ('percent_complete', 'actual_start', 'actual_finish')
  ),
  ADD CONSTRAINT export_batch_lines_current_policy_approval_capture_check
  CHECK (
    integrity_policy_version IS DISTINCT FROM 1
    OR (
      captured_approval_record_id IS NOT NULL
      AND captured_approval_state IS NOT NULL
    )
  ),
  ADD CONSTRAINT export_batch_lines_current_policy_eligible_approval_check
  CHECK (
    integrity_policy_version IS DISTINCT FROM 1
    OR is_export_eligible = false
    OR captured_approval_state = 'approved_for_export'
  );

COMMENT ON COLUMN approval_records.approval_event_order
  IS 'Database-assigned monotonic order for approval events created after V007. Legacy rows remain null.';

COMMENT ON COLUMN export_batches.integrity_policy_version
  IS 'Export-integrity policy marker. V006 and earlier batches remain null and read-only; new batches use version 1.';

COMMENT ON COLUMN export_batches.line_set_sealed
  IS 'Current-policy preview membership seal. Legacy batches remain null; new batches seal once after all lines are inserted.';

COMMENT ON COLUMN export_batch_lines.integrity_policy_version
  IS 'Export-integrity policy marker. V006 and earlier lines remain null; new lines use version 1.';

COMMENT ON COLUMN export_batch_lines.captured_approval_record_id
  IS 'Exact approval record used when a current-policy preview line was materialized.';

COMMENT ON COLUMN export_batch_lines.captured_approval_state
  IS 'Approval state captured with the exact approval record used to materialize a current-policy preview line.';

COMMENT ON INDEX export_batch_lines_current_policy_task_field_unique
  IS 'Allows at most one current-policy candidate per imported task and field within an export batch; legacy duplicates are preserved.';

COMMENT ON CONSTRAINT export_batch_lines_current_policy_field_authority_check ON export_batch_lines
  IS 'Only the approved MVP leaf-task progress and actual fields may be export eligible under the current policy.';
