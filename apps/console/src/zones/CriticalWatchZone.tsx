import { useCallback, useState } from "react";
import type {
  CriticalUpdateRecord,
  CriticalWatchlistRecord,
  CriticalWorkPackageRecord
} from "@shutdown-tracker/api-client";
import {
  BoundaryNote,
  CapabilityGate,
  PanelHeading,
  ResourceView,
  StatusChip,
  WriteFeedback,
  useWriteAction
} from "../components";
import { formatDateTime } from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import { summaryTasks, taskLabel, useSnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

/**
 * Critical Watchlists, Critical Work Packages, and the reports made against them.
 *
 * A Critical Work Package is a reporting object, not a scheduling one. Its membership is
 * chosen from summary tasks in the imported schedule; nothing here calculates critical path,
 * float, or slack, and no report written here changes an imported date. The screen states
 * that boundary rather than leaving it to be inferred from what the controls happen to do.
 *
 * Two capabilities are in play and they are deliberately separate. Composing a package is a
 * planning act; reporting against one is an execution act. A planner may build the package
 * and still, correctly, be unable to file a report on it.
 */
export function CriticalWatchZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const [watchlistId, setWatchlistId] = useState<string | null>(null);
  const [workPackageId, setWorkPackageId] = useState<string | null>(null);

  const watchlists = useAsyncResource<CriticalWatchlistRecord[]>(
    useCallback(() => client.criticalWatch.listWatchlists(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load Critical Watch." }
  );

  const workPackages = useAsyncResource<CriticalWorkPackageRecord[]>(
    useCallback(
      () =>
        watchlistId === null
          ? Promise.resolve([])
          : client.criticalWatch.listWorkPackages(projectId, watchlistId),
      [client, projectId, watchlistId]
    ),
    {
      enabled: session.live && watchlistId !== null,
      idleMessage: session.live
        ? "Choose a watchlist to see the work packages on it."
        : "Configure a project and actor to load Critical Watch."
    }
  );

  const updates = useAsyncResource<CriticalUpdateRecord[]>(
    useCallback(
      () =>
        workPackageId === null
          ? Promise.resolve([])
          : client.criticalWatch.listUpdates(projectId, workPackageId),
      [client, projectId, workPackageId]
    ),
    {
      enabled: session.live && workPackageId !== null,
      idleMessage: session.live
        ? "Choose a work package to see what has been reported on it."
        : "Configure a project and actor to load Critical Watch."
    }
  );

  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotTasks = tasks.state.status === "loaded" ? tasks.state.value : null;
  const sourceOptions = snapshotTasks === null ? [] : summaryTasks(snapshotTasks).slice(0, 300);

  const compose = useWriteAction();
  const report = useWriteAction();

  const [watchlistName, setWatchlistName] = useState("");
  const [packageName, setPackageName] = useState("");
  const [sourceTaskId, setSourceTaskId] = useState("");
  const [includeDescendants, setIncludeDescendants] = useState(true);
  const [currentFocus, setCurrentFocus] = useState("");
  const [blocker, setBlocker] = useState("");
  const [nextTarget, setNextTarget] = useState("");

  const createWatchlist = async () => {
    if (watchlistName.trim() === "") {
      return;
    }
    const ok = await compose.run("Watchlist created.", () =>
      client.criticalWatch.createWatchlist(projectId, watchlistName.trim())
    );
    if (ok) {
      setWatchlistName("");
      await watchlists.reload();
    }
  };

  const createWorkPackage = async () => {
    if (watchlistId === null || packageName.trim() === "") {
      return;
    }
    const ok = await compose.run("Work package created. Add a summary task as its source.", () =>
      client.criticalWatch.createWorkPackage(projectId, watchlistId, packageName.trim())
    );
    if (ok) {
      setPackageName("");
      await workPackages.reload();
    }
  };

  const addSource = async () => {
    if (workPackageId === null || sourceTaskId === "" || snapshotTasks?.snapshotId == null) {
      return;
    }
    const ok = await compose.run("Source added to the work package.", () =>
      client.criticalWatch.addSource(projectId, workPackageId, {
        projectSnapshotId: snapshotTasks.snapshotId as string,
        importedTaskId: sourceTaskId,
        includeDescendants
      })
    );
    if (ok) {
      setSourceTaskId("");
    }
  };

  const submitUpdate = async () => {
    if (workPackageId === null || currentFocus.trim() === "") {
      return;
    }
    const ok = await report.run("Report submitted. It does not change the schedule.", () =>
      client.criticalWatch.submitUpdate(projectId, {
        criticalWorkPackageId: workPackageId,
        updateMode: "ad_hoc",
        currentFocus: currentFocus.trim(),
        currentBlockerSummary: blocker.trim() === "" ? null : blocker.trim(),
        nextTarget: nextTarget.trim() === "" ? null : nextTarget.trim(),
        lines: []
      })
    );
    if (ok) {
      setCurrentFocus("");
      setBlocker("");
      setNextTarget("");
      await updates.reload();
    }
  };

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Reporting groups" title="Watchlists and work packages" />
        <BoundaryNote>
          A Critical Work Package groups work for reporting. Membership is chosen from summary
          tasks in the imported schedule — it is not derived from critical path, float, or
          slack, none of which Shutdown Tracker calculates.
        </BoundaryNote>

        <label className="wide-field">
          <span>Watchlist</span>
          <select
            value={watchlistId ?? ""}
            onChange={(event) => {
              setWatchlistId(event.target.value === "" ? null : event.target.value);
              setWorkPackageId(null);
            }}
          >
            <option value="">
              {watchlistCount(watchlists.state) === 0
                ? "No watchlists exist yet"
                : "Choose a watchlist"}
            </option>
            {watchlists.state.status === "loaded"
              ? watchlists.state.value.map((watchlist) => (
                  <option value={watchlist.id} key={watchlist.id}>
                    {watchlist.name}
                  </option>
                ))
              : null}
          </select>
        </label>

        <ResourceView
          resource={workPackages}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="This watchlist has no work packages yet."
        >
          {(value) => (
            <div className="queue-list">
              {value.map((workPackage) => (
                <article className="queue-card" key={workPackage.id}>
                  <div className="queue-card-heading">
                    <div>
                      <p className="eyebrow">Critical work package</p>
                      <h3>{workPackage.name}</h3>
                    </div>
                    <StatusChip
                      label={workPackage.id === workPackageId ? "Selected" : workPackage.status}
                    />
                  </div>
                  {workPackage.description ? (
                    <p className="queue-comment">{workPackage.description}</p>
                  ) : null}
                  <button type="button" onClick={() => setWorkPackageId(workPackage.id)}>
                    Show reports
                  </button>
                </article>
              ))}
            </div>
          )}
        </ResourceView>

        <CapabilityGate
          allowed={session.canManageCriticalWatchlist}
          reason="Composing a Critical Watchlist is a planner or shutdown control responsibility."
        >
          <div className="progress-form">
            <label className="wide-field">
              <span>New watchlist</span>
              <input
                value={watchlistName}
                onChange={(event) => setWatchlistName(event.target.value)}
                placeholder="Kiln shutdown critical watch"
              />
            </label>
            <button
              type="button"
              disabled={compose.busy || watchlistName.trim() === ""}
              onClick={() => void createWatchlist()}
            >
              Create watchlist
            </button>

            <label className="wide-field">
              <span>New work package</span>
              <input
                value={packageName}
                onChange={(event) => setPackageName(event.target.value)}
                placeholder="Refractory replacement"
              />
            </label>
            <button
              type="button"
              disabled={compose.busy || watchlistId === null || packageName.trim() === ""}
              onClick={() => void createWorkPackage()}
            >
              Create work package
            </button>
            {watchlistId === null ? (
              <p className="capability-reason">Choose a watchlist first.</p>
            ) : null}

            <label className="wide-field">
              <span>Add a summary task as a source</span>
              <select
                value={sourceTaskId}
                onChange={(event) => setSourceTaskId(event.target.value)}
                disabled={sourceOptions.length === 0}
              >
                <option value="">
                  {sourceOptions.length === 0
                    ? "No summary tasks are available in the newest snapshot"
                    : "Choose a summary task"}
                </option>
                {sourceOptions.map((task) => (
                  <option value={task.id} key={task.id}>
                    {snapshotTasks === null ? task.id : taskLabel(snapshotTasks, task.id)}
                  </option>
                ))}
              </select>
            </label>
            <label className="wide-field">
              <span>Include everything under it</span>
              <input
                type="checkbox"
                checked={includeDescendants}
                onChange={(event) => setIncludeDescendants(event.target.checked)}
              />
            </label>
            <button
              type="button"
              disabled={compose.busy || workPackageId === null || sourceTaskId === ""}
              onClick={() => void addSource()}
            >
              Add source
            </button>
            <p className="capability-reason">
              A package drawing on more than one summary task is recorded as multi-summary.
              That is decided by the server from what the package already covers.
            </p>
          </div>
        </CapabilityGate>
        <WriteFeedback state={compose.state} />
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Critical updates" title="Reports on this package">
          {workPackageId === null ? null : (
            <StatusChip
              label={reportCountLabel(
                updates.state.status === "loaded" ? updates.state.value.length : null
              )}
            />
          )}
        </PanelHeading>
        <BoundaryNote>
          A report records what is happening. It does not update Microsoft Project. A
          correction supersedes the earlier report rather than editing it, so what was said at
          the time stays readable.
        </BoundaryNote>

        <ResourceView
          resource={updates}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="Nothing has been reported on this work package yet."
        >
          {(value) => (
            <div className="queue-list">
              {value.map((update) => (
                <article className="queue-card" key={update.id}>
                  <div className="queue-card-heading">
                    <div>
                      <p className="eyebrow">{formatDateTime(update.submittedAt)}</p>
                      <h3>{update.currentFocus ?? "No focus recorded"}</h3>
                    </div>
                    <StatusChip label={updateStatusLabel(update)} />
                  </div>
                  {update.currentBlockerSummary ? (
                    <p className="queue-comment">Blocked: {update.currentBlockerSummary}</p>
                  ) : null}
                  {update.nextTarget ? (
                    <p className="queue-comment">Next: {update.nextTarget}</p>
                  ) : null}
                </article>
              ))}
            </div>
          )}
        </ResourceView>

        <CapabilityGate
          allowed={session.canSubmitCriticalUpdate}
          reason="Reporting on a Critical Work Package is done by the people on the work — shutdown control, coordinators, supervisors, field users, contractors, and inspectors."
        >
          <div className="progress-form">
            <label className="wide-field">
              <span>Current focus</span>
              <textarea
                rows={2}
                value={currentFocus}
                onChange={(event) => setCurrentFocus(event.target.value)}
                placeholder="What this package is working on now"
              />
            </label>
            <label className="wide-field">
              <span>Blocker</span>
              <textarea
                rows={2}
                value={blocker}
                onChange={(event) => setBlocker(event.target.value)}
                placeholder="What is holding it up, if anything"
              />
            </label>
            <label className="wide-field">
              <span>Next target</span>
              <input
                value={nextTarget}
                onChange={(event) => setNextTarget(event.target.value)}
                placeholder="What happens next"
              />
            </label>
            <button
              type="button"
              disabled={report.busy || workPackageId === null || currentFocus.trim() === ""}
              onClick={() => void submitUpdate()}
            >
              Submit report
            </button>
            {workPackageId === null ? (
              <p className="capability-reason">Choose a work package first.</p>
            ) : null}
          </div>
        </CapabilityGate>
        <WriteFeedback state={report.state} />
      </article>
    </div>
  );
}

export function reportCountLabel(count: number | null) {
  if (count === null) {
    return "Loading";
  }
  return count === 1 ? "1 report" : `${count} reports`;
}

/** A superseded report is kept and labelled, never hidden. */
export function updateStatusLabel(update: {
  status: string;
  supersedesCriticalUpdateId: string | null;
}) {
  if (update.status === "superseded") {
    return "Superseded";
  }
  return update.supersedesCriticalUpdateId === null ? "Reported" : "Correction";
}

function watchlistCount(state: { status: string; value?: unknown }) {
  return state.status === "loaded" && Array.isArray(state.value) ? state.value.length : 0;
}
