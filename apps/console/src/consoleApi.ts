import { createShutdownTrackerApiClient } from "@shutdown-tracker/api-client";
import type { ShutdownTrackerApiClientOptions } from "@shutdown-tracker/api-client";
import { buildConsoleSession, readStoredIdentity, sessionHeaders } from "./session";
import type { ConsoleSession } from "./session";

export type ConsoleApiClient = ReturnType<typeof createShutdownTrackerApiClient>;

export const consoleBaseUrl = readBaseUrl(import.meta.env);

export const initialConsoleSession = buildConsoleSession(
  import.meta.env,
  readStoredIdentity(typeof window === "undefined" ? undefined : window.localStorage)
);

/**
 * An API client carrying the session's actor.
 *
 * Rebuilt whenever the acting identity changes so a request is never attributed to the previous
 * person. Cheap to construct: the client holds configuration, not connections.
 */
export function createConsoleApiClient(
  session: ConsoleSession,
  options: Pick<ShutdownTrackerApiClientOptions, "fetchImpl"> = {}
): ConsoleApiClient {
  return createShutdownTrackerApiClient({
    baseUrl: consoleBaseUrl,
    headers: sessionHeaders(session),
    fetchImpl: options.fetchImpl
  });
}

function readBaseUrl(env: Record<string, unknown>) {
  const value = env.VITE_SHUTDOWN_TRACKER_API_BASE_URL;
  return typeof value === "string" ? value.trim() : "";
}
