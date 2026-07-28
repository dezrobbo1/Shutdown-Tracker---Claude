\set ON_ERROR_STOP on

BEGIN;
SELECT validation.insert_candidate_line(
  '20000000-0000-0000-0000-000000000643',
  '20000000-0000-0000-0000-000000000543',
  '20000000-0000-0000-0000-000000000243'
);
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
COMMIT;
