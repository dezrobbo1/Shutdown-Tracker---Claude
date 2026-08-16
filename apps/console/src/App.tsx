import { useMemo, useState } from "react";
import { RefreshCw } from "lucide-react";
import { projectRoleLabels, projectRoles } from "@shutdown-tracker/api-client";
import type { ProjectRole } from "@shutdown-tracker/api-client";
import { consoleBaseUrl, createConsoleApiClient, initialConsoleSession } from "./consoleApi";
import { describeSession, writeStoredRole } from "./session";
import type { ConsoleSession } from "./session";
import { consoleZones, sectionById, useZoneRoute, zoneById, zoneHref } from "./router";
import type { ConsoleRoute } from "./router";
import { buildZoneSession } from "./zones/ZoneProps";
import { ImportReviewZone } from "./zones/ImportReviewZone";
import { ExecutionZone } from "./zones/ExecutionZone";
import { PlannerReviewZone, SupervisorReviewZone } from "./zones/ReviewQueueZone";
import { ProblemsZone } from "./zones/ProblemsZone";
import { HandoverZone } from "./zones/HandoverZone";
import { MappingZone } from "./zones/MappingZone";
import { ExportZone } from "./zones/ExportZone";
import { TodayZone } from "./zones/TodayZone";
import { EvidenceZone } from "./zones/EvidenceZone";

/**
 * The Master Console.
 *
 * Five zones: Today, Tasks, Problems, Evidence, Exports. Today asks what needs attention now;
 * the rest are the places the work is actually done. The controlled path back to Microsoft
 * Project — import review, mapping, planner approval, artifact, verification — is gathered
 * under Exports, because it is one sequence rather than five separate destinations.
 */
export function App() {
  const [session, setSession] = useState<ConsoleSession>(initialConsoleSession);
  const [route, navigate] = useZoneRoute();
  const [reloadToken, setReloadToken] = useState(0);

  // Rebuilt with the session so a request is never attributed to a role that is no longer
  // selected. The token forces a rebuild on refresh, remounting the zone's queries.
  const client = useMemo(() => createConsoleApiClient(session), [session]);
  const zoneSession = useMemo(() => buildZoneSession(session), [session]);

  const zone = zoneById(route.zoneId);
  const section = sectionById(route.zoneId, route.sectionId);

  const changeRole = (role: ProjectRole) => {
    if (session.actor === null) {
      return;
    }
    writeStoredRole(typeof window === "undefined" ? undefined : window.localStorage, role);
    setSession({ ...session, actor: { ...session.actor, role } });
  };

  return (
    <div className="console-shell">
      <aside className="sidebar" aria-label="Console navigation">
        <div className="brand">
          <span className="brand-mark">ST</span>
          <div>
            <strong>Shutdown Tracker</strong>
            <span>Master Console</span>
          </div>
        </div>
        <nav className="nav-list">
          {consoleZones.map((item) => (
            <a
              className={item.id === route.zoneId ? "nav-item active" : "nav-item"}
              href={zoneHref(item.id)}
              key={item.id}
              aria-current={item.id === route.zoneId ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();
                navigate(item.id);
              }}
            >
              <item.icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </a>
          ))}
        </nav>

        <div className="session-panel">
          <p className="eyebrow">Acting as</p>
          <select
            value={session.actor?.role ?? ""}
            onChange={(event) => changeRole(event.target.value as ProjectRole)}
            disabled={session.actor === null}
            aria-label="Acting role"
          >
            {session.actor === null ? <option value="">No actor configured</option> : null}
            {projectRoles.map((role) => (
              <option value={role} key={role}>
                {projectRoleLabels[role]}
              </option>
            ))}
          </select>
          <p className="session-note">{describeSession(session)}</p>
          <p className="session-note">
            The server checks your real membership on this project. Selecting a role here only
            changes what this interface offers.
          </p>
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">{section.eyebrow}</p>
            <h1>{section.title}</h1>
          </div>
          <div className="header-actions" aria-label="Console actions">
            <span className="connection-pill">
              {consoleBaseUrl || "Relative API"} · {session.live ? "Live" : "Not configured"}
            </span>
            <button
              type="button"
              className="icon-button"
              aria-label="Reload this zone"
              onClick={() => setReloadToken((value) => value + 1)}
            >
              <RefreshCw size={18} aria-hidden="true" />
            </button>
          </div>
        </header>

        {zone.sections.length > 1 ? (
          <nav className="section-tabs" aria-label={`${zone.label} sections`}>
            {zone.sections.map((item) => (
              <a
                className={item.id === section.id ? "section-tab active" : "section-tab"}
                href={zoneHref(zone.id, item.id)}
                key={item.id}
                aria-current={item.id === section.id ? "page" : undefined}
                onClick={(event) => {
                  event.preventDefault();
                  navigate(zone.id, item.id);
                }}
              >
                {item.label}
              </a>
            ))}
          </nav>
        ) : null}

        <ZoneOutlet
          key={`${route.zoneId}-${section.id}-${reloadToken}`}
          route={{ zoneId: route.zoneId, sectionId: section.id }}
          session={zoneSession}
          client={client}
        />
      </main>
    </div>
  );
}

function ZoneOutlet({
  route,
  session,
  client
}: {
  route: ConsoleRoute;
  session: ReturnType<typeof buildZoneSession>;
  client: ReturnType<typeof createConsoleApiClient>;
}) {
  const props = { session, client };

  switch (`${route.zoneId}/${route.sectionId}`) {
    case "tasks/execution":
      return <ExecutionZone {...props} />;
    case "tasks/supervisor-review":
      return <SupervisorReviewZone {...props} />;
    case "problems/records":
      return <ProblemsZone {...props} />;
    case "problems/handover":
      return <HandoverZone {...props} />;
    case "evidence/task-evidence":
      return <EvidenceZone {...props} />;
    case "exports/import-review":
      return <ImportReviewZone {...props} />;
    case "exports/mapping":
      return <MappingZone {...props} />;
    case "exports/planner-review":
      return <PlannerReviewZone {...props} />;
    case "exports/batches":
      return <ExportZone {...props} />;
    case "today/attention":
    default:
      return <TodayZone {...props} />;
  }
}
