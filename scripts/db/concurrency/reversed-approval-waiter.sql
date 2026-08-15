\set ON_ERROR_STOP on

BEGIN;
SELECT validation.create_approval(
  '20000000-0000-0000-0000-000000000353',
  '20000000-0000-0000-0000-000000000250',
  'rejected',
  'Reversed waiter candidate B'
);
SELECT validation.create_approval(
  '20000000-0000-0000-0000-000000000354',
  '20000000-0000-0000-0000-000000000249',
  'superseded',
  'Reversed waiter candidate A'
);
COMMIT;
