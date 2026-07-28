\set ON_ERROR_STOP on

UPDATE project_snapshots
SET status = 'superseded'
WHERE id = '20000000-0000-0000-0000-000000000004';
