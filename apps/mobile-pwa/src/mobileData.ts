import type { LucideIcon } from "lucide-react";
import {
  Camera,
  ClipboardList,
  Home,
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
};

export type SyncSignal = {
  label: string;
  detail: string;
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
    title: "Synthetic Task A1",
    workPackage: "Synthetic Summary A",
    state: "Ready for update",
    detail: "Progress and actual fields only"
  },
  {
    title: "Synthetic Task A2",
    workPackage: "Synthetic Summary A",
    state: "Evidence pending",
    detail: "Photo metadata shell"
  },
  {
    title: "Synthetic Task B1",
    workPackage: "Synthetic Summary B",
    state: "Handover note",
    detail: "Draft text shell"
  }
];

export const syncSignals: SyncSignal[] = [
  {
    label: "Connection",
    detail: "Review mode"
  },
  {
    label: "Local queue",
    detail: "Not implemented"
  },
  {
    label: "Last refresh",
    detail: "Static scaffold"
  }
];

export const primaryActionIcon = RefreshCw;
export const evidenceActionIcon = ClipboardList;
