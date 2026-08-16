import { useEffect, useState } from "react";
import { Activity, Camera, FileSearch, ListChecks, Send, ShieldAlert } from "lucide-react";
import type { Capability } from "@shutdown-tracker/api-client";

/**
 * Console zones.
 *
 * The five top-level zones are the baseline information architecture from the concept pack:
 * Today, Tasks, Problems, Evidence, Exports. Work that does not warrant a zone of its own
 * becomes a section inside one, which is why import review, operational mapping, planner
 * review, and export batches all sit under Exports rather than competing for the sidebar.
 *
 * Routing is by URL fragment. That keeps a zone linkable — a coordinator can send a
 * supervisor the review queue rather than describing where to click — without adding a
 * router dependency or requiring server-side route handling for a static build. Sections are
 * addressable too, as `#/exports/mapping`, so a link can point at the exact surface.
 */

export const consoleZoneIds = ["today", "tasks", "problems", "evidence", "exports"] as const;

export type ConsoleZoneId = (typeof consoleZoneIds)[number];

export type ConsoleSection = {
  id: string;
  /** Shown on the in-zone section switcher. */
  label: string;
  eyebrow: string;
  title: string;
  /**
   * The capability whose absence makes the section read-only.
   *
   * Sections stay visible to every role: seeing that an export is awaiting approval is useful
   * to a supervisor who cannot approve it. Only the write controls are gated.
   */
  writeCapability: Capability;
};

export type ConsoleZone = {
  id: ConsoleZoneId;
  label: string;
  icon: typeof FileSearch;
  sections: readonly ConsoleSection[];
};

export type ConsoleRoute = {
  zoneId: ConsoleZoneId;
  sectionId: string;
};

export const consoleZones: readonly ConsoleZone[] = [
  {
    id: "today",
    label: "Today",
    icon: Activity,
    sections: [
      {
        id: "attention",
        label: "Attention",
        eyebrow: "This shift",
        title: "Today",
        writeCapability: "VIEW_PROJECT"
      }
    ]
  },
  {
    id: "tasks",
    label: "Tasks",
    icon: ListChecks,
    sections: [
      {
        id: "execution",
        label: "Task execution",
        eyebrow: "Live work",
        title: "Task execution",
        writeCapability: "SUBMIT_TASK_PROGRESS"
      },
      {
        id: "supervisor-review",
        label: "Supervisor review",
        eyebrow: "Step one",
        title: "Supervisor review queue",
        writeCapability: "REVIEW_TASK_PROGRESS"
      }
    ]
  },
  {
    id: "problems",
    label: "Problems",
    icon: ShieldAlert,
    sections: [
      {
        id: "records",
        label: "Problems and actions",
        eyebrow: "Operational records",
        title: "Problems and actions",
        writeCapability: "MANAGE_PROBLEM"
      },
      {
        id: "handover",
        label: "Handover",
        eyebrow: "Shift continuity",
        title: "Handover notes",
        writeCapability: "RECORD_HANDOVER"
      }
    ]
  },
  {
    id: "evidence",
    label: "Evidence",
    icon: Camera,
    sections: [
      {
        id: "task-evidence",
        label: "Task evidence",
        eyebrow: "Verification records",
        title: "Task evidence",
        writeCapability: "CAPTURE_EVIDENCE"
      }
    ]
  },
  {
    id: "exports",
    label: "Exports",
    icon: Send,
    sections: [
      {
        id: "import-review",
        label: "Import review",
        eyebrow: "Schedule intake",
        title: "Imported snapshots",
        writeCapability: "ACCEPT_IMPORT_SNAPSHOT"
      },
      {
        id: "mapping",
        label: "Mapping",
        eyebrow: "Import profile",
        title: "Operational categories",
        writeCapability: "MANAGE_IMPORT_PROFILE"
      },
      {
        id: "planner-review",
        label: "Planner review",
        eyebrow: "Step two",
        title: "Planner review queue",
        writeCapability: "PLANNER_REVIEW_TASK_PROGRESS"
      },
      {
        id: "batches",
        label: "Export batches",
        eyebrow: "Controlled return",
        title: "Export batches",
        writeCapability: "APPROVE_EXPORT_BATCH"
      }
    ]
  }
];

export const defaultZoneId: ConsoleZoneId = "today";

export function zoneById(zoneId: ConsoleZoneId): ConsoleZone {
  return consoleZones.find((zone) => zone.id === zoneId) ?? consoleZones[0];
}

/** The named section, or the zone's first section when the fragment names an unknown one. */
export function sectionById(zoneId: ConsoleZoneId, sectionId: string): ConsoleSection {
  const zone = zoneById(zoneId);
  return zone.sections.find((section) => section.id === sectionId) ?? zone.sections[0];
}

export function parseZoneId(hash: string): ConsoleZoneId {
  return parseRoute(hash).zoneId;
}

export function parseRoute(hash: string): ConsoleRoute {
  const [rawZone, rawSection] = hash.replace(/^#\/?/, "").trim().split("/");
  const zoneId = (consoleZoneIds as readonly string[]).includes(rawZone ?? "")
    ? (rawZone as ConsoleZoneId)
    : defaultZoneId;
  return { zoneId, sectionId: sectionById(zoneId, rawSection ?? "").id };
}

export function zoneHref(zoneId: ConsoleZoneId, sectionId?: string) {
  return sectionId === undefined ? `#/${zoneId}` : `#/${zoneId}/${sectionId}`;
}

/** Tracks the current zone and section, staying in step with back/forward navigation. */
export function useZoneRoute(): [ConsoleRoute, (zoneId: ConsoleZoneId, sectionId?: string) => void] {
  const [route, setRoute] = useState<ConsoleRoute>(() =>
    parseRoute(typeof window === "undefined" ? "" : window.location.hash)
  );

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const onHashChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const navigate = (zoneId: ConsoleZoneId, sectionId?: string) => {
    const next = { zoneId, sectionId: sectionById(zoneId, sectionId ?? "").id };
    if (typeof window !== "undefined") {
      window.location.hash = zoneHref(next.zoneId, next.sectionId);
    }
    setRoute(next);
  };

  return [route, navigate];
}
