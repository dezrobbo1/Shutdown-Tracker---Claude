import type { StatusClass } from "@shutdown-tracker/design-tokens";
import type {
  CandidateScheduleRunState,
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
  IN_EXPORT_PREVIEW: "In export preview",
  EXPORTED: "Exported",
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

/**
 * What a candidate schedule run's state means to a planner.
 *
 * "Accepted" is deliberately not "adopted": accepting a candidate is a review decision, and
 * making it the master schedule is a separate act that Shutdown Tracker records rather than
 * performs.
 */
export const candidateRunStateLabels: Record<CandidateScheduleRunState, string> = {
  RETURNED: "Returned — not yet compared",
  DELTA_READY: "Compared against source",
  ACCEPTED: "Accepted by planner",
  REJECTED: "Rejected",
  SUPERSEDED: "Superseded",
  FAILED: "Failed"
};

/**
 * The operational state class for a state name.
 *
 * Named for what the state means, not for the colour it happens to be. The six classes come from
 * `docs/product/design-language-and-status-semantics.md`, and the field app maps to the same set so
 * a state reads the same in both applications.
 *
 * `RESTRICTED` is the one that had no representation before: a summary task that can never be
 * export eligible is not a failure and should not be read as one, which is exactly the confusion a
 * red stamp on a permitted state would cause.
 */
export function toneForState(state: string): StatusClass {
  const normalised = state.toUpperCase();

  if (
    normalised.includes("REJECT") ||
    normalised.includes("BLOCKED") ||
    normalised.includes("FAILED") ||
    normalised.includes("CONFLICT")
  ) {
    return "critical";
  }
  if (
    normalised.includes("NOT_ELIGIBLE") ||
    normalised.includes("NOT ELIGIBLE") ||
    normalised.includes("NOT_REQUIRED") ||
    normalised.includes("NOT REQUIRED") ||
    normalised.includes("RESTRICTED")
  ) {
    return "restricted";
  }
  if (
    normalised.includes("AWAIT") ||
    normalised.includes("SUBMITTED") ||
    normalised.includes("NEEDS") ||
    normalised.includes("CORRECTION") ||
    normalised.includes("PAUSED") ||
    normalised.includes("PENDING") ||
    normalised.includes("QUEUED") ||
    normalised.includes("PARSED")
  ) {
    return "warning";
  }
  if (
    normalised.includes("ACCEPTED") ||
    normalised.includes("APPROVED") ||
    normalised.includes("VERIFIED") ||
    normalised.includes("COMPLETED") ||
    normalised.includes("UPLOADED")
  ) {
    return "success";
  }
  if (
    normalised.includes("NOT_STARTED") ||
    normalised.includes("NOT STARTED") ||
    normalised.includes("SUPERSEDED") ||
    normalised.includes("DRAFT")
  ) {
    return "neutral";
  }
  return "info";
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
