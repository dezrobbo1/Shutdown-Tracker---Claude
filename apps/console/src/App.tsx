import { useMemo, useState } from "react";
import { RefreshCw } from "lucide-react";
import { projectRoleLabels, projectRoles } from "@shutdown-tracker/api-client";
import type { ProjectRole } from "@shutdown-tracker/api-client";
import { consoleBaseUrl, createConsoleApiClient, initialConsoleSession } from "./consoleApi";
import { describeSession, writeStoredRole } from "./session";
import type { ConsoleSession } from "./session";
import { consoleZones, useZoneRoute, zoneById, zoneHref } from "./router";
import type { ConsoleZoneId } from "./router";
import { buildZoneSession } from "./zones/ZoneProps";
import { ImportReviewZone } from "./zones/ImportReviewZone";
import { ExecutionZone } from "./zones/ExecutionZone";
import { ReviewQueueZone } from "./zones/ReviewQueueZone";
import { ProblemsZone } from "./zones/ProblemsZone";
import { HandoverZone } from "./zones/HandoverZone";
import { MappingZone } from "./zones/MappingZone";
import { ExportZone } from "./zones/ExportZone";

/**
 * The Master Console.
 *
 * One workspace per operational question, rather than one page per database table. The zones
 * follow the controlled path a shutdown actually runs on: a schedule comes in, work is done
 * against it, what was done is reviewed twice, and only then can anything go back to
 * Microsoft Project.
 */
export function App() {
  const [session, setSession] = useState<ConsoleSession>(initialConsoleSession);
  const [zoneId, navigate] = useZoneRoute();
  const [reloadToken, setReloadToken] = useState(0);

  // Rebuilt with the session so a request is never attributed to a role that is no longer
  // selected. The token forces a rebuild on refresh, remounting the zone's queries.
  const client = useMemo(() => createConsoleApiClient(session), [session]);
  const zoneSession = useMemo(() => buildZoneSession(session), [session]);

  const zone = zoneById(zoneId);

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
              className={item.id === zoneId ? "nav-item active" : "nav-item"}
              href={zoneHref(item.id)}
              key={item.id}
              aria-current={item.id === zoneId ? "page" : undefined}
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
            <p className="eyebrow">{zone.eyebrow}</p>
            <h1>{zone.title}</h1>
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

        <ZoneOutlet key={`${zoneId}-${reloadToken}`} zoneId={zoneId} session={zoneSession} client={client} />
      </main>
    </div>
  );
}

function ZoneOutlet({
  zoneId,
  session,
  client
}: {
  zoneId: ConsoleZoneId;
  session: ReturnType<typeof buildZoneSession>;
  client: ReturnType<typeof createConsoleApiClient>;
}) {
  const props = { session, client };

  switch (zoneId) {
    case "execution":
      return <ExecutionZone {...props} />;
    case "review-queue":
      return <ReviewQueueZone {...props} />;
    case "problems":
      return <ProblemsZone {...props} />;
    case "handover":
      return <HandoverZone {...props} />;
    case "mapping":
      return <MappingZone {...props} />;
    case "export":
      return <ExportZone {...props} />;
    case "import-review":
    default:
      return <ImportReviewZone {...props} />;
  }
}
