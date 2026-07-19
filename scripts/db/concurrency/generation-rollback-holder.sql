\set ON_ERROR_STOP on

BEGIN;
SELECT id
FROM export_batches
WHERE id = '20000000-0000-0000-0000-000000000513'
FOR UPDATE;
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
SELECT 1 / 0;
COMMIT;
