import type { CriticalUpdateSubmitRequest, TaskProgressSubmitRequest } from "@shutdown-tracker/api-client";
import { ShutdownTrackerApiError } from "@shutdown-tracker/api-client";

/**
 * The offline execution queue.
 *
 * A shutdown happens inside plant with no usable signal. A field user must be able to report
 * where the work is and have it arrive later, without ever wondering whether it did. Three rules
 * follow from that:
 *
 * 1. A submission is written to durable storage before anything is sent. Nothing exists only
 *    in a form's state.
 * 2. Each submission carries an idempotency key generated once, at capture time, and reused
 *    on every retry. A retry over a bad connection returns the original record rather than
 *    reporting the same progress twice.
 * 3. Sync state is visible and never inferred. "Saved on this device" and "the server has it"
 *    are different facts, and a supervisor asking whether an update landed needs the second.
 *
 * The queue carries more than one kind of report. Progress and Critical Updates are captured in
 * the same places, under the same conditions, and both are safe to retry because the server pairs
 * an idempotency key with the project and returns the original submission for a repeated key. What
 * differs is only the endpoint and what a refusal means, so the kind travels on the item and the
 * mechanics stay in one place.
 */

export type SyncState =
  | "PENDING"
  | "SENDING"
  | "SYNCED"
  /** The server refused it. Retrying unchanged will not help; a person must intervene. */
  | "REJECTED";

/** What a queued item reports. The endpoint and the meaning of a refusal follow from it. */
export type QueuedSubmissionKind = "progress" | "critical-update";

type QueuedEnvelope = {
  /** Generated on the device. Identifies this capture across retries and restarts. */
  localId: string;
  idempotencyKey: string;
  /** What the report is about, for the sync list: a task name, or a work package name. */
  subject: string;
  capturedAt: string;
  syncState: SyncState;
  attempts: number;
  /** Set once the server accepts it, so the device can show the record it created. */
  serverId: string | null;
  lastError: string | null;
};

export type QueuedSubmission =
  | (QueuedEnvelope & { kind: "progress"; request: TaskProgressSubmitRequest })
  | (QueuedEnvelope & { kind: "critical-update"; request: CriticalUpdateSubmitRequest });

/**
 * Durable storage for the queue.
 *
 * An interface rather than a direct IndexedDB dependency: the sync rules are the part that
 * must be verifiable, and they are testable only if the store can be substituted.
 */
export type QueueStore = {
  readAll: () => Promise<QueuedSubmission[]>;
  writeAll: (items: QueuedSubmission[]) => Promise<void>;
};

/**
 * Sends one queued item and returns the record the server created.
 *
 * One function rather than one per kind: the queue decides *when* to send and what a failure
 * means, and knowing which endpoint a kind goes to is the caller's business.
 */
export type SubmitQueued = (item: QueuedSubmission) => Promise<{ id: string }>;

export type IdFactory = () => string;

/** Terminal sync states — nothing further will be attempted automatically. */
export function isSettled(item: QueuedSubmission) {
  return item.syncState === "SYNCED" || item.syncState === "REJECTED";
}

export function pendingCount(items: QueuedSubmission[]) {
  return items.filter((item) => item.syncState === "PENDING" || item.syncState === "SENDING").length;
}

export function rejectedCount(items: QueuedSubmission[]) {
  return items.filter((item) => item.syncState === "REJECTED").length;
}

/**
 * A refusal the server will repeat, versus a failure worth retrying.
 *
 * A 4xx other than 408 or 429 means the submission itself is unacceptable — the wrong role,
 * a task that is not in the snapshot, a value out of range. Retrying it forever would hide a
 * problem the reporter has to see. Anything else, including every network failure, stays
 * queued: the work was done and the report should still arrive.
 */
export function isPermanentRejection(error: unknown) {
  if (!(error instanceof ShutdownTrackerApiError)) {
    return false;
  }
  if (error.status === 408 || error.status === 429) {
    return false;
  }
  return error.status >= 400 && error.status < 500;
}

/**
 * What a refusal means to the person who made the capture.
 *
 * The status alone does not say it: a 404 on a progress report means the task left the snapshot,
 * and a 404 on a Critical Update means the work package is gone. Both are things the reporter can
 * act on, and neither is helped by being told "404".
 */
export function describeQueueError(error: unknown, kind: QueuedSubmissionKind = "progress") {
  if (error instanceof ShutdownTrackerApiError) {
    if (error.status === 403) {
      return kind === "progress"
        ? "Your role on this project cannot report progress on that task."
        : "Your role on this project cannot file a Critical Update.";
    }
    if (error.status === 404) {
      return kind === "progress"
        ? "That task is not part of the current schedule snapshot."
        : "That critical work package no longer exists.";
    }
    return `Rejected by the server (${error.status}): ${error.responseBody || error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "The submission could not be sent.";
}

/**
 * Reads an item stored before the queue carried more than one kind.
 *
 * A field user updating the app may have unsent reports on the device. Those were written with a
 * `taskName` and no `kind`, and dropping them — or leaving them without a kind and failing to
 * dispatch them — would lose work that was done and captured. They are progress reports; that is
 * all the queue used to hold.
 */
export function normalizeStoredSubmission(stored: unknown): QueuedSubmission | null {
  if (stored === null || typeof stored !== "object") {
    return null;
  }
  const item = stored as Partial<QueuedSubmission> & { taskName?: string };
  if (typeof item.localId !== "string" || item.request === undefined) {
    return null;
  }
  if (item.kind === "progress" || item.kind === "critical-update") {
    return item as QueuedSubmission;
  }
  return {
    ...(item as object),
    kind: "progress",
    subject: item.subject ?? item.taskName ?? "Task progress"
  } as QueuedSubmission;
}

/**
 * Captures a submission and flushes what is captured.
 *
 * Deliberately not a React hook: this is the part that must keep working the same way whether
 * the screen is mounted, and the part worth testing directly.
 */
export class OfflineSubmissionQueue {
  private readonly store: QueueStore;
  private readonly submit: SubmitQueued;
  private readonly newId: IdFactory;
  private readonly clock: () => string;
  private flushing = false;

  constructor(options: {
    store: QueueStore;
    submit: SubmitQueued;
    newId: IdFactory;
    clock?: () => string;
  }) {
    this.store = options.store;
    this.submit = options.submit;
    this.newId = options.newId;
    this.clock = options.clock ?? (() => new Date().toISOString());
  }

  list() {
    return this.store.readAll();
  }

  /**
   * Records a submission on the device.
   *
   * Returns as soon as it is stored, without waiting for the network. The report is safe at
   * this point; sending it is a separate concern the reporter can watch in the sync queue.
   */
  async enqueueProgress(
    request: Omit<TaskProgressSubmitRequest, "idempotencyKey" | "offlineLocalId">,
    taskName: string
  ): Promise<QueuedSubmission> {
    return this.enqueue("progress", request, taskName);
  }

  /**
   * Records a Critical Update on the device.
   *
   * Queued on the same terms as progress, because the server pairs the idempotency key with the
   * project and returns the original submission for a repeated key — so a retry over a bad
   * connection cannot produce a second report on the package.
   */
  async enqueueCriticalUpdate(
    request: Omit<CriticalUpdateSubmitRequest, "idempotencyKey" | "offlineLocalId">,
    workPackageName: string
  ): Promise<QueuedSubmission> {
    return this.enqueue("critical-update", request, workPackageName);
  }

  private async enqueue(
    kind: QueuedSubmissionKind,
    request: object,
    subject: string
  ): Promise<QueuedSubmission> {
    const localId = this.newId();
    const idempotencyKey = this.newId();
    const item = {
      localId,
      idempotencyKey,
      kind,
      // The key travels with the request so a retry is recognised as the same capture.
      request: { ...request, idempotencyKey, offlineLocalId: localId },
      subject,
      capturedAt: this.clock(),
      syncState: "PENDING",
      attempts: 0,
      serverId: null,
      lastError: null
    } as QueuedSubmission;

    const items = await this.store.readAll();
    await this.store.writeAll([...items, item]);
    return item;
  }

  /**
   * Sends everything still waiting.
   *
   * Sequential rather than parallel: submissions for one task must reach the server in the
   * order the reporter made them, or a correction can land before the report it corrects.
   * Re-entry is guarded so a reconnect event during a flush does not send anything twice.
   */
  async flush(): Promise<QueuedSubmission[]> {
    if (this.flushing) {
      return this.store.readAll();
    }
    this.flushing = true;

    try {
      let items = await this.store.readAll();

      for (const item of items) {
        if (item.syncState !== "PENDING") {
          continue;
        }

        // The outcome is written on top of the sending item, not the item as it was read, so
        // the attempt count is raised exactly once and nothing recorded at send time is lost.
        const sending: QueuedSubmission = {
          ...item,
          syncState: "SENDING",
          attempts: item.attempts + 1
        };
        items = replace(items, sending);
        await this.store.writeAll(items);

        try {
          const record = await this.submit(sending);
          items = replace(items, {
            ...sending,
            syncState: "SYNCED",
            serverId: record.id,
            lastError: null
          });
        } catch (error) {
          items = replace(items, {
            ...sending,
            // A rejection is final; anything else goes back to pending for the next attempt.
            syncState: isPermanentRejection(error) ? "REJECTED" : "PENDING",
            lastError: describeQueueError(error, sending.kind)
          });
        }

        await this.store.writeAll(items);
      }

      return items;
    } finally {
      this.flushing = false;
    }
  }

  /** Clears settled entries. Anything unsent is kept, whatever the reporter asked for. */
  async clearSettled(): Promise<QueuedSubmission[]> {
    const items = await this.store.readAll();
    const remaining = items.filter((item) => !isSettled(item));
    await this.store.writeAll(remaining);
    return remaining;
  }

  /** Returns a rejected submission to the queue, for use after the cause is fixed. */
  async retry(localId: string): Promise<QueuedSubmission[]> {
    const items = await this.store.readAll();
    const next = items.map((item) =>
      item.localId === localId && item.syncState === "REJECTED"
        ? { ...item, syncState: "PENDING" as SyncState, lastError: null }
        : item
    );
    await this.store.writeAll(next);
    return next;
  }
}

function replace(items: QueuedSubmission[], updated: QueuedSubmission) {
  return items.map((item) => (item.localId === updated.localId ? updated : item));
}

/** An in-memory store. Used by tests, and as the fallback when IndexedDB is unavailable. */
export function createMemoryQueueStore(initial: QueuedSubmission[] = []): QueueStore {
  let items = [...initial];
  return {
    readAll: async () => [...items],
    writeAll: async (next) => {
      items = [...next];
    }
  };
}
