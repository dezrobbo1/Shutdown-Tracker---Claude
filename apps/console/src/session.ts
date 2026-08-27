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
 * **There is one identity and no way to change it.** The console round-trip trial is driven by a
 * single super user, so the build decides who this console is and nothing in the browser overrides
 * it. What used to be here was a selector backed by `localStorage`, and it outlived what it was
 * for: a browser that had once picked "Review Planner" went on acting as one through every
 * redeploy, including after the seeder stopped creating that person. A remembered choice among
 * identities that no longer exist is worse than no choice at all — it is a console that quietly
 * disagrees with the server about who is using it.
 */

export type ConsoleSession = {
  projectId: string;
  actor: ActorIdentity | null;
  /** True when both a project and an actor are configured, so live calls can be made. */
  live: boolean;
};

export type ConsoleEnv = Record<string, unknown>;

/** The key a build with an identity selector wrote its choice to. Read by nothing now. */
const identityStorageKey = "shutdown-tracker.console.identity";

export function buildConsoleSession(env: ConsoleEnv): ConsoleSession {
  const projectId = readString(env.VITE_SHUTDOWN_TRACKER_PROJECT_ID);
  const userId = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ID);
  const displayName = readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_NAME) || "Console user";
  const role = resolveRole(readString(env.VITE_SHUTDOWN_TRACKER_ACTOR_ROLE));

  const actor: ActorIdentity | null =
    userId.length > 0 && role !== null ? { userId, role, displayName } : null;

  return {
    projectId,
    actor,
    live: projectId.length > 0 && actor !== null
  };
}

/**
 * The role to act as, or null if the build did not name a usable one.
 *
 * An unrecognised value is discarded rather than passed through: sending a role the server
 * does not know produces a confusing rejection at the first write.
 *
 * The role is cosmetic to the server either way — `ProjectAuthorizationService` resolves the
 * caller's real role from their membership and ignores the header — so it decides only what this
 * interface offers. Getting it wrong therefore shows up as a control that is greyed out when the
 * server would have allowed it, or offered when the server will refuse.
 */
export function resolveRole(configuredRole: string): ProjectRole | null {
  const candidate = configuredRole.trim();
  return isProjectRole(candidate) ? (candidate as ProjectRole) : null;
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
  return `${session.actor.displayName} · ${projectRoleLabels[session.actor.role]}`;
}

/**
 * Clears an identity remembered by a build that still had the "Acting as" selector.
 *
 * <p>Nothing reads that key any more, which is what made a stale value harmless. It is removed
 * anyway: the value names an account the seeder has since retired, and leaving it on every
 * reviewer's machine means reintroducing a read path would silently resurrect it.
 */
export function discardLegacyStoredIdentity(storage: Pick<Storage, "removeItem"> | undefined) {
  try {
    storage?.removeItem(identityStorageKey);
  } catch {
    // Private-browsing modes throw on storage access. Nothing reads the key, so this is housekeeping.
  }
}

function readString(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}
