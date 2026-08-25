/**
 * Project roles and capabilities, mirroring the server's `ProjectRole` and `Capability`
 * enums.
 *
 * This exists so an interface can hide or disable an action the caller cannot perform,
 * rather than offering it and failing with a 403. It is a usability layer only: the server
 * resolves the caller's real membership from the database and is the sole authority on what
 * is permitted. Never treat a `true` here as authorisation.
 */

export const projectRoles = [
  "admin",
  "planner",
  "shutdown_control",
  "coordinator",
  "supervisor",
  "field_user",
  "contractor",
  "inspector",
  "viewer"
] as const;

export type ProjectRole = (typeof projectRoles)[number];

export function isProjectRole(value: string): value is ProjectRole {
  return (projectRoles as readonly string[]).includes(value);
}

/** Human labels for role pickers and attribution lines. */
export const projectRoleLabels: Record<ProjectRole, string> = {
  admin: "Administrator",
  planner: "Planner",
  shutdown_control: "Shutdown control",
  coordinator: "Coordinator",
  supervisor: "Supervisor",
  field_user: "Field user",
  contractor: "Contractor",
  inspector: "Inspector",
  viewer: "Viewer"
};

export type Capability =
  | "UPLOAD_SOURCE_FILE"
  | "REQUEST_PROJECT_PARSE"
  | "ACCEPT_IMPORT_SNAPSHOT"
  | "REJECT_IMPORT_SNAPSHOT"
  | "RECONCILE_TASK_LINEAGE"
  | "MANAGE_IMPORT_PROFILE"
  | "MANAGE_RESOURCE_LINK"
  | "RECORD_APPROVAL"
  | "CREATE_EXPORT_PREVIEW"
  | "APPROVE_EXPORT_BATCH"
  | "GENERATE_EXPORT_ARTIFACT"
  | "RECORD_EXPORT_VERIFICATION"
  | "RETURN_CANDIDATE_SCHEDULE"
  | "SUBMIT_TASK_PROGRESS"
  | "REVIEW_TASK_PROGRESS"
  | "PLANNER_REVIEW_TASK_PROGRESS"
  | "RAISE_PROBLEM"
  | "MANAGE_PROBLEM"
  | "MANAGE_ACTION"
  | "CAPTURE_EVIDENCE"
  | "RECORD_HANDOVER"
  | "MANAGE_CRITICAL_WATCHLIST"
  | "SUBMIT_CRITICAL_UPDATE"
  | "VIEW_PROJECT";

/**
 * The roles allowed each capability.
 *
 * One product rule is visible here and must stay visible: supervisor acceptance appears under
 * `REVIEW_TASK_PROGRESS` only — it never grants an export capability, because confirming that
 * work happened is not the same as approving a schedule change.
 *
 * A second rule is currently SUSPENDED. Export approval is planner-owned and deliberately
 * excluded `admin`, because administering access is not the same as approving what goes back to
 * Microsoft Project. The console round-trip trial is driven by one admin, so `admin` now holds the
 * execution, review and export capabilities marked `trial:` below. The three review stages still
 * exist and are still walked in order — but one person walks all of them, so the four-eyes property
 * does not hold while the trial runs. Restoring it means removing `admin` from those grants and
 * giving the trial a second actor. This mirrors `Capability.java`; the two must not diverge.
 */
const capabilityRoles: Record<Capability, readonly ProjectRole[]> = {
  UPLOAD_SOURCE_FILE: ["planner", "admin"],
  REQUEST_PROJECT_PARSE: ["planner", "admin"],
  ACCEPT_IMPORT_SNAPSHOT: ["planner", "admin"],
  REJECT_IMPORT_SNAPSHOT: ["planner", "admin"],
  RECONCILE_TASK_LINEAGE: ["planner"],
  MANAGE_IMPORT_PROFILE: ["planner"],
  MANAGE_RESOURCE_LINK: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  RECORD_APPROVAL: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  CREATE_EXPORT_PREVIEW: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  APPROVE_EXPORT_BATCH: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  GENERATE_EXPORT_ARTIFACT: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  RECORD_EXPORT_VERIFICATION: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  RETURN_CANDIDATE_SCHEDULE: ["planner", "admin"],
  // trial: one admin walks the whole round trip.
  SUBMIT_TASK_PROGRESS: ["field_user", "contractor", "supervisor", "coordinator", "admin"],
  // trial: one admin walks the whole round trip.
  REVIEW_TASK_PROGRESS: ["supervisor", "coordinator", "shutdown_control", "admin"],
  // trial: one admin walks the whole round trip.
  PLANNER_REVIEW_TASK_PROGRESS: ["planner", "admin"],
  RAISE_PROBLEM: [
    "field_user",
    "contractor",
    "supervisor",
    "coordinator",
    "shutdown_control",
    "inspector",
    "planner"
  ],
  MANAGE_PROBLEM: ["supervisor", "coordinator", "shutdown_control"],
  MANAGE_ACTION: ["supervisor", "coordinator", "shutdown_control"],
  CAPTURE_EVIDENCE: ["field_user", "contractor", "supervisor", "inspector"],
  RECORD_HANDOVER: ["field_user", "supervisor", "coordinator", "shutdown_control"],
  MANAGE_CRITICAL_WATCHLIST: ["planner", "shutdown_control"],
  SUBMIT_CRITICAL_UPDATE: [
    "shutdown_control",
    "coordinator",
    "supervisor",
    "field_user",
    "contractor",
    "inspector"
  ],
  VIEW_PROJECT: [
    "admin",
    "planner",
    "shutdown_control",
    "coordinator",
    "supervisor",
    "field_user",
    "contractor",
    "inspector",
    "viewer"
  ]
};

export function capabilityAllows(capability: Capability, role: ProjectRole) {
  return capabilityRoles[capability].includes(role);
}

export function rolesAllowedFor(capability: Capability): readonly ProjectRole[] {
  return capabilityRoles[capability];
}

/** The actor an interface is acting as. */
export type ActorIdentity = {
  userId: string;
  role: ProjectRole;
  displayName: string;
};

export const actorIdHeader = "X-Shutdown-Tracker-Actor-Id";
export const actorRoleHeader = "X-Shutdown-Tracker-Actor-Role";
export const actorDisplayNameHeader = "X-Shutdown-Tracker-Actor-Name";

/**
 * Headers carrying the actor to the API.
 *
 * These are trusted-header development credentials. Nothing signs them, so they are only
 * safe behind a gateway that authenticates the caller and sets them itself. Production
 * authentication replaces this with a validated token.
 */
export function actorHeaders(actor: ActorIdentity): Record<string, string> {
  return {
    [actorIdHeader]: actor.userId,
    [actorRoleHeader]: actor.role,
    [actorDisplayNameHeader]: actor.displayName
  };
}
