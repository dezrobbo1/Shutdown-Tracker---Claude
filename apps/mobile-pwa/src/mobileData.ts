import type { LucideIcon } from "lucide-react";
import {
  Camera,
  ClipboardList,
  Home,
  Send,
  ListChecks,
  MessageSquareWarning,
  RefreshCw,
  Signal
} from "lucide-react";

export type MobileNavItem = {
  label: string;
  icon: LucideIcon;
  active?: boolean;
};

export type MobileWorkItem = {
  title: string;
  workPackage: string;
  state: string;
  detail: string;
  percentComplete: string;
  primaryAction: string;
  reviewBadge: string;
  blockerBadge: string;
  evidenceBadge: string;
  syncBadge: string;
};

export type SyncSignal = {
  label: string;
  detail: string;
};

export type MobileProgressFlow = {
  taskName: string;
  currentState: string;
  percentComplete: string;
  actualStart: string;
  actualFinish: string;
  comment: string;
  visualStates: string[];
};

export type MobileSyncQueueItem = {
  label: string;
  task: string;
  detail: string;
  state: string;
};

export const mobileNavItems: MobileNavItem[] = [
  { label: "My Work", icon: Home, active: true },
  { label: "Today", icon: ListChecks },
  { label: "Problems", icon: MessageSquareWarning },
  { label: "Evidence", icon: Camera },
  { label: "Sync", icon: Signal }
];

export const mobileWorkItems: MobileWorkItem[] = [
  {
    title: "C2 cyclone — remove access cover",
    workPackage: "Calciner C2 refractory outage",
    state: "In progress",
    detail: "Imported Project 40%; proposed progress ready for review",
    percentComplete: "75%",
    primaryAction: "Submit progress",
    reviewBadge: "Awaiting review",
    blockerBadge: "No blocker",
    evidenceBadge: "Evidence ready",
    syncBadge: "Server received"
  },
  {
    title: "C2 cyclone — inspect refractory lining",
    workPackage: "Calciner C2 refractory outage",
    state: "Completed",
    detail: "Completion update needs evidence before supervisor acceptance",
    percentComplete: "100%",
    primaryAction: "Request evidence",
    reviewBadge: "Accepted by supervisor",
    blockerBadge: "No blocker",
    evidenceBadge: "Evidence missing",
    syncBadge: "Server received"
  },
  {
    title: "Kiln feed chute — awaiting scaffold",
    workPackage: "Calciner C2 access workfront",
    state: "Blocked",
    detail: "Scaffold not available; progress held from export",
    percentComplete: "25%",
    primaryAction: "Create blocker",
    reviewBadge: "Needs planner review",
    blockerBadge: "Blocked",
    evidenceBadge: "Evidence not required",
    syncBadge: "Server received"
  },
  {
    title: "Valve isolation — field update draft",
    workPackage: "Calciner C2 isolation workfront",
    state: "In progress",
    detail: "Saved locally for later send",
    percentComplete: "15%",
    primaryAction: "Review draft",
    reviewBadge: "Awaiting review",
    blockerBadge: "No blocker",
    evidenceBadge: "Evidence draft",
    syncBadge: "Queued on this device"
  },
  {
    title: "Crane lift — evidence upload retry",
    workPackage: "Calciner C2 lifting workfront",
    state: "Blocked",
    detail: "Could not send but remains saved",
    percentComplete: "30%",
    primaryAction: "Retry send",
    reviewBadge: "Awaiting review",
    blockerBadge: "Blocked",
    evidenceBadge: "Evidence saved locally",
    syncBadge: "Failed to send"
  }
];

export const syncSignals: SyncSignal[] = [
  {
    label: "Connection",
    detail: "Review mode only"
  },
  {
    label: "Local queue",
    detail: "Visual state only"
  },
  {
    label: "Last refresh",
    detail: "Thread may be out of date. Last synced at [time]."
  }
];

export const mobileProgressFlow: MobileProgressFlow = {
  taskName: "C2 cyclone — remove access cover",
  currentState: "In progress",
  percentComplete: "75",
  actualStart: "2026-06-20",
  actualFinish: "",
  comment: "Field progress ready for supervisor review.",
  visualStates: [
    "Saved locally.",
    "Queued on this device. Not yet sent.",
    "Server received.",
    "Could not send. Still saved on this device."
  ]
};

export const mobileSyncQueueItems: MobileSyncQueueItem[] = [
  {
    label: "Queued progress update",
    task: "Valve isolation — field update draft",
    detail: "Queued on this device. Not yet sent.",
    state: "Queued on device"
  },
  {
    label: "Failed progress update",
    task: "Crane lift — evidence upload retry",
    detail: "Could not send. Still saved on this device.",
    state: "Failed"
  },
  {
    label: "Queued evidence item",
    task: "C2 cyclone — inspect refractory lining",
    detail: "Completion photo metadata saved locally.",
    state: "Local draft"
  },
  {
    label: "Server received item",
    task: "C2 cyclone — remove access cover",
    detail: "Server received.",
    state: "Server received"
  },
  {
    label: "Conflict item",
    task: "Permit isolation — re-import review",
    detail: "Thread may be out of date. Last synced at [time].",
    state: "Conflict"
  }
];

export const primaryActionIcon = RefreshCw;
export const evidenceActionIcon = ClipboardList;
export const submitActionIcon = Send;
