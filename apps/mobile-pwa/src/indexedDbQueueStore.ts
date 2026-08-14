import { createMemoryQueueStore } from "./offlineQueue";
import type { QueueStore, QueuedProgressUpdate } from "./offlineQueue";

/**
 * IndexedDB storage for the offline queue.
 *
 * IndexedDB rather than localStorage because a captured report must survive the browser
 * reclaiming memory from a backgrounded tab, which is the normal state of a phone in someone's
 * pocket between tasks.
 *
 * The whole queue is read and written as one record. A shift's worth of captures is small, and
 * a single write keeps the stored queue consistent — a partial write here would mean a report
 * that exists but is not sendable.
 */

const databaseName = "shutdown-tracker-field";
const storeName = "progress-queue";
const queueKey = "queue";
const databaseVersion = 1;

export function indexedDbAvailable(): boolean {
  return typeof globalThis !== "undefined" && typeof globalThis.indexedDB !== "undefined";
}

/**
 * Durable storage when the browser provides it, memory when it does not.
 *
 * Falling back rather than failing keeps the app usable in a private window or an unsupported
 * browser. The interface reports which one is in use, because "your reports survive closing
 * this app" stops being true in the fallback and the user has to know that.
 */
export function createQueueStore(): { store: QueueStore; durable: boolean } {
  if (!indexedDbAvailable()) {
    return { store: createMemoryQueueStore(), durable: false };
  }
  return { store: createIndexedDbQueueStore(), durable: true };
}

export function createIndexedDbQueueStore(): QueueStore {
  return {
    readAll: async () => {
      const database = await openDatabase();
      try {
        const stored = await request<QueuedProgressUpdate[] | undefined>(
          database.transaction(storeName, "readonly").objectStore(storeName).get(queueKey)
        );
        return stored ?? [];
      } finally {
        database.close();
      }
    },
    writeAll: async (items) => {
      const database = await openDatabase();
      try {
        const transaction = database.transaction(storeName, "readwrite");
        transaction.objectStore(storeName).put(items, queueKey);
        await completed(transaction);
      } finally {
        database.close();
      }
    }
  };
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const open = globalThis.indexedDB.open(databaseName, databaseVersion);
    open.onupgradeneeded = () => {
      if (!open.result.objectStoreNames.contains(storeName)) {
        open.result.createObjectStore(storeName);
      }
    };
    open.onsuccess = () => resolve(open.result);
    open.onerror = () => reject(open.error ?? new Error("The field database could not be opened."));
  });
}

function request<T>(source: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    source.onsuccess = () => resolve(source.result);
    source.onerror = () => reject(source.error ?? new Error("The field database read failed."));
  });
}

function completed(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onabort = () => reject(transaction.error ?? new Error("The field database write was aborted."));
    transaction.onerror = () => reject(transaction.error ?? new Error("The field database write failed."));
  });
}
