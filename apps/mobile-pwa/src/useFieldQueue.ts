import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CriticalUpdateSubmitRequest, TaskProgressSubmitRequest } from "@shutdown-tracker/api-client";
import { OfflineSubmissionQueue } from "./offlineQueue";
import type { QueueStore, QueuedSubmission } from "./offlineQueue";
import { createQueueStore } from "./indexedDbQueueStore";
import type { FieldApiClient } from "./fieldSession";

/**
 * The queue, wired to the screen.
 *
 * Flushing is triggered by regaining connectivity rather than by a timer. A worker walking out
 * of a vessel gets their reports sent without having to remember to press anything, which is
 * the whole point of capturing them offline.
 */

export type FieldQueueState = {
  items: QueuedSubmission[];
  durable: boolean;
  online: boolean;
  flushing: boolean;
};

export function useFieldQueue(
  client: FieldApiClient,
  projectId: string,
  options: { store?: QueueStore; durable?: boolean } = {}
) {
  const [items, setItems] = useState<QueuedSubmission[]>([]);
  const [flushing, setFlushing] = useState(false);
  const [online, setOnline] = useState(() =>
    typeof navigator === "undefined" ? true : navigator.onLine !== false
  );

  const storage = useMemo(
    () => (options.store ? { store: options.store, durable: options.durable ?? false } : createQueueStore()),
    [options.store, options.durable]
  );

  const queue = useMemo(
    () =>
      new OfflineSubmissionQueue({
        store: storage.store,
        // Which endpoint a kind goes to is decided here; the queue only decides when to send.
        submit: (item) =>
          item.kind === "progress"
            ? client.taskProgress.submit(projectId, item.request)
            : client.criticalWatch.submitUpdate(projectId, item.request),
        newId: newLocalId
      }),
    [storage.store, client, projectId]
  );

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const refresh = useCallback(async () => {
    const current = await queue.list();
    if (mounted.current) {
      setItems(current);
    }
  }, [queue]);

  const flush = useCallback(async () => {
    setFlushing(true);
    try {
      const next = await queue.flush();
      if (mounted.current) {
        setItems(next);
      }
    } finally {
      if (mounted.current) {
        setFlushing(false);
      }
    }
  }, [queue]);

  const sendIfConnected = useCallback(async () => {
    await refresh();
    // Sending immediately when there is a connection; the capture is already safe either way.
    if (typeof navigator === "undefined" || navigator.onLine !== false) {
      await flush();
    }
  }, [refresh, flush]);

  const capture = useCallback(
    async (
      request: Omit<TaskProgressSubmitRequest, "idempotencyKey" | "offlineLocalId">,
      taskName: string
    ) => {
      await queue.enqueueProgress(request, taskName);
      await sendIfConnected();
    },
    [queue, sendIfConnected]
  );

  const captureCriticalUpdate = useCallback(
    async (
      request: Omit<CriticalUpdateSubmitRequest, "idempotencyKey" | "offlineLocalId">,
      workPackageName: string
    ) => {
      await queue.enqueueCriticalUpdate(request, workPackageName);
      await sendIfConnected();
    },
    [queue, sendIfConnected]
  );

  const retry = useCallback(
    async (localId: string) => {
      setItems(await queue.retry(localId));
      await flush();
    },
    [queue, flush]
  );

  const clearSettled = useCallback(async () => {
    setItems(await queue.clearSettled());
  }, [queue]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const goOnline = () => {
      setOnline(true);
      void flush();
    };
    const goOffline = () => setOnline(false);

    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    return () => {
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
  }, [flush]);

  const state: FieldQueueState = { items, durable: storage.durable, online, flushing };

  return { state, capture, captureCriticalUpdate, flush, retry, clearSettled, refresh };
}

/**
 * An identifier generated on the device.
 *
 * `crypto.randomUUID` where available. The fallback still has to be unique across a shift on
 * one device, which is all a local id and an idempotency key need — the server pairs the key
 * with the project, so a collision between two devices is not possible.
 */
export function newLocalId(): string {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === "function") {
    return cryptoApi.randomUUID();
  }
  return `local-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
