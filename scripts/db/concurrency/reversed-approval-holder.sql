\set ON_ERROR_STOP on

BEGIN;
SELECT validation.create_approval(
  '20000000-0000-0000-0000-000000000351',
  '20000000-0000-0000-0000-000000000249',
  'rejected',
  'Reversed holder candidate A'
);
SELECT validation.create_approval(
  '20000000-0000-0000-0000-000000000352',
  '20000000-0000-0000-0000-000000000250',
  'superseded',
  'Reversed holder candidate B'
);
SELECT pg_advisory_lock(807, :gate_id);
SELECT pg_advisory_unlock(807, :gate_id);
COMMIT;
