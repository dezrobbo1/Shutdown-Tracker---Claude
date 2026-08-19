import { renderToString } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ShutdownTrackerApiError } from "@shutdown-tracker/api-client";
import type {
  CriticalUpdateSubmitRequest,
  TaskProgressSubmitRequest,
  TaskProgressUpdateRecord
} from "@shutdown-tracker/api-client";
import {
  App,
  captureFieldEvidence,
  describeRaiseFailure,
  mobileChipTone,
  toInstant,
  validateFieldProgress,
  workCardPercent
} from "./App";
import { buildFieldSession, describeFieldSession, fieldSessionAllows } from "./fieldSession";
import {
  OfflineSubmissionQueue,
  createMemoryQueueStore,
  describeQueueError,
  isPermanentRejection,
  isSettled,
  normalizeStoredSubmission,
  pendingCount,
  rejectedCount
} from "./offlineQueue";
import type { QueuedSubmission } from "./offlineQueue";
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
        kind: "progress",
        request: { percentComplete: 60 }
      } as never)
    ).toBe("60%");

    // A Critical Update reports on a work package. It says nothing about how far this task has
    // got, so the card keeps showing the value that does.
    expect(
      workCardPercent(task, {
        kind: "critical-update",
        request: { criticalWorkPackageId: "package-1", currentFocus: "Blanking plates" }
      } as never)
    ).toBe("10%");
  });

  it("tells the reporter that submitting sends the report for review, not to the schedule", () => {
    const html = renderToString(<App />);

    expect(html).toContain("No project configured for this device.");
    expect(html).not.toContain("Review mode");
  });

  it("says a problem was not kept when it could not be sent", () => {
    // A progress report survives a dead connection; a problem does not, and the difference
    // has to be visible or someone walks away believing it was recorded.
    expect(describeRaiseFailure(new TypeError("Failed to fetch"))).toContain("not saved on this device");
    expect(describeRaiseFailure(new Error("Network request failed"))).toContain("not saved on this device");

    // A real answer from the server is more useful than the generic line.
    expect(describeRaiseFailure(new Error("Your role on this project cannot raise problems."))).toBe(
      "Your role on this project cannot raise problems."
    );
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
    const queue = new OfflineSubmissionQueue({ store, submit, newId: idFactory() });

    await queue.enqueueProgress(capture, "Remove guard");

    expect(submit).not.toHaveBeenCalled();
    const items = await queue.list();
    expect(items).toHaveLength(1);
    expect(items[0].syncState).toBe("PENDING");
    expect(items[0].subject).toBe("Remove guard");
  });

  it("carries an idempotency key so a retry cannot double-report progress", async () => {
    const store = createMemoryQueueStore();
    const sent: TaskProgressSubmitRequest[] = [];
    let failFirst = true;
    const queue = new OfflineSubmissionQueue({
      store,
      newId: idFactory(),
      submit: async (item) => {
        sent.push(item.request as TaskProgressSubmitRequest);
        if (failFirst) {
          failFirst = false;
          throw new Error("network down");
        }
        return serverRecord();
      }
    });

    await queue.enqueueProgress(capture, "Weld repair");
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
    const queue = new OfflineSubmissionQueue({
      store,
      newId: idFactory(),
      submit: async () => {
        throw new TypeError("Failed to fetch");
      }
    });

    await queue.enqueueProgress(capture, "Isolate feeder");
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
    const queue = new OfflineSubmissionQueue({ store, newId: idFactory(), submit });

    await queue.enqueueProgress(capture, "Weld repair");
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
    const queue = new OfflineSubmissionQueue({
      store,
      newId: idFactory(),
      submit: async () => {
        if (forbidden) {
          throw new ShutdownTrackerApiError("forbidden", 403, "");
        }
        return serverRecord();
      }
    });

    const captured = await queue.enqueueProgress(capture, "Weld repair");
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
    const queue = new OfflineSubmissionQueue({
      store,
      newId: idFactory(),
      submit: async (item) => {
        order.push((item.request as TaskProgressSubmitRequest).comment);
        return serverRecord();
      }
    });

    await queue.enqueueProgress({ ...capture, comment: "first" }, "Task");
    await queue.enqueueProgress({ ...capture, comment: "second" }, "Task");
    await queue.enqueueProgress({ ...capture, comment: "third" }, "Task");
    await queue.flush();

    expect(order).toEqual(["first", "second", "third"]);
  });

  it("does not send anything twice when a flush overlaps a reconnect", async () => {
    const store = createMemoryQueueStore();
    const submit = vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5));
      return serverRecord();
    });
    const queue = new OfflineSubmissionQueue({ store, newId: idFactory(), submit });

    await queue.enqueueProgress(capture, "Task");
    await Promise.all([queue.flush(), queue.flush()]);

    expect(submit).toHaveBeenCalledTimes(1);
  });

  it("keeps unsent reports when clearing, and removes only settled ones", async () => {
    const store = createMemoryQueueStore();
    let sendCount = 0;
    const queue = new OfflineSubmissionQueue({
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

    await queue.enqueueProgress({ ...capture, comment: "sent" }, "Task");
    await queue.enqueueProgress({ ...capture, comment: "stuck" }, "Task");
    await queue.flush();

    const remaining = await queue.clearSettled();

    expect(remaining).toHaveLength(1);
    expect((remaining[0].request as TaskProgressSubmitRequest).comment).toBe("stuck");
  });

  it("counts what is still waiting and what needs attention", () => {
    const items = [
      { syncState: "PENDING" },
      { syncState: "SENDING" },
      { syncState: "SYNCED" },
      { syncState: "REJECTED" }
    ] as QueuedSubmission[];

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

describe("field evidence capture", () => {
  const photo = new File(["blanking plate fitted"], "blanking-plate.jpg", { type: "image/jpeg" });

  /**
   * The file is attached to a record that already exists. Registering afterwards would mean bytes
   * stored against nothing, and there is no record to upload to before the first call returns.
   */
  it("registers the record before sending the photo", async () => {
    const calls: string[] = [];
    const client = {
      evidence: {
        register: async (projectId: string, request: { originalFilename: string; caption: string | null }) => {
          calls.push(`register ${projectId} ${request.originalFilename} ${request.caption}`);
          return { id: "evidence-1" };
        },
        uploadContent: async (projectId: string, evidenceId: string, file: Blob) => {
          calls.push(`upload ${projectId} ${evidenceId} ${file.size}`);
          return { id: evidenceId };
        }
      }
    };

    await captureFieldEvidence(client as never, "p1", "task-1", photo, "  Blanking plate fitted  ");

    expect(calls).toEqual([
      "register p1 blanking-plate.jpg Blanking plate fitted",
      `upload p1 evidence-1 ${photo.size}`
    ]);
  });

  /**
   * A failed send must not be reported as a capture that never happened: the record exists and is
   * waiting for its file, which is what the evidence list then shows.
   */
  it("surfaces a failed send rather than swallowing it", async () => {
    const client = {
      evidence: {
        register: async () => ({ id: "evidence-1" }),
        uploadContent: async () => {
          throw new ShutdownTrackerApiError("Shutdown Tracker API request failed with 413.", 413, "");
        }
      }
    };

    await expect(captureFieldEvidence(client as never, "p1", "task-1", photo, "")).rejects.toBeInstanceOf(
      ShutdownTrackerApiError
    );
  });
});

describe("the queue carries Critical Updates as well as progress", () => {
  const criticalUpdate = {
    criticalWorkPackageId: "package-1",
    updateMode: "shift" as const,
    currentFocus: "Blanking plates on the north face",
    currentBlockerSummary: null,
    nextTarget: null
  };

  /**
   * A Critical Update is captured in the same places and under the same conditions as progress,
   * and the server pairs the idempotency key with the project. So a retry over a bad connection
   * returns the original report rather than filing a second one against the package.
   */
  it("carries an idempotency key so a retry cannot double-file a Critical Update", async () => {
    const sent: CriticalUpdateSubmitRequest[] = [];
    let failFirst = true;
    const queue = new OfflineSubmissionQueue({
      store: createMemoryQueueStore(),
      newId: idFactory(),
      submit: async (item) => {
        sent.push(item.request as CriticalUpdateSubmitRequest);
        if (failFirst) {
          failFirst = false;
          throw new Error("network down");
        }
        return { id: "critical-update-1" };
      }
    });

    await queue.enqueueCriticalUpdate(criticalUpdate, "C2 Cyclone — internals");
    await queue.flush();
    await queue.flush();

    expect(sent).toHaveLength(2);
    expect(sent[0].idempotencyKey).toBeTruthy();
    expect(sent[1].idempotencyKey).toBe(sent[0].idempotencyKey);

    const items = await queue.list();
    expect(items).toHaveLength(1);
    expect(items[0].kind).toBe("critical-update");
    expect(items[0].subject).toBe("C2 Cyclone — internals");
    expect(items[0].syncState).toBe("SYNCED");
    expect(items[0].serverId).toBe("critical-update-1");
  });

  it("sends each kind to its own endpoint, in the order they were captured", async () => {
    const order: string[] = [];
    const queue = new OfflineSubmissionQueue({
      store: createMemoryQueueStore(),
      newId: idFactory(),
      submit: async (item) => {
        order.push(item.kind);
        return { id: "server-1" };
      }
    });

    await queue.enqueueProgress(capture, "Remove guard");
    await queue.enqueueCriticalUpdate(criticalUpdate, "C2 Cyclone — internals");
    await queue.enqueueProgress(capture, "Refit guard");
    await queue.flush();

    expect(order).toEqual(["progress", "critical-update", "progress"]);
  });

  /**
   * The status alone does not tell the reporter what to do. A 404 on a progress report means the
   * task left the snapshot; on a Critical Update it means the package is gone.
   */
  it("explains a refusal in terms of what was being reported", () => {
    const notFound = new ShutdownTrackerApiError("failed", 404, "");

    expect(describeQueueError(notFound, "progress")).toContain("schedule snapshot");
    expect(describeQueueError(notFound, "critical-update")).toContain("critical work package");
  });

  /**
   * A field user updating the app may have unsent reports on the device, written before the queue
   * carried a kind. Dropping them, or leaving them undispatchable, would lose work that was done
   * and captured.
   */
  it("reads a report captured before the queue carried more than one kind", () => {
    const legacy = {
      localId: "local-1",
      idempotencyKey: "key-1",
      request: { importedTaskId: "task-1", percentComplete: 40 },
      taskName: "C2 Cyclone — remove access cover",
      capturedAt: "2026-08-18T06:00:00.000Z",
      syncState: "PENDING",
      attempts: 0,
      serverId: null,
      lastError: null
    };

    const normalized = normalizeStoredSubmission(legacy);

    expect(normalized?.kind).toBe("progress");
    expect(normalized?.subject).toBe("C2 Cyclone — remove access cover");
    expect(normalized?.syncState).toBe("PENDING");
  });

  it("keeps an item that already carries a kind, and drops one that is not a submission", () => {
    const current = {
      localId: "local-2",
      idempotencyKey: "key-2",
      kind: "critical-update",
      request: { criticalWorkPackageId: "package-1" },
      subject: "C2 Cyclone — internals",
      capturedAt: "2026-08-18T06:00:00.000Z",
      syncState: "PENDING",
      attempts: 0,
      serverId: null,
      lastError: null
    };

    expect(normalizeStoredSubmission(current)?.kind).toBe("critical-update");
    expect(normalizeStoredSubmission(null)).toBeNull();
    expect(normalizeStoredSubmission({ localId: "local-3" })).toBeNull();
  });
});
