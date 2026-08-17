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
  canCreateExportPreview: boolean;
  canApproveExport: boolean;
  canGenerateArtifact: boolean;
  canRecordVerification: boolean;
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
    canCreateExportPreview: sessionAllows(session, "CREATE_EXPORT_PREVIEW"),
    canApproveExport: sessionAllows(session, "APPROVE_EXPORT_BATCH"),
    canGenerateArtifact: sessionAllows(session, "GENERATE_EXPORT_ARTIFACT"),
    canRecordVerification: sessionAllows(session, "RECORD_EXPORT_VERIFICATION")
  };
}
