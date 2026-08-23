-- Which batch carried which field update, and a progress row that stops guessing.
--
-- Two defects, one migration, because they are the same column.
--
-- **export_batch_id was never written.** task_progress_updates has carried the column since V009
-- and nothing has ever set it, so the audit could not answer which export batch carried which
-- approved field change. findExportQueue already filters on `export_batch_id IS NULL` and says in
-- a comment that nothing writes it yet; this is what makes that clause mean something.
--
-- **Six of the nine progress_export_state values were dead.** Only `not_eligible`, `eligible` and
-- `superseded` were ever assigned. `artifact_generated`, `opened_in_microsoft_project` and
-- `verified` mirrored export_batches.status, which owns that lifecycle for real — a batch's
-- progress is answerable by joining through the export_batch_id this migration starts writing, and
-- two columns that must agree about one fact eventually disagree. `approved_for_export` duplicated
-- planner_review_state, which already records that the planner approved. `export_blocked` had no
-- blocking logic behind it. `in_export_preview` was the one dead value worth keeping, and it
-- becomes live below.
--
-- The surviving lifecycle is the one docs/product/approval-export-state-model.md already describes
-- for an export candidate — eligible, in_export_preview, exported — minus the states that document
-- describes for entities other than this row. It deliberately stops at `exported` rather than
-- reaching for `verified`: that document warns against overloading `verified`, which means a
-- generated artifact opened in Microsoft Project as expected, not that anything was recalculated.
--
--   eligible ──(preview created)──> in_export_preview ──(batch verified)──> exported
--                    ^                     │
--                    └──(batch rejected)───┘
--
-- A rejected batch returns its updates to the queue rather than stranding them. The approved field
-- work was never carried anywhere, and the queue filters on `export_batch_id IS NULL`, so leaving
-- the row claimed would discard reviewed field work permanently with no way back short of a
-- correction.
--
-- PostgreSQL cannot drop a value from an enum, so the type is rebuilt and swapped rather than
-- edited. The USING clause maps every retired value rather than defaulting them: no row can hold
-- one today, but a migration that silently dropped rows it did not expect would be worse than one
-- that states where each would land.

-- The old index was partial on `export_state IN ('eligible', 'approved_for_export')` — half of it
-- on a value nothing wrote. Dropped before the type is rebuilt rather than after: a partial index
-- predicate holds constants of the column's type, so PostgreSQL rebuilds it during the ALTER and
-- compares the new type against the old one, which has no equality operator. The replacement is
-- created below, once the new type is the column's own.
DROP INDEX idx_task_progress_updates_export_candidates;

ALTER TABLE task_progress_updates ALTER COLUMN export_state DROP DEFAULT;

CREATE TYPE progress_export_state_v2 AS ENUM (
  'not_eligible',
  'eligible',
  'in_export_preview',
  'exported',
  'superseded'
);

ALTER TABLE task_progress_updates
  ALTER COLUMN export_state TYPE progress_export_state_v2
  USING (
    CASE export_state::TEXT
      -- Live values, unchanged.
      WHEN 'not_eligible' THEN 'not_eligible'
      WHEN 'eligible' THEN 'eligible'
      WHEN 'in_export_preview' THEN 'in_export_preview'
      WHEN 'superseded' THEN 'superseded'
      -- Retired. Approval is planner_review_state's fact, not this column's.
      WHEN 'approved_for_export' THEN 'eligible'
      -- Retired with no blocking logic behind it; a blocked update is one that may not travel.
      WHEN 'export_blocked' THEN 'not_eligible'
      -- Retired batch mirrors. Each meant the batch had got somewhere, which is now read from
      -- export_batches.status through export_batch_id; for this row the fact is that it travelled.
      WHEN 'artifact_generated' THEN 'exported'
      WHEN 'opened_in_microsoft_project' THEN 'exported'
      WHEN 'verified' THEN 'exported'
    END
  )::progress_export_state_v2;

ALTER TABLE task_progress_updates
  ALTER COLUMN export_state SET DEFAULT 'not_eligible';

DROP TYPE progress_export_state;
ALTER TYPE progress_export_state_v2 RENAME TO progress_export_state;

-- The replacement for the index dropped above. The queue it exists for asks for eligible rows no
-- batch has claimed, in submission order, so the predicate and the ordering column both belong in
-- it — rather than half a predicate on a value nothing ever wrote.
CREATE INDEX idx_task_progress_updates_export_queue
  ON task_progress_updates (project_id, submitted_at)
  WHERE export_state = 'eligible' AND export_batch_id IS NULL;

-- Answering "which updates did this batch carry" is now a real query rather than a dead column.
CREATE INDEX idx_task_progress_updates_export_batch
  ON task_progress_updates (export_batch_id)
  WHERE export_batch_id IS NOT NULL;

COMMENT ON COLUMN task_progress_updates.export_state IS
  'Whether this update may travel to Microsoft Project, and whether it has. Only reviewed leaf-task percent complete, actual start, and actual finish may reach export; see the MVP export whitelist. How far the carrying batch got is export_batches.status, reached through export_batch_id, and is deliberately not mirrored here.';

COMMENT ON COLUMN task_progress_updates.export_batch_id IS
  'The export batch that carried this update. Set when a preview is created, cleared if that batch is rejected, and retained once the batch is verified so the audit can answer which batch carried which field change.';
