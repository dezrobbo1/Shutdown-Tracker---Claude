import { useCallback, useEffect, useRef, useState } from "react";
import { ShutdownTrackerApiError } from "@shutdown-tracker/api-client";

/**
 * Loading, error, and refresh handling for a single API read.
 *
 * Every zone needs the same four states, and each one has to be distinguishable on screen:
 * a planner deciding whether to approve an export must be able to tell "nothing is eligible"
 * from "the eligibility check failed". Collapsing those into one empty view is how a control
 * system quietly reports the wrong thing.
 */

export type ResourceState<T> =
  | { status: "idle"; message: string }
  | { status: "loading" }
  | { status: "loaded"; value: T }
  | { status: "error"; message: string };

export type Resource<T> = {
  state: ResourceState<T>;
  reload: () => Promise<void>;
  /** Replaces the held value after a write, so a zone need not refetch to show its own result. */
  replace: (value: T) => void;
};

export function useAsyncResource<T>(
  load: () => Promise<T>,
  options: { enabled: boolean; idleMessage: string }
): Resource<T> {
  const { enabled, idleMessage } = options;
  const [state, setState] = useState<ResourceState<T>>(
    enabled ? { status: "loading" } : { status: "idle", message: idleMessage }
  );

  // Guards against a slow first response overwriting a newer one, which otherwise shows
  // stale review queues after a refresh.
  const requestRef = useRef(0);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const reload = useCallback(async () => {
    if (!enabled) {
      setState({ status: "idle", message: idleMessage });
      return;
    }

    const request = ++requestRef.current;
    setState({ status: "loading" });

    try {
      const value = await load();
      if (mountedRef.current && request === requestRef.current) {
        setState({ status: "loaded", value });
      }
    } catch (error) {
      if (mountedRef.current && request === requestRef.current) {
        setState({ status: "error", message: describeApiError(error) });
      }
    }
  }, [enabled, idleMessage, load]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const replace = useCallback((value: T) => {
    requestRef.current += 1;
    setState({ status: "loaded", value });
  }, []);

  return { state, reload, replace };
}

/**
 * A message describing why a call failed.
 *
 * A 403 is called out specifically. It is the expected answer when someone opens a zone their
 * role does not cover, and "you do not have permission" is actionable in a way that a bare
 * status code is not.
 */
export function describeApiError(error: unknown) {
  if (error instanceof ShutdownTrackerApiError) {
    if (error.status === 403) {
      return "Your role on this project does not permit that operation.";
    }
    if (error.status === 404) {
      return "Not found. It may belong to another project or have been superseded.";
    }
    if (error.status === 409) {
      return `Rejected because of the current state: ${error.responseBody || "conflict"}`;
    }
    return `Request failed (${error.status}): ${error.responseBody || error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "The request failed.";
}
