\set ON_ERROR_STOP on

BEGIN;
UPDATE imported_tasks
SET percent_complete = 12
WHERE id = '20000000-0000-0000-0000-000000000102';
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
COMMIT;
