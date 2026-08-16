import { useEffect, useState } from "react";
import {
  ClipboardCheck,
  FileSearch,
  FileWarning,
  GitCompare,
  ListChecks,
  Send,
  ShieldAlert,
  Tags
} from "lucide-react";
import type { Capability } from "@shutdown-tracker/api-client";

/**
 * Console zones.
 *
 * Routing is by URL fragment. That keeps a zone linkable — a coordinator can send a
 * supervisor the review queue rather than describing where to click — without adding a
 * router dependency or requiring server-side route handling for a static build.
 */

export const consoleZoneIds = [
  "import-review",
  "execution",
  "review-queue",
  "problems",
  "handover",
  "mapping",
  "export"
] as const;

export type ConsoleZoneId = (typeof consoleZoneIds)[number];

export type ConsoleZone = {
  id: ConsoleZoneId;
  label: string;
  eyebrow: string;
  title: string;
  icon: typeof FileSearch;
  /**
   * The capability whose absence makes the zone read-only.
   *
   * Zones stay visible to every role: seeing that an export is awaiting approval is useful
   * to a supervisor who cannot approve it. Only the write controls are gated.
   */
  writeCapability: Capability;
};

export const consoleZones: readonly ConsoleZone[] = [
  {
    id: "import-review",
    label: "Import review",
    eyebrow: "Schedule intake",
    title: "Imported snapshots",
    icon: FileSearch,
    writeCapability: "ACCEPT_IMPORT_SNAPSHOT"
  },
  {
    id: "execution",
    label: "Execution",
    eyebrow: "Live work",
    title: "Task execution",
    icon: ListChecks,
    writeCapability: "SUBMIT_TASK_PROGRESS"
  },
  {
    id: "review-queue",
    label: "Progress review",
    eyebrow: "Field to export",
    title: "Progress review queues",
    icon: ClipboardCheck,
    writeCapability: "REVIEW_TASK_PROGRESS"
  },
  {
    id: "problems",
    label: "Problems",
    eyebrow: "Operational records",
    title: "Problems and actions",
    icon: ShieldAlert,
    writeCapability: "MANAGE_PROBLEM"
  },
  {
    id: "handover",
    label: "Handover",
    eyebrow: "Shift continuity",
    title: "Handover notes",
    icon: FileWarning,
    writeCapability: "RECORD_HANDOVER"
  },
  {
    id: "mapping",
    label: "Mapping",
    eyebrow: "Import profile",
    title: "Operational categories",
    icon: Tags,
    writeCapability: "MANAGE_IMPORT_PROFILE"
  },
  {
    id: "export",
    label: "Export",
    eyebrow: "Controlled return",
    title: "Export batches",
    icon: Send,
    writeCapability: "APPROVE_EXPORT_BATCH"
  }
];

export const lineageIcon = GitCompare;

export const defaultZoneId: ConsoleZoneId = "import-review";

export function parseZoneId(hash: string): ConsoleZoneId {
  const candidate = hash.replace(/^#\/?/, "").trim();
  return (consoleZoneIds as readonly string[]).includes(candidate)
    ? (candidate as ConsoleZoneId)
    : defaultZoneId;
}

export function zoneHref(zoneId: ConsoleZoneId) {
  return `#/${zoneId}`;
}

export function zoneById(zoneId: ConsoleZoneId): ConsoleZone {
  return consoleZones.find((zone) => zone.id === zoneId) ?? consoleZones[0];
}

/** Tracks the current zone, staying in step with back/forward navigation. */
export function useZoneRoute(): [ConsoleZoneId, (zoneId: ConsoleZoneId) => void] {
  const [zoneId, setZoneId] = useState<ConsoleZoneId>(() =>
    parseZoneId(typeof window === "undefined" ? "" : window.location.hash)
  );

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const onHashChange = () => setZoneId(parseZoneId(window.location.hash));
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const navigate = (next: ConsoleZoneId) => {
    if (typeof window !== "undefined") {
      window.location.hash = zoneHref(next);
    }
    setZoneId(next);
  };

  return [zoneId, navigate];
}
