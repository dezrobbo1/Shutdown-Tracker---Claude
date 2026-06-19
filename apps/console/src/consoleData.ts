import type { LucideIcon } from "lucide-react";
import {
  ClipboardCheck,
  Database,
  FileCheck2,
  FileSearch,
  ListChecks,
  Radio,
  Send,
  ShieldCheck
} from "lucide-react";
import type { ConsoleReviewData, ConsoleReviewLoadState } from "./apiReviewClient";

export type ConsoleNavItem = {
  label: string;
  icon: LucideIcon;
  active?: boolean;
};

export type ConsoleMetric = {
  label: string;
  value: string;
  detail: string;
  tone: "green" | "blue" | "amber" | "red";
  icon: LucideIcon;
};

export type ReviewRow = {
  item: string;
  source: string;
  state: string;
  owner: string;
};

export type ExportPreviewRow = {
  field: string;
  candidate: string;
  eligibility: string;
};

export const consoleNavItems: ConsoleNavItem[] = [
  { label: "Today", icon: Radio, active: true },
  { label: "Tasks", icon: ListChecks },
  { label: "Problems", icon: FileSearch },
  { label: "Evidence", icon: FileCheck2 },
  { label: "Exports", icon: Send }
];

export const consoleMetrics: ConsoleMetric[] = [
  {
    label: "Source files",
    value: "Validation only",
    detail: "No storage call wired",
    tone: "blue",
    icon: Database
  },
  {
    label: "Parsed snapshots",
    value: "Synthetic review",
    detail: "Local profile services",
    tone: "green",
    icon: ClipboardCheck
  },
  {
    label: "Lineage review",
    value: "Manual review",
    detail: "Suggested links only",
    tone: "amber",
    icon: ShieldCheck
  },
  {
    label: "Export preview",
    value: "Draft batches",
    detail: "Approved leaf updates",
    tone: "red",
    icon: Send
  }
];

export function buildConsoleMetrics(
  reviewData: ConsoleReviewData | null,
  loadState: ConsoleReviewLoadState
): ConsoleMetric[] {
  if (reviewData?.mode !== "live") {
    return [
      {
        label: "Source files",
        value: "Validation only",
        detail: loadState.status === "error" ? "Live fetch unavailable" : "No storage call from console",
        tone: loadState.status === "error" ? "red" : "blue",
        icon: Database
      },
      ...consoleMetrics.slice(1)
    ];
  }

  const selectedTaskCount = reviewData.snapshotDetail?.snapshot.taskCount ?? 0;
  const exportLineCount = reviewData.exportPreview?.batch.lineCount ?? 0;
  const eligibleLineCount = reviewData.exportPreview?.batch.eligibleLineCount ?? 0;

  return [
    {
      label: "Source files",
      value: "API review data",
      detail: "No upload from console",
      tone: "blue",
      icon: Database
    },
    {
      label: "Parsed snapshots",
      value: String(reviewData.snapshots.length),
      detail: selectedTaskCount === 0 ? "No selected snapshot tasks" : `${selectedTaskCount} selected tasks`,
      tone: "green",
      icon: ClipboardCheck
    },
    {
      label: "Lineage review",
      value: "Manual review",
      detail: "No automatic linking",
      tone: "amber",
      icon: ShieldCheck
    },
    {
      label: "Export preview",
      value: reviewData.exportPreview === null ? "Not configured" : `${eligibleLineCount}/${exportLineCount}`,
      detail: reviewData.exportPreview === null ? "Set export batch id" : "Eligible leaf updates",
      tone: reviewData.exportPreview === null ? "amber" : "red",
      icon: Send
    }
  ];
}

export const reviewRows: ReviewRow[] = [
  {
    item: "Synthetic Basic WBS",
    source: "MSPDI fixture",
    state: "Parsed snapshot ready",
    owner: "Planner"
  },
  {
    item: "Summary descendants",
    source: "Imported tasks",
    state: "Awaiting review",
    owner: "Coordinator"
  },
  {
    item: "Approved leaf updates",
    source: "Review records",
    state: "Ready for preview",
    owner: "Package owner"
  }
];

export function buildReviewRows(reviewData: ConsoleReviewData | null): ReviewRow[] {
  if (reviewData?.mode !== "live") {
    return reviewRows;
  }

  if (reviewData.snapshots.length === 0) {
    return [
      {
        item: "No imported snapshots",
        source: "Import review API",
        state: "No records returned",
        owner: reviewData.projectId ?? "Project"
      }
    ];
  }

  const snapshotRows = reviewData.snapshots.map((snapshot) => ({
    item: snapshot.externalProjectName ?? `Snapshot ${snapshot.snapshotVersion}`,
    source: `Batch ${shortId(snapshot.importBatchId)}`,
    state: `${snapshot.status} - ${snapshot.taskCount} tasks`,
    owner: `v${snapshot.snapshotVersion}`
  }));

  const taskRows =
    reviewData.snapshotDetail?.tasks.slice(0, 3).map((task) => ({
      item: task.name ?? task.externalId ?? "Unnamed imported task",
      source: task.summary ? "Summary task" : "Leaf task",
      state: task.summary ? "Review context" : "Leaf update candidate",
      owner: task.outlineNumber ?? task.wbs ?? "Imported task"
    })) ?? [];

  return [...snapshotRows, ...taskRows];
}

export const exportPreviewRows: ExportPreviewRow[] = [
  {
    field: "percent_complete",
    candidate: "Synthetic Task A1",
    eligibility: "Approved leaf task"
  },
  {
    field: "actual_finish",
    candidate: "Synthetic Task A2",
    eligibility: "Approved leaf task"
  },
  {
    field: "physical_percent_complete",
    candidate: "Synthetic Summary A",
    eligibility: "Held from export preview"
  }
];

export function buildExportPreviewRows(reviewData: ConsoleReviewData | null): ExportPreviewRow[] {
  if (reviewData?.mode !== "live") {
    return exportPreviewRows;
  }

  if (reviewData.exportPreview === null) {
    return [
      {
        field: "export_preview",
        candidate: "No export batch configured",
        eligibility: "Set VITE_SHUTDOWN_TRACKER_EXPORT_BATCH_ID to fetch a batch"
      }
    ];
  }

  if (reviewData.exportPreview.lines.length === 0) {
    return [
      {
        field: "export_preview",
        candidate: "No preview lines",
        eligibility: "No export candidates returned"
      }
    ];
  }

  return reviewData.exportPreview.lines.slice(0, 5).map((line) => ({
    field: line.fieldName,
    candidate: line.importedTaskName ?? line.importedTaskExternalId ?? line.importedTaskId,
    eligibility: line.exportEligible ? "Eligible leaf update" : "Held from export preview"
  }));
}

export const exportPreviewSignals = [
  { label: "Preview batch", value: "Draft" },
  { label: "Fields", value: "Progress and actuals" },
  { label: "Source", value: "Approved leaf updates" }
];

export function buildExportPreviewSignals(reviewData: ConsoleReviewData | null) {
  if (reviewData?.mode !== "live") {
    return exportPreviewSignals;
  }

  if (reviewData.exportPreview === null) {
    return [
      { label: "Preview batch", value: "Not configured" },
      { label: "Fields", value: "Progress and actuals" },
      { label: "Source", value: "Live API optional" }
    ];
  }

  const batch = reviewData.exportPreview.batch;

  return [
    { label: "Preview batch", value: batch.status },
    { label: "Lines", value: `${batch.eligibleLineCount}/${batch.lineCount} eligible` },
    { label: "Source", value: "Live API review" }
  ];
}

function shortId(value: string) {
  return value.length > 8 ? value.slice(0, 8) : value;
}
