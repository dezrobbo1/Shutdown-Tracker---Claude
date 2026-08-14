import { useCallback } from "react";
import type { ImportReviewSnapshotDetail, ImportReviewTaskRow } from "@shutdown-tracker/api-client";
import type { ConsoleApiClient } from "./consoleApi";
import { useAsyncResource } from "./useAsyncResource";
import type { Resource } from "./useAsyncResource";

/**
 * The imported tasks of the newest snapshot, indexed for lookup.
 *
 * Progress updates, problems, evidence, and export lines all reference a task by id. Showing
 * a raw identifier to a supervisor deciding whether to accept a report is useless — they need
 * the task name and its place in the schedule — so the zones resolve names through this.
 */

export type SnapshotTasks = {
  snapshotId: string | null;
  tasks: ImportReviewTaskRow[];
  byId: Map<string, ImportReviewTaskRow>;
};

export const emptySnapshotTasks: SnapshotTasks = {
  snapshotId: null,
  tasks: [],
  byId: new Map()
};

export function useSnapshotTasks(
  client: ConsoleApiClient,
  projectId: string,
  enabled: boolean
): Resource<SnapshotTasks> {
  return useAsyncResource<SnapshotTasks>(
    useCallback(async () => {
      const snapshots = await client.importReview.listSnapshots(projectId);
      const newest = [...snapshots].sort((left, right) => right.snapshotVersion - left.snapshotVersion)[0];
      if (!newest) {
        return emptySnapshotTasks;
      }
      const detail = await client.importReview.getSnapshot(projectId, newest.id);
      return indexSnapshotTasks(detail);
    }, [client, projectId]),
    { enabled, idleMessage: "Configure a project and actor to load the imported schedule." }
  );
}

export function indexSnapshotTasks(detail: ImportReviewSnapshotDetail): SnapshotTasks {
  return {
    snapshotId: detail.snapshot.id,
    tasks: detail.tasks,
    byId: new Map(detail.tasks.map((task) => [task.id, task]))
  };
}

/**
 * A task's display name.
 *
 * Falls back to the identifier rather than to a blank, because a record referring to a task
 * that is not in the current snapshot is a real condition worth seeing, not one to hide.
 */
export function taskLabel(tasks: SnapshotTasks, importedTaskId: string | null | undefined) {
  if (!importedTaskId) {
    return "No task";
  }
  const task = tasks.byId.get(importedTaskId);
  if (!task) {
    return `Task ${importedTaskId.slice(0, 8)} (not in this snapshot)`;
  }
  return task.name ?? `Task ${task.externalId ?? task.externalUid ?? importedTaskId.slice(0, 8)}`;
}

/** Only leaf tasks carry execution: a summary task's progress is a roll-up of its children. */
export function leafTasks(tasks: SnapshotTasks) {
  return tasks.tasks.filter((task) => !task.summary);
}
