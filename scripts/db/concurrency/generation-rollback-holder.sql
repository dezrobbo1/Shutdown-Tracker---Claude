\set ON_ERROR_STOP on

BEGIN;
UPDATE export_batches
SET
  status = 'generated',
  generated_at = '2026-07-18T09:00:00Z',
  export_file_uri = 'validation://synthetic/must-roll-back.xml',
  export_file_hash = repeat('f', 64)
WHERE id = '20000000-0000-0000-0000-000000000548';
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
SELECT 1 / 0;
COMMIT;
