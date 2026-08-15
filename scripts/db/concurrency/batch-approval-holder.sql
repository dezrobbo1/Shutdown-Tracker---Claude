\set ON_ERROR_STOP on

BEGIN;
UPDATE export_batches
SET status = 'approved', approved_at = '2026-07-18T08:45:00Z'
WHERE id = '20000000-0000-0000-0000-000000000544';
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
COMMIT;
