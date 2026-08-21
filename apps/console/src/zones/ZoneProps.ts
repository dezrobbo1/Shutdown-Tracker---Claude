import type { ConsoleApiClient } from "../consoleApi";
import { sessionAllows } from "../session";
import type { ConsoleSession } from "../session";

/**
 * What every zone is given: who is acting, which project, and a client already carrying
 * that actor.
 *
 * The capability flags are named after the decision rather than the endpoint, so a zone reads
 * as the product rule it enforces. They gate controls only — the server checks the caller's
 * stored membership on every write regardless of what the interface offered.
 */
export type ZoneSession = ConsoleSession & {
  canAcceptSnapshot: boolean;
  canReconcileLineage: boolean;
  canSubmitProgress: boolean;
  canReviewProgress: boolean;
  canPlannerReview: boolean;
  canRaiseProblem: boolean;
  canManageProblem: boolean;
  canManageAction: boolean;
  canRecordHandover: boolean;
  canCaptureEvidence: boolean;
  canManageCriticalWatchlist: boolean;
  canSubmitCriticalUpdate: boolean;
  canManageMapping: boolean;
  canManageResourceLink: boolean;
  canCreateExportPreview: boolean;
  canApproveExport: boolean;
  canUploadSourceFile: boolean;
  canRequestParse: boolean;
  canGenerateArtifact: boolean;
  canRecordVerification: boolean;
  canReturnCandidate: boolean;
};

export type ZoneProps = {
  session: ZoneSession;
  client: ConsoleApiClient;
};

export function buildZoneSession(session: ConsoleSession): ZoneSession {
  return {
    ...session,
    canAcceptSnapshot: sessionAllows(session, "ACCEPT_IMPORT_SNAPSHOT"),
    canReconcileLineage: sessionAllows(session, "RECONCILE_TASK_LINEAGE"),
    canSubmitProgress: sessionAllows(session, "SUBMIT_TASK_PROGRESS"),
    canReviewProgress: sessionAllows(session, "REVIEW_TASK_PROGRESS"),
    canPlannerReview: sessionAllows(session, "PLANNER_REVIEW_TASK_PROGRESS"),
    canRaiseProblem: sessionAllows(session, "RAISE_PROBLEM"),
    canManageProblem: sessionAllows(session, "MANAGE_PROBLEM"),
    canManageAction: sessionAllows(session, "MANAGE_ACTION"),
    canRecordHandover: sessionAllows(session, "RECORD_HANDOVER"),
    canCaptureEvidence: sessionAllows(session, "CAPTURE_EVIDENCE"),
    // Composing a package and reporting on one are separate grants: a planner builds the
    // package, the people on the work report against it.
    canManageCriticalWatchlist: sessionAllows(session, "MANAGE_CRITICAL_WATCHLIST"),
    canSubmitCriticalUpdate: sessionAllows(session, "SUBMIT_CRITICAL_UPDATE"),
    canManageMapping: sessionAllows(session, "MANAGE_IMPORT_PROFILE"),
    // Deciding which Project resource is which person. Planner-owned like the mapping above,
    // and shared with an admin, who is the role that maintains who the users are.
    canManageResourceLink: sessionAllows(session, "MANAGE_RESOURCE_LINK"),
    canCreateExportPreview: sessionAllows(session, "CREATE_EXPORT_PREVIEW"),
    canApproveExport: sessionAllows(session, "APPROVE_EXPORT_BATCH"),
    canUploadSourceFile: sessionAllows(session, "UPLOAD_SOURCE_FILE"),
    canRequestParse: sessionAllows(session, "REQUEST_PROJECT_PARSE"),
    canGenerateArtifact: sessionAllows(session, "GENERATE_EXPORT_ARTIFACT"),
    canRecordVerification: sessionAllows(session, "RECORD_EXPORT_VERIFICATION"),
    // Bringing back what Microsoft Project calculated, and reading it back. Planner-owned
    // like the rest of the handoff: the planner is who ran Project against the candidate.
    canReturnCandidate: sessionAllows(session, "RETURN_CANDIDATE_SCHEDULE")
  };
}
