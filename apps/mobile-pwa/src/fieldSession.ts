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
 */

export type FieldSession = {
  projectId: string;
  actor: ActorIdentity | null;
  live: boolean;
};

export type FieldApiClient = ReturnType<typeof createShutdownTrackerApiClient>;

/** An identity chosen on this device, as it is remembered between reloads. */
export type StoredFieldIdentity = {
  userId: string;
  role: string;
  displayName: string;
  projectId?: string;
};

const identityStorageKey = "shutdown-tracker.field.identity";

export function buildFieldSession(
  env: Record<string, unknown>,
  storedIdentity?: StoredFieldIdentity | null
): FieldSession {
  const stored = validStoredIdentity(storedIdentity);

  const projectId = stored?.projectId ?? readString(env.VITE_SHUTDOWN_TRACKER_PROJECT_ID);
  const userId = stored?.userId ?? readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ID);
  const displayName =
    stored?.displayName ?? (readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_NAME) || "Field user");
  const configuredRole = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ROLE) || "field_user";
  const resolvedRole = stored?.role ?? configuredRole;
  const role: ProjectRole | null = isProjectRole(resolvedRole) ? resolvedRole : null;

  const actor = userId.length > 0 && role !== null ? { userId, role, displayName } : null;

  return { projectId, actor, live: projectId.length > 0 && actor !== null };
}

function validStoredIdentity(stored: StoredFieldIdentity | null | undefined): StoredFieldIdentity | null {
  if (!stored || typeof stored !== "object") {
    return null;
  }
  const userId = readString(stored.userId);
  const displayName = readString(stored.displayName);
  const projectId = readString(stored.projectId);
  if (userId.length === 0 || !isProjectRole(readString(stored.role))) {
    return null;
  }
  return {
    userId,
    role: readString(stored.role),
    displayName: displayName.length > 0 ? displayName : userId,
    projectId: projectId.length > 0 ? projectId : undefined
  };
}

export function readStoredFieldIdentity(
  storage: Pick<Storage, "getItem"> | undefined
): StoredFieldIdentity | null {
  try {
    const raw = storage?.getItem(identityStorageKey);
    return raw === null || raw === undefined ? null : (JSON.parse(raw) as StoredFieldIdentity);
  } catch {
    return null;
  }
}

export function writeStoredFieldIdentity(
  storage: Pick<Storage, "setItem"> | undefined,
  identity: StoredFieldIdentity
) {
  try {
    storage?.setItem(identityStorageKey, JSON.stringify(identity));
  } catch {
    // The selection still applies to this session even when it cannot be remembered.
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

export const initialFieldSession = buildFieldSession(
  import.meta.env,
  readStoredFieldIdentity(typeof window === "undefined" ? undefined : window.localStorage)
);

function readString(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}
