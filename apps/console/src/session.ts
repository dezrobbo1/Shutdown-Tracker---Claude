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
 * a gateway that sets them from a real login. The role selector below is a development
 * affordance for exercising the review chain from both sides; it grants nothing, because the
 * server resolves the caller's actual project membership before allowing any operation.
 */

export type ConsoleSession = {
  projectId: string;
  actor: ActorIdentity | null;
  /** True when both a project and an actor are configured, so live calls can be made. */
  live: boolean;
};

export type ConsoleEnv = Record<string, unknown>;

const roleStorageKey = "shutdown-tracker.console.role";

export function buildConsoleSession(env: ConsoleEnv, storedRole?: string | null): ConsoleSession {
  const projectId = readString(env.VITE_SHUTDOWN_TRACKER_PROJECT_ID);
  const userId = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ID);
  const displayName = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_NAME) || "Console user";
  const role = resolveRole(storedRole, readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ROLE));

  const actor: ActorIdentity | null =
    userId.length > 0 && role !== null ? { userId, role, displayName } : null;

  return {
    projectId,
    actor,
    live: projectId.length > 0 && actor !== null
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

export function readStoredRole(storage: Pick<Storage, "getItem"> | undefined): string | null {
  try {
    return storage?.getItem(roleStorageKey) ?? null;
  } catch {
    // Private browsing modes can throw on access. A missing preference is not an error.
    return null;
  }
}

export function writeStoredRole(storage: Pick<Storage, "setItem"> | undefined, role: ProjectRole) {
  try {
    storage?.setItem(roleStorageKey, role);
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
