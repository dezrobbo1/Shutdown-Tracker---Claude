import { useMemo, useState } from "react";
import { RefreshCw } from "lucide-react";
import { consoleBaseUrl, createConsoleApiClient, initialConsoleSession } from "./consoleApi";
import { describeSession } from "./session";
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
import { PeopleZone } from "./zones/PeopleZone";
import { ExportZone } from "./zones/ExportZone";
import { TodayZone } from "./zones/TodayZone";
import { EvidenceZone } from "./zones/EvidenceZone";
import { CriticalWatchZone } from "./zones/CriticalWatchZone";

/**
 * The Master Console.
 *
 * Five zones: Today, Tasks, Problems, Evidence, Exports. Today asks what needs attention now;
 * the rest are the places the work is actually done. The controlled path back to Microsoft
 * Project — import review, mapping, planner approval, artifact, verification — is gathered
 * under Exports, because it is one sequence rather than five separate destinations.
 */
export function App() {
  // Fixed for the life of the page. The trial has one super user, and the build says who it is.
  const [session] = useState<ConsoleSession>(initialConsoleSession);
  const [route, navigate] = useZoneRoute();
  const [reloadToken, setReloadToken] = useState(0);

  // The token forces a rebuild on refresh, remounting the zone's queries.
  const client = useMemo(() => createConsoleApiClient(session), [session]);
  const zoneSession = useMemo(() => buildZoneSession(session), [session]);

  const zone = zoneById(route.zoneId);
  const section = sectionById(route.zoneId, route.sectionId);

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
          <p className="eyebrow">Signed in as</p>
          <p className="session-actor">{describeSession(session)}</p>
          <p className="session-note">
            One person drives this console, and the build decides who. The server resolves their
            real membership on this project and is what actually decides what they may do.
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
    case "tasks/critical-watch":
      return <CriticalWatchZone {...props} />;
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
    case "exports/people":
      return <PeopleZone {...props} />;
    case "exports/planner-review":
      return <PlannerReviewZone {...props} />;
    case "exports/batches":
      return <ExportZone {...props} />;
    case "today/attention":
    default:
      return <TodayZone {...props} />;
  }
}
