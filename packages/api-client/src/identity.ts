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
  | "RESET_REVIEW_DATA"
  | "VIEW_PROJECT";

/**
 * The roles the permission matrix grants each capability, before the super user rule below.
 *
 * One product rule is visible here and must stay visible: supervisor acceptance appears under
 * `REVIEW_TASK_PROGRESS` only — it never grants an export capability, because confirming that
 * work happened is not the same as approving a schedule change.
 *
 * A second rule is SUSPENDED for the console round-trip trial: see `superUserRole`. These lists
 * are the model that returns when it ends, so they are left as the matrix writes them rather than
 * edited to hand the trial's one actor what it needs. This mirrors `Capability.java`; the two must
 * not diverge, and `CapabilityClientParityTests` fails if they do.
 */
const capabilityRoles: Record<Capability, readonly ProjectRole[]> = {
  UPLOAD_SOURCE_FILE: ["planner", "admin"],
  REQUEST_PROJECT_PARSE: ["planner", "admin"],
  ACCEPT_IMPORT_SNAPSHOT: ["planner", "admin"],
  REJECT_IMPORT_SNAPSHOT: ["planner", "admin"],
  RECONCILE_TASK_LINEAGE: ["planner"],
  MANAGE_IMPORT_PROFILE: ["planner"],
  MANAGE_RESOURCE_LINK: ["planner", "admin"],
  RECORD_APPROVAL: ["planner"],
  CREATE_EXPORT_PREVIEW: ["planner"],
  APPROVE_EXPORT_BATCH: ["planner"],
  GENERATE_EXPORT_ARTIFACT: ["planner"],
  RECORD_EXPORT_VERIFICATION: ["planner"],
  RETURN_CANDIDATE_SCHEDULE: ["planner"],
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
  MANAGE_CRITICAL_WATCHLIST: ["planner", "shutdown_control"],
  SUBMIT_CRITICAL_UPDATE: [
    "shutdown_control",
    "coordinator",
    "supervisor",
    "field_user",
    "contractor",
    "inspector"
  ],
  RESET_REVIEW_DATA: ["admin"],
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

/** Every capability, in declaration order. Exhaustive by construction: the record is typed. */
export const capabilities = Object.keys(capabilityRoles) as Capability[];

/**
 * The role that holds every capability, whatever the grants above say.
 *
 * The console round-trip trial is driven by one person, so `admin` is a super user for the
 * duration. Separation of duty is what this suspends: export approval is planner-owned precisely
 * because administering access is not the same as deciding what returns to Microsoft Project, and
 * the three review stages exist so that more than one person walks them. They are still walked in
 * order — by one person, so the four-eyes property does not hold while the trial runs.
 *
 * One rule in one place, rather than `admin` appended to twenty-four grant lists: written that way
 * it would be indistinguishable from a considered decision per capability, a new capability would
 * silently omit the super user, and ending the trial would be twenty-four edits a reviewer has to
 * check are all of them. Mirrors `Capability.SUPER_USER`.
 */
export const superUserRole: ProjectRole = "admin";

export function capabilityAllows(capability: Capability, role: ProjectRole) {
  return role === superUserRole || capabilityRoles[capability].includes(role);
}

/**
 * Every role that may perform this capability, the super user included.
 *
 * Answers the same question `capabilityAllows` does, and must not answer it differently: a caller
 * listing who may act and a caller asking about one role would otherwise disagree about the super
 * user.
 */
export function rolesAllowedFor(capability: Capability): readonly ProjectRole[] {
  const declared = capabilityRoles[capability];
  return declared.includes(superUserRole) ? declared : [...declared, superUserRole];
}

/** The roles the permission matrix grants, before the super user rule. */
export function rolesDeclaredFor(capability: Capability): readonly ProjectRole[] {
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
