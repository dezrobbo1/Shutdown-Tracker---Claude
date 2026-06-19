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

export const exportPreviewSignals = [
  { label: "Preview batch", value: "Draft" },
  { label: "Fields", value: "Progress and actuals" },
  { label: "Source", value: "Approved leaf updates" }
];
