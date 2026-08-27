import {
  actorHeaders,
  capabilityAllows,
  createShutdownTrackerApiClient,
  isProjectRole,
  projectRoleLabels
} from "@shutdown-tracker/api-client";
import type { ActorIdentity, Capability, ProjectRole } from "@shutdown-tracker/api-client";

/**
 * Who the field app is acting as.
 *
 * As in the console, the actor is carried in trusted headers and only means anything behind a
 * gateway that authenticates the person first. Capability checks here decide what the screen
 * offers; the server decides what is permitted.
 *
 * <p>There is no picker, for the same reason the console lost one. The trial is driven by a single
 * super user and the seeder retires everybody else, so the only identities a picker could offer are
 * accounts the API now refuses. A remembered choice was worse still: it replaced the built-in actor
 * wholesale and survived every redeploy, so a device that once chose the field user kept sending a
 * retired id and got 403 on everything. The build-time actor is the only actor.
 */

export type FieldSession = {
  projectId: string;
  actor: ActorIdentity | null;
  live: boolean;
};

export type FieldApiClient = ReturnType<typeof createShutdownTrackerApiClient>;

const identityStorageKey = "shutdown-tracker.field.identity";

export function buildFieldSession(env: Record<string, unknown>): FieldSession {
  const projectId = readString(env.VITE_SHUTDOWN_TRACKER_PROJECT_ID);
  const userId = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ID);
  const displayName = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_NAME) || "Field user";
  const configuredRole = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ROLE) || "field_user";
  const role: ProjectRole | null = isProjectRole(configuredRole) ? configuredRole : null;

  const actor = userId.length > 0 && role !== null ? { userId, role, displayName } : null;

  return { projectId, actor, live: projectId.length > 0 && actor !== null };
}

/**
 * Clears an identity remembered by a build that still had a picker.
 *
 * <p>Nothing reads that key any more, so a leftover value is already inert. It is removed anyway so
 * that reintroducing a read path cannot silently resurrect a retired account years later.
 */
export function discardLegacyFieldIdentity(storage: Pick<Storage, "removeItem"> | undefined) {
  try {
    storage?.removeItem(identityStorageKey);
  } catch {
    // Private-browsing modes throw on storage access. Nothing reads the key, so this is housekeeping.
  }
}

export function fieldSessionAllows(session: FieldSession, capability: Capability) {
  return session.actor !== null && capabilityAllows(capability, session.actor.role);
}

export function describeFieldSession(session: FieldSession) {
  if (session.actor === null) {
    return "No actor configured";
  }
  return `${session.actor.displayName} · ${projectRoleLabels[session.actor.role]}`;
}

export function createFieldApiClient(session: FieldSession, baseUrl: string): FieldApiClient {
  return createShutdownTrackerApiClient({
    baseUrl,
    headers: session.actor === null ? {} : actorHeaders(session.actor)
  });
}

export const fieldBaseUrl = readString(import.meta.env.VITE_SHUTDOWN_TRACKER_API_BASE_URL);

export const initialFieldSession = buildFieldSession(import.meta.env);

function readString(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}
