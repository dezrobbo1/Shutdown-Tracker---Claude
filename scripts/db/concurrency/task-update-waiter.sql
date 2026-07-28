\set ON_ERROR_STOP on

UPDATE imported_tasks
SET name = 'Concurrent post-generation task name'
WHERE id = '20000000-0000-0000-0000-000000000101';
