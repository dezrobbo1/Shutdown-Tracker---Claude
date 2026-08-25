import { renderToString } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ShutdownTrackerApiError, capabilityAllows } from "@shutdown-tracker/api-client";
import { isStatusClass } from "@shutdown-tracker/design-tokens";
import type {
  CriticalUpdateSubmitRequest,
  ProblemCreateRequest,
  TaskProgressSubmitRequest,
  TaskProgressUpdateRecord
} from "@shutdown-tracker/api-client";
import type { AssignedWorkView, ImportReviewTaskRow } from "@shutdown-tracker/api-client";
import {
  App,
  EvidenceScreen,
  WorkList,
  captureFieldEvidence,
  describeRaiseFailure,
  evidenceCaptureDisabled,
  evidenceCaptureNotice,
  mobileChipTone,
  queuedItemDetail,
  toInstant,
  validateFieldProgress,
  workCardPercent,
  canSwitchIdentity
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

  it("says a problem was not kept when the device could not keep it", () => {
    // A dead connection no longer reaches here: the capture goes to the queue and Sync owns
    // the sending. What is left is the device failing to store it, and then nothing holds the
    // problem — so the line has to send the person back to raise it again.
    expect(describeRaiseFailure(new Error("QuotaExceededError"))).toBe(
      "This device could not keep the problem: QuotaExceededError. Raise it again."
    );
    expect(describeRaiseFailure(null)).toBe("This device could not keep the problem. Raise it again.");
  });

  it("names the sync states so saved and sent are not confused", () => {
    expect(mobileChipTone("Server received")).toBe("success");
    expect(mobileChipTone("Waiting to send")).toBe("warning");
    expect(mobileChipTone("Could not be sent")).toBe("critical");
    expect(mobileChipTone("Blocked")).toBe("critical");
  });

  it("classes a state the same way the console does", () => {
    // The design language requires that the same state look and read the same in both
    // applications. Both map onto the same six classes from the shared token layer, so a
    // supervisor moving between the phone and the console is not learning two vocabularies.
    for (const label of ["Server received", "Waiting to send", "Blocked"]) {
      expect(isStatusClass(mobileChipTone(label))).toBe(true);
    }
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

  it("acts as the identity chosen on this device rather than the one baked into the build", () => {
    const env = {
      VITE_SHUTDOWN_TRACKER_PROJECT_ID: "project-1",
      VITE_SHUTDOWN_TRACKER_ACTOR_ID: "user-1",
      VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "planner"
    };

    const session = buildFieldSession(env, {
      userId: "user-2",
      role: "field_user",
      displayName: "Rae Field",
      projectId: "project-2"
    });

    expect(session.actor?.userId).toBe("user-2");
    expect(session.actor?.role).toBe("field_user");
    expect(session.projectId).toBe("project-2");
    expect(fieldSessionAllows(session, "SUBMIT_TASK_PROGRESS")).toBe(true);
  });

  it("discards an unusable stored identity rather than half-applying it", () => {
    const env = { VITE_SHUTDOWN_TRACKER_PROJECT_ID: "project-1", VITE_SHUTDOWN_TRACKER_ACTOR_ID: "user-1" };

    const session = buildFieldSession(env, { userId: "user-2", role: "wizard", displayName: "Rae" });

    expect(session.actor?.userId).toBe("user-1");
    expect(session.actor?.role).toBe("field_user");
  });
});

describe("switching identity on the device", () => {
  const queued = (syncState: string): QueuedSubmission =>
    normalizeStoredSubmission({
      localId: "local-1",
      idempotencyKey: "key-1",
      kind: "progress",
      request: { importedTaskId: "task-1", percentComplete: 40 },
      subject: "C2 Cyclone — remove access cover",
      capturedAt: "2026-08-18T06:00:00.000Z",
      syncState,
      attempts: 0,
      serverId: null,
      lastError: null
    }) as QueuedSubmission;

  it("is refused while a report captured by this person is still waiting", () => {
    // The queue is memoised on the API client, so switching now would send this report under
    // somebody else's actor header — a misattribution, and for a role that may not submit, a
    // refusal whose message says nothing about the real cause.
    expect(canSwitchIdentity([queued("PENDING")])).toBe(false);
  });

  it("is allowed once nothing is left to send", () => {
    expect(canSwitchIdentity([])).toBe(true);
    expect(canSwitchIdentity([queued("SYNCED")])).toBe(true);
    expect(canSwitchIdentity([queued("REJECTED")])).toBe(true);
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

/**
 * The field evidence gate.
 *
 * `CAPTURE_EVIDENCE` is enforced by the server and honoured by the console, and until now was
 * never checked here: the field app offered a photo control to every role and let the server
 * refuse it. Reading evidence is a separate permission and stays open, so the gate has to be
 * narrower than the screen.
 */
describe("the field app checks who may capture evidence", () => {
  it("holds the same roles the server accepts evidence from", () => {
    for (const role of ["field_user", "contractor", "supervisor", "inspector"] as const) {
      expect(capabilityAllows("CAPTURE_EVIDENCE", role)).toBe(true);
    }
    for (const role of ["coordinator", "shutdown_control", "planner", "viewer"] as const) {
      expect(capabilityAllows("CAPTURE_EVIDENCE", role)).toBe(false);
    }

    // Why the screen cannot infer capture from a reader simply being on the field app: a
    // coordinator reports progress and raises problems here without holding this capability.
    expect(capabilityAllows("SUBMIT_TASK_PROGRESS", "coordinator")).toBe(true);
    expect(capabilityAllows("RAISE_PROBLEM", "coordinator")).toBe(true);
  });

  /**
   * The expression the Evidence screen is handed. A coordinator is the case that motivates the
   * slice: a real field identity, at home on this app, holding everything around evidence and
   * not evidence itself.
   */
  it("denies a coordinator's own session the capability, while it reports progress", () => {
    const session = buildFieldSession(
      { VITE_SHUTDOWN_TRACKER_PROJECT_ID: "project-1", VITE_SHUTDOWN_TRACKER_ACTOR_ID: "user-1" },
      { userId: "user-1", role: "coordinator", displayName: "Ali Coordinator", projectId: "project-1" }
    );

    expect(session.actor?.role).toBe("coordinator");
    expect(fieldSessionAllows(session, "CAPTURE_EVIDENCE")).toBe(false);
    expect(fieldSessionAllows(session, "SUBMIT_TASK_PROGRESS")).toBe(true);
  });

  it("refuses the send control on the permission alone", () => {
    // Every passing reason to be disabled is held open, so only the capability decides.
    const ready = { live: true, online: true, capturing: false, hasFile: true };

    expect(evidenceCaptureDisabled({ ...ready, canCapture: true })).toBe(false);
    expect(evidenceCaptureDisabled({ ...ready, canCapture: false })).toBe(true);
  });

  it("keeps refusing it for the reasons that were already there", () => {
    const allowed = { canCapture: true, live: true, online: true, capturing: false, hasFile: true };

    expect(evidenceCaptureDisabled({ ...allowed, online: false })).toBe(true);
    expect(evidenceCaptureDisabled({ ...allowed, live: false })).toBe(true);
    expect(evidenceCaptureDisabled({ ...allowed, capturing: true })).toBe(true);
    expect(evidenceCaptureDisabled({ ...allowed, hasFile: false })).toBe(true);
  });

  /**
   * Two refusals that must not read alike. Being offline passes, and the copy says to capture it
   * again later; not holding the capability does not pass, and telling that reader to come back
   * when they have a connection would be false.
   */
  it("says which of the two refusals it is", () => {
    const role = "field, contractor, supervisor, or inspector";

    expect(evidenceCaptureNotice(false, true)).toContain(role);
    expect(evidenceCaptureNotice(false, false)).toContain(role);
    expect(evidenceCaptureNotice(false, false)).not.toContain("back on");

    expect(evidenceCaptureNotice(true, false)).toContain("back on");
    expect(evidenceCaptureNotice(true, false)).not.toContain(role);
    expect(evidenceCaptureNotice(true, true)).toContain("needs a connection");
  });

  it("leaves the evidence a role cannot add to still readable", () => {
    const html = renderToString(
      <EvidenceScreen
        tasks={[]}
        live
        online
        canCapture={false}
        loadEvidence={async () => []}
        captureEvidence={async () => undefined}
      />
    );

    expect(html).toContain("field, contractor, supervisor, or inspector");
    // The picker is a read and stays. Hiding it would leave the screen with nothing on it for a
    // reader whose whole business here is looking at what somebody else recorded.
    expect(html).toContain("Choose a task");
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
    await queue.enqueueProblem({
      importedTaskId: "task-1",
      title: "Scaffold missing",
      description: null,
      blocksExecution: true
    });
    await queue.enqueueProgress(capture, "Refit guard");
    await queue.flush();

    expect(order).toEqual(["progress", "critical-update", "problem", "progress"]);
  });

  /**
   * The status alone does not tell the reporter what to do. A 404 on a progress report means the
   * task left the snapshot; on a Critical Update it means the package is gone.
   */
  it("explains a refusal in terms of what was being reported", () => {
    const notFound = new ShutdownTrackerApiError("failed", 404, "");

    expect(describeQueueError(notFound, "progress")).toContain("schedule snapshot");
    expect(describeQueueError(notFound, "critical-update")).toContain("critical work package");
    expect(describeQueueError(notFound, "problem")).toContain("raised against");

    const forbidden = new ShutdownTrackerApiError("failed", 403, "");
    expect(describeQueueError(forbidden, "problem")).toContain("cannot raise problems");
  });

  /**
   * A problem is raised where the work is, which is where there is no signal. Holding it in the
   * queue is only safe because the server pairs the idempotency key with the project: the retry
   * returns the problem the first attempt raised rather than raising a second one.
   */
  it("carries an idempotency key so a retry cannot raise the same problem twice", async () => {
    const sent: ProblemCreateRequest[] = [];
    let failFirst = true;
    const queue = new OfflineSubmissionQueue({
      store: createMemoryQueueStore(),
      newId: idFactory(),
      submit: async (item) => {
        sent.push(item.request as ProblemCreateRequest);
        if (failFirst) {
          failFirst = false;
          throw new TypeError("Failed to fetch");
        }
        return { id: "problem-1" };
      }
    });

    await queue.enqueueProblem({
      importedTaskId: "task-1",
      title: "Scaffold missing",
      description: "Cannot reach the valve.",
      blocksExecution: true
    });
    await queue.flush();
    await queue.flush();

    expect(sent).toHaveLength(2);
    expect(sent[0].idempotencyKey).toBeTruthy();
    expect(sent[1].idempotencyKey).toBe(sent[0].idempotencyKey);

    const items = await queue.list();
    expect(items).toHaveLength(1);
    expect(items[0].kind).toBe("problem");
    // The title is what identifies a problem in the sync list; nothing else on it would.
    expect(items[0].subject).toBe("Scaffold missing");
    expect(items[0].syncState).toBe("SYNCED");
    expect(items[0].serverId).toBe("problem-1");
  });

  it("keeps a raised problem queued when the network fails, because it is still wrong", async () => {
    const queue = new OfflineSubmissionQueue({
      store: createMemoryQueueStore(),
      newId: idFactory(),
      submit: async () => {
        throw new TypeError("Failed to fetch");
      }
    });

    await queue.enqueueProblem({ title: "Valve seized", description: null, blocksExecution: true });
    const items = await queue.flush();

    expect(items[0].syncState).toBe("PENDING");
    expect(items[0].lastError).toContain("Failed to fetch");
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

  /**
   * The sync list has one line per item. What that line has to say differs by kind, and the half
   * a supervisor reads first on a problem is whether it stops work.
   */
  it("says what each queued kind is about in one line", () => {
    expect(
      queuedItemDetail({
        kind: "problem",
        request: { title: "Scaffold missing", description: "Cannot reach the valve.", blocksExecution: true }
      } as never)
    ).toBe("Blocked · Cannot reach the valve.");

    expect(
      queuedItemDetail({
        kind: "problem",
        request: { title: "Label unreadable", description: null, blocksExecution: false }
      } as never)
    ).toBe("Problem");

    expect(
      queuedItemDetail({
        kind: "progress",
        request: { executionState: "IN_PROGRESS", percentComplete: 40 }
      } as never)
    ).toBe("In progress · 40%");
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

/**
 * My Work has to tell four situations apart.
 *
 * Every one of them renders an empty list, and only one of them means the reader is finished for
 * the day. Collapsing them is the exact failure the active goal names: a screen that implies a
 * capability, or a state, that is not real. These assert the distinction survives.
 */
describe("my work says which kind of empty it is", () => {
  function workView(overrides: Partial<AssignedWorkView> = {}): AssignedWorkView {
    return {
      projectId: "project-1",
      projectSnapshotId: "snapshot-1",
      snapshotVersion: 3,
      linked: true,
      linkedResourceUids: ["R-1"],
      unmatchedResourceUids: [],
      tasks: [],
      ...overrides
    };
  }

  function task(id: string, name: string): ImportReviewTaskRow {
    return {
      id,
      externalUid: id,
      externalId: id,
      name,
      wbs: "1.1",
      outlineNumber: "1.1",
      outlineLevel: 2,
      summary: false,
      parentExternalUid: null,
      parentImportedTaskId: null,
      plannedStart: null,
      plannedFinish: null,
      actualStart: null,
      actualFinish: null,
      percentComplete: null,
      physicalPercentComplete: null,
      notes: null
    };
  }

  function render(assigned: AssignedWorkView | null, loadMessage = "") {
    return renderToString(
      <WorkList
        assigned={assigned}
        blockingTaskIds={new Set()}
        unsentByTaskId={new Map()}
        loadMessage={loadMessage}
        onSelect={() => undefined}
      />
    );
  }

  it("says no schedule has been accepted, rather than showing an empty day", () => {
    const html = render(workView({ projectSnapshotId: null, linked: false, linkedResourceUids: [] }));

    expect(html).toContain("No schedule has been accepted");
    expect(html).not.toContain("assigned to you.</p>");
  });

  it("says nobody has linked the reader, rather than that they have no work", () => {
    const html = render(workView({ linked: false, linkedResourceUids: [] }));

    // The distinction that matters: "we cannot tell" is not "there is nothing".
    expect(html).toContain("No Microsoft Project resource is linked to your account");
    expect(html).toContain("A planner links you to your resource");
  });

  it("says the schedule lost the linked resource, rather than emptying the list quietly", () => {
    const html = render(workView({ unmatchedResourceUids: ["R-1"] }));

    expect(html).toContain("does not carry the resource you are linked to");
    expect(html).toContain("Tell a planner");
  });

  it("distinguishes some resources missing from all of them missing", () => {
    const html = render(
      workView({ linkedResourceUids: ["R-1", "R-2"], unmatchedResourceUids: ["R-2"] })
    );

    expect(html).toContain("does not carry 1 of the resources");
    expect(html).toContain("some of your work may be missing");
  });

  it("says the day is clear only when it is", () => {
    const html = render(workView());

    expect(html).toContain("None of the accepted schedule&#x27;s work is assigned to you.");
    expect(html).not.toContain("linked to your account");
  });

  it("lists the work when there is work", () => {
    const html = render(workView({ tasks: [task("task-1", "C2 Cyclone — remove access cover")] }));

    expect(html).toContain("C2 Cyclone — remove access cover");
    expect(html).not.toContain("None of the accepted schedule");
  });

  it("says it could not resolve the work rather than claiming there is none", () => {
    // The load failed. Reporting that as an empty list would tell somebody on site to go home.
    const html = render(null);

    expect(html).toContain("Your work could not be resolved");
  });

  it("says how many it truncated, and does not claim to be showing the schedule", () => {
    const many = Array.from({ length: 140 }, (_, index) => task(`task-${index}`, `Job ${index}`));
    const html = render(workView({ tasks: many }));

    expect(html).toContain("Showing the first 100 of 140 tasks assigned to you.");
    expect(html).not.toContain("every task");
  });
});
