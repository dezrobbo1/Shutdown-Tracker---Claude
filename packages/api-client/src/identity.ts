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
  | "RECORD_APPROVAL"
  | "CREATE_EXPORT_PREVIEW"
  | "APPROVE_EXPORT_BATCH"
  | "GENERATE_EXPORT_ARTIFACT"
  | "RECORD_EXPORT_VERIFICATION"
  | "SUBMIT_TASK_PROGRESS"
  | "REVIEW_TASK_PROGRESS"
  | "PLANNER_REVIEW_TASK_PROGRESS"
  | "RAISE_PROBLEM"
  | "MANAGE_PROBLEM"
  | "MANAGE_ACTION"
  | "CAPTURE_EVIDENCE"
  | "RECORD_HANDOVER"
  | "VIEW_PROJECT";

/**
 * The roles allowed each capability.
 *
 * Two product rules are visible here and must stay visible. Export approval is planner-owned
 * and deliberately excludes `admin`: administering access is not the same as approving what
 * goes back to Microsoft Project. And supervisor acceptance appears under
 * `REVIEW_TASK_PROGRESS` only — it never grants an export capability, because confirming that
 * work happened is not the same as approving a schedule change.
 */
const capabilityRoles: Record<Capability, readonly ProjectRole[]> = {
  UPLOAD_SOURCE_FILE: ["planner", "admin"],
  REQUEST_PROJECT_PARSE: ["planner", "admin"],
  ACCEPT_IMPORT_SNAPSHOT: ["planner", "admin"],
  REJECT_IMPORT_SNAPSHOT: ["planner", "admin"],
  RECONCILE_TASK_LINEAGE: ["planner"],
  MANAGE_IMPORT_PROFILE: ["planner"],
  RECORD_APPROVAL: ["planner"],
  CREATE_EXPORT_PREVIEW: ["planner"],
  APPROVE_EXPORT_BATCH: ["planner"],
  GENERATE_EXPORT_ARTIFACT: ["planner"],
  RECORD_EXPORT_VERIFICATION: ["planner"],
  SUBMIT_TASK_PROGRESS: ["field_user", "contractor", "supervisor", "coordinator"],
  REVIEW_TASK_PROGRESS: ["supervisor", "coordinator", "shutdown_control"],
  PLANNER_REVIEW_TASK_PROGRESS: ["planner"],
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
