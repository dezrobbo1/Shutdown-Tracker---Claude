-- Offline capture for raised problems.
--
-- A field user raises a problem where the work is, which is where there is no signal.
-- Progress reports and Critical Updates already survive that: each carries a key the
-- device generates once, at capture time, so a retry over a bad connection returns the
-- record the first attempt created instead of raising the same thing twice. Problems had
-- no such key, so the offline queue could not hold them, and raising one required a
-- connection at the moment it was raised.
--
-- The columns mirror task_progress_updates: the key identifies the capture to the server,
-- and the local id identifies it to the device that made it, which is what lets a person
-- match a queued entry to the record it became.
--
-- Both are nullable. A problem raised from the console has neither, and a NULL key is not
-- a claim of uniqueness — the partial unique index below deliberately ignores them.

ALTER TABLE problems
  ADD COLUMN idempotency_key TEXT,
  ADD COLUMN offline_local_id TEXT;

COMMENT ON COLUMN problems.idempotency_key IS 'Device-generated. Prevents a retried offline capture raising a second problem.';
COMMENT ON COLUMN problems.offline_local_id IS 'The capturing device''s own identifier for this problem, so a queued entry can be matched to the record it became.';

-- A retried capture from the field must not create a second problem. Scoped to the
-- project because the key is generated on a device and only has to be unique there;
-- pairing it with the project is what makes a collision between two devices impossible.
CREATE UNIQUE INDEX idx_problems_idempotency
  ON problems (project_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
