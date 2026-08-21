import {
  actorHeaders,
  capabilityAllows,
  isProjectRole,
  projectRoleLabels
} from "@shutdown-tracker/api-client";
import type { ActorIdentity, Capability, ProjectRole } from "@shutdown-tracker/api-client";

/**
 * Who the console is acting as, and against which project.
 *
 * The actor is configured rather than authenticated. Until production token authentication
 * lands, the console carries the actor in trusted headers, which only means anything behind
 * a gateway that sets them from a real login.
 *
 * What is stored is a whole identity — user id, name, role and project together — and not a role
 * on its own. A role by itself was actively misleading: changing it rewrote the role header and
 * re-derived every capability flag, but the server resolves the caller's role from their
 * `project_memberships` row and ignores the header entirely. With one identity configured, the
 * only two things the old selector could do were un-grey a control the server would refuse, or
 * grey out one it would have allowed. Switching the person is what changes the answer.
 */

export type ConsoleSession = {
  projectId: string;
  actor: ActorIdentity | null;
  /** True when both a project and an actor are configured, so live calls can be made. */
  live: boolean;
};

export type ConsoleEnv = Record<string, unknown>;

const identityStorageKey = "shutdown-tracker.console.identity";

/** An identity chosen in this browser, as it is remembered between reloads. */
export type StoredIdentity = {
  userId: string;
  role: string;
  displayName: string;
  projectId?: string;
};

export function buildConsoleSession(env: ConsoleEnv, storedIdentity?: StoredIdentity | null): ConsoleSession {
  const stored = validStoredIdentity(storedIdentity);

  // A stored identity replaces the build-time actor wholesale. Taking the id from one source and
  // the role from another is how a session ends up claiming a role its membership does not have.
  const projectId = stored?.projectId ?? readString(env.VITE_SHUTDOWN_TRACKER_PROJECT_ID);
  const userId = stored?.userId ?? readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ID);
  const displayName =
    stored?.displayName ?? (readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_NAME) || "Console user");
  const role = stored
    ? (stored.role as ProjectRole)
    : resolveRole(null, readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ROLE));

  const actor: ActorIdentity | null =
    userId.length > 0 && role !== null ? { userId, role, displayName } : null;

  return {
    projectId,
    actor,
    live: projectId.length > 0 && actor !== null
  };
}

/**
 * A stored identity, or null if it is unusable.
 *
 * Discarded rather than partially applied, for the reason `resolveRole` gives: a half-valid
 * identity produces a confusing rejection at the first write rather than an obvious one now.
 */
function validStoredIdentity(stored: StoredIdentity | null | undefined): StoredIdentity | null {
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

/**
 * The role to act as, preferring an explicit in-session choice over the build-time default.
 *
 * An unrecognised value is discarded rather than passed through: sending a role the server
 * does not know produces a confusing rejection at the first write.
 */
export function resolveRole(storedRole: string | null | undefined, configuredRole: string): ProjectRole | null {
  for (const candidate of [storedRole, configuredRole]) {
    if (typeof candidate === "string" && isProjectRole(candidate.trim())) {
      return candidate.trim() as ProjectRole;
    }
  }
  return null;
}

export function readStoredIdentity(storage: Pick<Storage, "getItem"> | undefined): StoredIdentity | null {
  try {
    const raw = storage?.getItem(identityStorageKey);
    return raw === null || raw === undefined ? null : (JSON.parse(raw) as StoredIdentity);
  } catch {
    // Private browsing modes throw on access, and a value written by an older build may not
    // parse. Neither is an error: an unreadable preference is simply no preference.
    return null;
  }
}

export function writeStoredIdentity(storage: Pick<Storage, "setItem"> | undefined, identity: StoredIdentity) {
  try {
    storage?.setItem(identityStorageKey, JSON.stringify(identity));
  } catch {
    // The selection still applies to this session even when it cannot be remembered.
  }
}

/**
 * Whether the current actor may perform an operation.
 *
 * Used to disable controls, never to decide whether a request is safe. With no actor
 * configured nothing is permitted, which keeps the read-only state honest rather than
 * showing controls that would fail.
 */
export function sessionAllows(session: ConsoleSession, capability: Capability) {
  return session.actor !== null && capabilityAllows(capability, session.actor.role);
}

export function sessionHeaders(session: ConsoleSession): Record<string, string> {
  return session.actor === null ? {} : actorHeaders(session.actor);
}

export function describeSession(session: ConsoleSession) {
  if (session.actor === null) {
    return "No actor configured. Set VITE_SHUTDOWN_TRACKER_ACTOR_ID to act on this project.";
  }
  if (session.projectId.length === 0) {
    return "No project configured. Set VITE_SHUTDOWN_TRACKER_PROJECT_ID to load a shutdown.";
  }
  return `${session.actor.displayName} acting as ${projectRoleLabels[session.actor.role]}`;
}

function readString(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}
