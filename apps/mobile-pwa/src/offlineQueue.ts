import type { TaskProgressSubmitRequest, TaskProgressUpdateRecord } from "@shutdown-tracker/api-client";
import { ShutdownTrackerApiError } from "@shutdown-tracker/api-client";

/**
 * The offline execution queue.
 *
 * A shutdown happens inside plant with no usable signal. A field user must be able to report
 * progress where the work is and have it arrive later, without ever wondering whether it did.
 * Three rules follow from that:
 *
 * 1. A submission is written to durable storage before anything is sent. Nothing exists only
 *    in a form's state.
 * 2. Each submission carries an idempotency key generated once, at capture time, and reused
 *    on every retry. A retry over a bad connection returns the original record rather than
 *    reporting the same progress twice.
 * 3. Sync state is visible and never inferred. "Saved on this device" and "the server has it"
 *    are different facts, and a supervisor asking whether an update landed needs the second.
 */

export type SyncState =
  | "PENDING"
  | "SENDING"
  | "SYNCED"
  /** The server refused it. Retrying unchanged will not help; a person must intervene. */
  | "REJECTED";

export type QueuedProgressUpdate = {
  /** Generated on the device. Identifies this capture across retries and restarts. */
  localId: string;
  idempotencyKey: string;
  request: TaskProgressSubmitRequest;
  taskName: string;
  capturedAt: string;
  syncState: SyncState;
  attempts: number;
  /** Set once the server accepts it, so the device can show the record it created. */
  serverId: string | null;
  lastError: string | null;
};

/**
 * Durable storage for the queue.
 *
 * An interface rather than a direct IndexedDB dependency: the sync rules are the part that
 * must be verifiable, and they are testable only if the store can be substituted.
 */
export type QueueStore = {
  readAll: () => Promise<QueuedProgressUpdate[]>;
  writeAll: (items: QueuedProgressUpdate[]) => Promise<void>;
};

export type SubmitProgress = (request: TaskProgressSubmitRequest) => Promise<TaskProgressUpdateRecord>;

export type IdFactory = () => string;

/** Terminal sync states — nothing further will be attempted automatically. */
export function isSettled(item: QueuedProgressUpdate) {
  return item.syncState === "SYNCED" || item.syncState === "REJECTED";
}

export function pendingCount(items: QueuedProgressUpdate[]) {
  return items.filter((item) => item.syncState === "PENDING" || item.syncState === "SENDING").length;
}

export function rejectedCount(items: QueuedProgressUpdate[]) {
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

export function describeQueueError(error: unknown) {
  if (error instanceof ShutdownTrackerApiError) {
    if (error.status === 403) {
      return "Your role on this project cannot report progress on that task.";
    }
    if (error.status === 404) {
      return "That task is not part of the current schedule snapshot.";
    }
    return `Rejected by the server (${error.status}): ${error.responseBody || error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "The submission could not be sent.";
}

/**
 * Captures a submission and flushes what is captured.
 *
 * Deliberately not a React hook: this is the part that must keep working the same way whether
 * the screen is mounted, and the part worth testing directly.
 */
export class OfflineProgressQueue {
  private readonly store: QueueStore;
  private readonly submit: SubmitProgress;
  private readonly newId: IdFactory;
  private readonly clock: () => string;
  private flushing = false;

  constructor(options: {
    store: QueueStore;
    submit: SubmitProgress;
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
  async enqueue(
    request: Omit<TaskProgressSubmitRequest, "idempotencyKey" | "offlineLocalId">,
    taskName: string
  ): Promise<QueuedProgressUpdate> {
    const localId = this.newId();
    const item: QueuedProgressUpdate = {
      localId,
      idempotencyKey: this.newId(),
      request: { ...request, idempotencyKey: null, offlineLocalId: localId },
      taskName,
      capturedAt: this.clock(),
      syncState: "PENDING",
      attempts: 0,
      serverId: null,
      lastError: null
    };
    // The key travels with the request so a retry is recognised as the same capture.
    item.request.idempotencyKey = item.idempotencyKey;

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
  async flush(): Promise<QueuedProgressUpdate[]> {
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

        items = replace(items, { ...item, syncState: "SENDING", attempts: item.attempts + 1 });
        await this.store.writeAll(items);

        try {
          const record = await this.submit(item.request);
          items = replace(items, {
            ...item,
            attempts: item.attempts + 1,
            syncState: "SYNCED",
            serverId: record.id,
            lastError: null
          });
        } catch (error) {
          items = replace(items, {
            ...item,
            attempts: item.attempts + 1,
            // A rejection is final; anything else goes back to pending for the next attempt.
            syncState: isPermanentRejection(error) ? "REJECTED" : "PENDING",
            lastError: describeQueueError(error)
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
  async clearSettled(): Promise<QueuedProgressUpdate[]> {
    const items = await this.store.readAll();
    const remaining = items.filter((item) => !isSettled(item));
    await this.store.writeAll(remaining);
    return remaining;
  }

  /** Returns a rejected submission to the queue, for use after the cause is fixed. */
  async retry(localId: string): Promise<QueuedProgressUpdate[]> {
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

function replace(items: QueuedProgressUpdate[], updated: QueuedProgressUpdate) {
  return items.map((item) => (item.localId === updated.localId ? updated : item));
}

/** An in-memory store. Used by tests, and as the fallback when IndexedDB is unavailable. */
export function createMemoryQueueStore(initial: QueuedProgressUpdate[] = []): QueueStore {
  let items = [...initial];
  return {
    readAll: async () => [...items],
    writeAll: async (next) => {
      items = [...next];
    }
  };
}
