\set ON_ERROR_STOP on

INSERT INTO approval_records (
  id,
  project_id,
  source_entity_type,
  source_entity_id,
  approval_state,
  reason,
  created_at
)
VALUES (
  :'approval_id',
  '20000000-0000-0000-0000-000000000001',
  'task_update',
  :'source_id',
  :'approval_state',
  :'reason',
  :'created_at'
);
