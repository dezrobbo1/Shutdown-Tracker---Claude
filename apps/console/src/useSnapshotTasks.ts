import { useCallback } from "react";
import type {
  ImportReviewSnapshotDetail,
  ImportReviewSnapshotSummary,
  ImportReviewTaskRow
} from "@shutdown-tracker/api-client";
import type { ConsoleApiClient } from "./consoleApi";
import { useAsyncResource } from "./useAsyncResource";
import type { Resource } from "./useAsyncResource";

/**
 * The imported tasks of the newest accepted snapshot, indexed for lookup.
 *
 * Progress updates, problems, evidence, and export lines all reference a task by id. Showing
 * a raw identifier to a supervisor deciding whether to accept a report is useless — they need
 * the task name and its place in the schedule — so the zones resolve names through this.
 */

export type SnapshotTasks = {
  snapshotId: string | null;
  tasks: ImportReviewTaskRow[];
  byId: Map<string, ImportReviewTaskRow>;
  /**
   * Microsoft Project resource groups reaching each task through its assignments.
   *
   * Resolved here rather than at the server because the snapshot detail already carries the
   * resources and assignments; the console was fetching both and discarding them.
   */
  groupsByTaskId: Map<string, string[]>;
};

export const emptySnapshotTasks: SnapshotTasks = {
  snapshotId: null,
  tasks: [],
  byId: new Map(),
  groupsByTaskId: new Map()
};

export function useSnapshotTasks(
  client: ConsoleApiClient,
  projectId: string,
  enabled: boolean
): Resource<SnapshotTasks> {
  return useAsyncResource<SnapshotTasks>(
    useCallback(async () => {
      const snapshots = await client.importReview.listSnapshots(projectId);
      // Accepted only. A newer snapshot that has been parsed but not accepted is a proposal, not
      // the schedule this project is running on: export candidates are refused against it, and
      // reporting progress against its tasks would attach work to a schedule nobody adopted.
      const newest = newestAcceptedSnapshot(snapshots);
      if (!newest) {
        return emptySnapshotTasks;
      }
      const detail = await client.importReview.getSnapshot(projectId, newest.id);
      return indexSnapshotTasks(detail);
    }, [client, projectId]),
    { enabled, idleMessage: "Configure a project and actor to load the imported schedule." }
  );
}

/**
 * The accepted snapshot with the highest version, or nothing when none has been accepted.
 *
 * Exported so the rule is testable on its own: "newest" and "newest accepted" differ exactly when
 * a re-import is sitting in review, which is the case that used to send the whole console at a
 * schedule the server would refuse.
 */
export function newestAcceptedSnapshot(
  snapshots: ImportReviewSnapshotSummary[]
): ImportReviewSnapshotSummary | undefined {
  return [...snapshots]
    .filter((snapshot) => snapshot.status === "ACCEPTED")
    .sort((left, right) => right.snapshotVersion - left.snapshotVersion)[0];
}

export function indexSnapshotTasks(detail: ImportReviewSnapshotDetail): SnapshotTasks {
  return {
    snapshotId: detail.snapshot.id,
    tasks: detail.tasks,
    byId: new Map(detail.tasks.map((task) => [task.id, task])),
    groupsByTaskId: indexResourceGroups(detail)
  };
}

/**
 * Resource groups per task, distinct and sorted.
 *
 * A task usually draws several resources from one group, so the distinct set is what reads as
 * useful — "Mechanical", not "Mechanical, Mechanical, Mechanical". A resource with no Group in
 * Project contributes nothing rather than an empty entry, so a task whose resources are all
 * ungrouped is indistinguishable from one with no resources at all: both simply have no group,
 * which is what the column then shows.
 *
 * Assignments are matched by `importedResourceId` where the snapshot resolved it and by
 * `resourceExternalUid` where it did not, because an assignment can name a resource the parse
 * carried without a database row.
 */
export function indexResourceGroups(detail: ImportReviewSnapshotDetail): Map<string, string[]> {
  const groupByResourceId = new Map<string, string>();
  const groupByResourceUid = new Map<string, string>();
  for (const resource of detail.resources) {
    const group = resource.resourceGroup?.trim();
    if (!group) {
      continue;
    }
    groupByResourceId.set(resource.id, group);
    if (resource.externalUid) {
      groupByResourceUid.set(resource.externalUid, group);
    }
  }

  const collected = new Map<string, Set<string>>();
  for (const assignment of detail.assignments) {
    if (!assignment.importedTaskId) {
      continue;
    }
    const group =
      (assignment.importedResourceId
        ? groupByResourceId.get(assignment.importedResourceId)
        : undefined) ??
      (assignment.resourceExternalUid
        ? groupByResourceUid.get(assignment.resourceExternalUid)
        : undefined);
    if (!group) {
      continue;
    }
    const existing = collected.get(assignment.importedTaskId);
    if (existing) {
      existing.add(group);
    } else {
      collected.set(assignment.importedTaskId, new Set([group]));
    }
  }

  return new Map(
    [...collected].map(([taskId, groups]) => [taskId, [...groups].sort((a, b) => a.localeCompare(b))])
  );
}

/** Every group present in the snapshot, sorted — the options a group filter offers. */
export function allResourceGroups(tasks: SnapshotTasks): string[] {
  const groups = new Set<string>();
  for (const taskGroups of tasks.groupsByTaskId.values()) {
    for (const group of taskGroups) {
      groups.add(group);
    }
  }
  return [...groups].sort((a, b) => a.localeCompare(b));
}

/** The groups on one task, or an empty list when Project gave its resources no Group. */
export function taskResourceGroups(tasks: SnapshotTasks, taskId: string): string[] {
  return tasks.groupsByTaskId.get(taskId) ?? [];
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

/**
 * Summary tasks, which is what a Critical Work Package may be sourced from.
 *
 * The product rule is that package membership is chosen from summary tasks in the imported
 * schedule — never derived from critical path or float, which are not calculated here.
 * Offering leaf tasks in that picker would invite the arbitrary grouping the docs defer.
 */
export function summaryTasks(tasks: SnapshotTasks) {
  return tasks.tasks.filter((task) => task.summary);
}
