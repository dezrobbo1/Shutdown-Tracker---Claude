import { renderToString } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ShutdownTrackerApiError } from "@shutdown-tracker/api-client";
import type { TaskProgressSubmitRequest, TaskProgressUpdateRecord } from "@shutdown-tracker/api-client";
import { App, mobileChipTone, toInstant, validateFieldProgress, workCardPercent } from "./App";
import { buildFieldSession, describeFieldSession, fieldSessionAllows } from "./fieldSession";
import {
  OfflineProgressQueue,
  createMemoryQueueStore,
  isPermanentRejection,
  isSettled,
  pendingCount,
  rejectedCount
} from "./offlineQueue";
import type { QueuedProgressUpdate } from "./offlineQueue";
import { newLocalId } from "./useFieldQueue";

function idFactory() {
  let next = 0;
  return () => `id-${++next}`;
}

function serverRecord(overrides: Partial<TaskProgressUpdateRecord> = {}): TaskProgressUpdateRecord {
  return {
    id: "server-1",
    projectId: "project-1",
    projectSnapshotId: "snapshot-1",
    importedTaskId: "task-1",
    executionState: "IN_PROGRESS",
    percentComplete: 40,
    actualStart: null,
    actualFinish: null,
    physicalPercentComplete: null,
    comment: null,
    submittedByUserId: "user-1",
    progressReviewState: "SUBMITTED",
    plannerReviewState: "NOT_REQUIRED",
    exportState: "NOT_ELIGIBLE",
    supersedesProgressUpdateId: null,
    ...overrides
  };
}

const capture = {
  importedTaskId: "task-1",
  executionState: "IN_PROGRESS" as const,
  percentComplete: 40
};

describe("field app shell", () => {
  it("shows the field navigation", () => {
    const html = renderToString(<App />);

    // The baseline field zones. Reporting progress is reached from a task rather than being a
    // peer tab, so it is deliberately absent here.
    for (const label of ["My Work", "Today", "Problems", "Evidence", "Sync"]) {
      expect(html).toContain(label);
    }
  });

  it("shows an unsent report on the work card rather than the stale server value", () => {
    const task = {
      id: "task-1",
      name: "C2 Cyclone — remove access cover",
      percentComplete: 10
    } as never;

    expect(workCardPercent(task, undefined)).toBe("10%");
    expect(
      workCardPercent(task, {
        request: { percentComplete: 60 }
      } as never)
    ).toBe("60%");
  });

  it("tells the reporter that submitting sends the report for review, not to the schedule", () => {
    const html = renderToString(<App />);

    expect(html).toContain("No project configured for this device.");
    expect(html).not.toContain("Review mode");
  });

  it("names the sync states so saved and sent are not confused", () => {
    expect(mobileChipTone("Server received")).toBe("green");
    expect(mobileChipTone("Waiting to send")).toBe("amber");
    expect(mobileChipTone("Could not be sent")).toBe("red");
    expect(mobileChipTone("Blocked")).toBe("red");
  });
});

describe("field session", () => {
  it("defaults to the field-user role rather than refusing to start", () => {
    const session = buildFieldSession({
      VITE_SHUTDOWN_TRACKER_PROJECT_ID: "project-1",
      VITE_SHUTDOWN_TRACKER_ACTOR_ID: "user-1"
    });

    expect(session.actor?.role).toBe("field_user");
    expect(session.live).toBe(true);
    expect(fieldSessionAllows(session, "SUBMIT_TASK_PROGRESS")).toBe(true);
    expect(fieldSessionAllows(session, "REVIEW_TASK_PROGRESS")).toBe(false);
  });

  it("is not live without an actor, and says so", () => {
    const session = buildFieldSession({ VITE_SHUTDOWN_TRACKER_PROJECT_ID: "project-1" });

    expect(session.live).toBe(false);
    expect(describeFieldSession(session)).toBe("No actor configured");
  });
});

describe("offline queue", () => {
  it("stores a report before anything is sent", async () => {
    const store = createMemoryQueueStore();
    const submit = vi.fn();
    const queue = new OfflineProgressQueue({ store, submit, newId: idFactory() });

    await queue.enqueue(capture, "Remove guard");

    expect(submit).not.toHaveBeenCalled();
    const items = await queue.list();
    expect(items).toHaveLength(1);
    expect(items[0].syncState).toBe("PENDING");
    expect(items[0].taskName).toBe("Remove guard");
  });

  it("carries an idempotency key so a retry cannot double-report progress", async () => {
    const store = createMemoryQueueStore();
    const sent: TaskProgressSubmitRequest[] = [];
    let failFirst = true;
    const queue = new OfflineProgressQueue({
      store,
      newId: idFactory(),
      submit: async (request) => {
        sent.push(request);
        if (failFirst) {
          failFirst = false;
          throw new Error("network down");
        }
        return serverRecord();
      }
    });

    await queue.enqueue(capture, "Weld repair");
    await queue.flush();
    await queue.flush();

    expect(sent).toHaveLength(2);
    expect(sent[0].idempotencyKey).toBeTruthy();
    expect(sent[1].idempotencyKey).toBe(sent[0].idempotencyKey);

    const items = await queue.list();
    expect(items).toHaveLength(1);
    expect(items[0].syncState).toBe("SYNCED");
    expect(items[0].serverId).toBe("server-1");
  });

  it("keeps a report queued when the network fails, because the work still happened", async () => {
    const store = createMemoryQueueStore();
    const queue = new OfflineProgressQueue({
      store,
      newId: idFactory(),
      submit: async () => {
        throw new TypeError("Failed to fetch");
      }
    });

    await queue.enqueue(capture, "Isolate feeder");
    const items = await queue.flush();

    expect(items[0].syncState).toBe("PENDING");
    expect(items[0].attempts).toBe(1);
    expect(items[0].lastError).toContain("Failed to fetch");
  });

  it("stops retrying a report the server will always refuse", async () => {
    const store = createMemoryQueueStore();
    const submit = vi.fn(async () => {
      throw new ShutdownTrackerApiError("forbidden", 403, "");
    });
    const queue = new OfflineProgressQueue({ store, newId: idFactory(), submit });

    await queue.enqueue(capture, "Weld repair");
    await queue.flush();
    await queue.flush();

    expect(submit).toHaveBeenCalledTimes(1);
    const items = await queue.list();
    expect(items[0].syncState).toBe("REJECTED");
    expect(items[0].lastError).toContain("cannot report progress");
  });

  it("lets a rejected report be sent again once the cause is fixed", async () => {
    const store = createMemoryQueueStore();
    let forbidden = true;
    const queue = new OfflineProgressQueue({
      store,
      newId: idFactory(),
      submit: async () => {
        if (forbidden) {
          throw new ShutdownTrackerApiError("forbidden", 403, "");
        }
        return serverRecord();
      }
    });

    const captured = await queue.enqueue(capture, "Weld repair");
    await queue.flush();
    expect((await queue.list())[0].syncState).toBe("REJECTED");

    forbidden = false;
    await queue.retry(captured.localId);
    await queue.flush();

    expect((await queue.list())[0].syncState).toBe("SYNCED");
  });

  it("sends reports in the order they were captured", async () => {
    const store = createMemoryQueueStore();
    const order: (string | null | undefined)[] = [];
    const queue = new OfflineProgressQueue({
      store,
      newId: idFactory(),
      submit: async (request) => {
        order.push(request.comment);
        return serverRecord();
      }
    });

    await queue.enqueue({ ...capture, comment: "first" }, "Task");
    await queue.enqueue({ ...capture, comment: "second" }, "Task");
    await queue.enqueue({ ...capture, comment: "third" }, "Task");
    await queue.flush();

    expect(order).toEqual(["first", "second", "third"]);
  });

  it("does not send anything twice when a flush overlaps a reconnect", async () => {
    const store = createMemoryQueueStore();
    const submit = vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5));
      return serverRecord();
    });
    const queue = new OfflineProgressQueue({ store, newId: idFactory(), submit });

    await queue.enqueue(capture, "Task");
    await Promise.all([queue.flush(), queue.flush()]);

    expect(submit).toHaveBeenCalledTimes(1);
  });

  it("keeps unsent reports when clearing, and removes only settled ones", async () => {
    const store = createMemoryQueueStore();
    let sendCount = 0;
    const queue = new OfflineProgressQueue({
      store,
      newId: idFactory(),
      submit: async () => {
        sendCount += 1;
        if (sendCount === 2) {
          throw new TypeError("Failed to fetch");
        }
        return serverRecord();
      }
    });

    await queue.enqueue({ ...capture, comment: "sent" }, "Task");
    await queue.enqueue({ ...capture, comment: "stuck" }, "Task");
    await queue.flush();

    const remaining = await queue.clearSettled();

    expect(remaining).toHaveLength(1);
    expect(remaining[0].request.comment).toBe("stuck");
  });

  it("counts what is still waiting and what needs attention", () => {
    const items = [
      { syncState: "PENDING" },
      { syncState: "SENDING" },
      { syncState: "SYNCED" },
      { syncState: "REJECTED" }
    ] as QueuedProgressUpdate[];

    expect(pendingCount(items)).toBe(2);
    expect(rejectedCount(items)).toBe(1);
    expect(isSettled(items[2])).toBe(true);
    expect(isSettled(items[0])).toBe(false);
  });

  it("treats a timeout or a rate limit as worth retrying, unlike a refusal", () => {
    expect(isPermanentRejection(new ShutdownTrackerApiError("bad", 400, ""))).toBe(true);
    expect(isPermanentRejection(new ShutdownTrackerApiError("forbidden", 403, ""))).toBe(true);
    expect(isPermanentRejection(new ShutdownTrackerApiError("timeout", 408, ""))).toBe(false);
    expect(isPermanentRejection(new ShutdownTrackerApiError("slow down", 429, ""))).toBe(false);
    expect(isPermanentRejection(new ShutdownTrackerApiError("server", 500, ""))).toBe(false);
    expect(isPermanentRejection(new TypeError("Failed to fetch"))).toBe(false);
  });

  it("generates a distinct local identifier per capture", () => {
    expect(newLocalId()).not.toBe(newLocalId());
  });
});

describe("field validation", () => {
  it("catches an impossible report on the device, not hours later in the queue", () => {
    expect(validateFieldProgress("40", "", "")).toBeNull();
    expect(validateFieldProgress("140", "", "")).toContain("between 0 and 100");
    expect(validateFieldProgress("", "", "2026-08-14T06:00")).toContain("actual start before");
    expect(validateFieldProgress("", "2026-08-14T08:00", "2026-08-14T06:00")).toContain(
      "cannot be before actual start"
    );
  });

  it("sends an instant rather than a zoneless local time", () => {
    const sent = toInstant("2026-08-14T08:00");

    expect(sent).not.toBeNull();
    expect(new Date(sent as string).toISOString()).toBe(sent);
    expect(toInstant("")).toBeNull();
  });
});
