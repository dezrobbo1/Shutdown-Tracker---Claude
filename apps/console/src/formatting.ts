import type {
  ExportBatchState,
  PlannerReviewState,
  ProgressExportState,
  ProgressReviewState,
  ProjectSnapshotStatus,
  TaskExecutionState
} from "@shutdown-tracker/api-client";

/**
 * Display helpers shared by the zones.
 *
 * State labels are written out rather than shown as enum constants. A supervisor reading
 * "Awaiting supervisor review" knows what is being asked of them; "SUBMITTED" does not say
 * who is waiting on whom.
 */

export const executionStateLabels: Record<TaskExecutionState, string> = {
  NOT_STARTED: "Not started",
  READY: "Ready",
  IN_PROGRESS: "In progress",
  PAUSED: "Paused",
  BLOCKED: "Blocked",
  COMPLETED: "Complete"
};

export const progressReviewStateLabels: Record<ProgressReviewState, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Awaiting supervisor review",
  SUPERVISOR_ACCEPTED: "Supervisor accepted",
  CORRECTION_REQUESTED: "Correction requested",
  REJECTED: "Rejected",
  SUPERSEDED: "Superseded"
};

export const plannerReviewStateLabels: Record<PlannerReviewState, string> = {
  NOT_REQUIRED: "No planner review needed",
  NEEDS_PLANNER_REVIEW: "Awaiting planner review",
  PLANNER_APPROVED: "Planner approved",
  PLANNER_REJECTED: "Planner rejected"
};

export const exportStateLabels: Record<ProgressExportState, string> = {
  NOT_ELIGIBLE: "Not export eligible",
  ELIGIBLE: "Export eligible",
  EXPORT_BLOCKED: "Export blocked",
  APPROVED_FOR_EXPORT: "Approved for export",
  IN_EXPORT_PREVIEW: "In export preview",
  ARTIFACT_GENERATED: "Artifact generated",
  OPENED_IN_MICROSOFT_PROJECT: "Opened in Microsoft Project",
  VERIFIED: "Verified",
  SUPERSEDED: "Superseded"
};

export const snapshotStatusLabels: Record<ProjectSnapshotStatus, string> = {
  PARSED: "Parsed, awaiting review",
  ACCEPTED: "Accepted",
  REJECTED: "Rejected",
  SUPERSEDED: "Superseded",
  FAILED: "Failed"
};

export const exportBatchStateLabels: Record<ExportBatchState, string> = {
  DRAFT_PREVIEW: "Draft preview",
  AWAITING_APPROVAL: "Awaiting planner approval",
  APPROVED: "Approved for export",
  REJECTED: "Rejected",
  GENERATED: "Artifact generated",
  OPENED_IN_MICROSOFT_PROJECT: "Opened in Microsoft Project",
  VERIFIED: "Verified",
  SUPERSEDED: "Superseded",
  FAILED: "Failed"
};

/** Maps a state to the visual tone the stylesheet already defines. */
export function toneForState(state: string): "green" | "amber" | "red" | "blue" {
  const normalised = state.toUpperCase();

  if (
    normalised.includes("REJECT") ||
    normalised.includes("BLOCKED") ||
    normalised.includes("FAILED")
  ) {
    return "red";
  }
  if (
    normalised.includes("AWAIT") ||
    normalised.includes("SUBMITTED") ||
    normalised.includes("NEEDS") ||
    normalised.includes("CORRECTION") ||
    normalised.includes("PAUSED") ||
    normalised.includes("PARSED")
  ) {
    return "amber";
  }
  if (
    normalised.includes("ACCEPTED") ||
    normalised.includes("APPROVED") ||
    normalised.includes("VERIFIED") ||
    normalised.includes("COMPLETED")
  ) {
    return "green";
  }
  return "blue";
}

/**
 * A date for reading, in the browser's zone.
 *
 * Imported and actual dates are stored with an offset. Rendering the raw value invites a
 * supervisor to read a UTC timestamp as local time and accept the wrong shift's progress.
 */
export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "—";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function formatPercent(value: number | null | undefined) {
  return value === null || value === undefined ? "—" : `${value}%`;
}

/** A shortened identifier, for correlating a record with a log line without dominating a row. */
export function shortId(value: string | null | undefined) {
  return value ? value.slice(0, 8) : "—";
}
