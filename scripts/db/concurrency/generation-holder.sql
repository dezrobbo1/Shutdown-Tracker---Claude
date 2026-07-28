\set ON_ERROR_STOP on

BEGIN;
UPDATE export_batches
SET
  status = 'generated',
  generated_at = '2026-07-18T09:00:00Z',
  export_file_uri = 'validation://synthetic/concurrent-generated.xml',
  export_file_hash = repeat('e', 64)
WHERE id = :'batch_id';
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
COMMIT;
