import { useCallback, useState } from "react";
import type { ImportReviewSnapshotDetail, ImportReviewSnapshotSummary } from "@shutdown-tracker/api-client";
import {
  BoundaryNote,
  CapabilityGate,
  DetailValue,
  PanelHeading,
  ResourceView,
  StatusChip,
  WriteFeedback,
  useWriteAction
} from "../components";
import { formatDateTime, formatPercent, snapshotStatusLabels } from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import type { ZoneProps } from "./ZoneProps";

/**
 * Import review.
 *
 * A parsed snapshot is not yet the project's working schedule: a planner reads what the
 * parser found and decides whether it represents the file they intended to import. Accepting
 * is the point at which execution starts referring to this version, so it is a deliberate
 * act with an audit record, not a consequence of uploading.
 */
export function ImportReviewZone({ session, client }: ZoneProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const projectId = session.projectId;

  const snapshots = useAsyncResource<ImportReviewSnapshotSummary[]>(
    useCallback(() => client.importReview.listSnapshots(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load imported snapshots." }
  );

  const activeSnapshotId =
    selectedId ??
    (snapshots.state.status === "loaded" ? newestSnapshotId(snapshots.state.value) : null);

  const detail = useAsyncResource<ImportReviewSnapshotDetail | null>(
    useCallback(
      () =>
        activeSnapshotId === null
          ? Promise.resolve(null)
          : client.importReview.getSnapshot(projectId, activeSnapshotId),
      [client, projectId, activeSnapshotId]
    ),
    { enabled: session.live && activeSnapshotId !== null, idleMessage: "Select a snapshot to inspect it." }
  );

  const accept = useWriteAction();
  const canDecide = session.canAcceptSnapshot;

  const decide = async (decision: "accept" | "reject") => {
    if (activeSnapshotId === null) {
      return;
    }
    const verb = decision === "accept" ? "Accepted" : "Rejected";
    const ok = await accept.run(`${verb} snapshot. The decision is recorded against your user.`, () =>
      decision === "accept"
        ? client.importReview.acceptSnapshot(projectId, activeSnapshotId)
        : client.importReview.rejectSnapshot(projectId, activeSnapshotId)
    );
    if (ok) {
      await snapshots.reload();
      await detail.reload();
    }
  };

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Schedule intake" title="Imported snapshots" />
        <BoundaryNote>
          Microsoft Project remains the schedule authority. Accepting a snapshot records which
          imported version execution refers to; it does not alter the file.
        </BoundaryNote>
        <ResourceView
          resource={snapshots}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="No snapshots have been parsed for this project yet."
        >
          {(value) => (
            <div className="review-table" role="table" aria-label="Imported snapshots">
              <div className="review-row review-head" role="row">
                <span role="columnheader">Version</span>
                <span role="columnheader">Project</span>
                <span role="columnheader">State</span>
                <span role="columnheader">Tasks</span>
              </div>
              {[...value]
                .sort((left, right) => right.snapshotVersion - left.snapshotVersion)
                .map((snapshot) => (
                  <button
                    type="button"
                    className={
                      snapshot.id === activeSnapshotId ? "review-row selectable selected" : "review-row selectable"
                    }
                    role="row"
                    key={snapshot.id}
                    onClick={() => setSelectedId(snapshot.id)}
                    aria-pressed={snapshot.id === activeSnapshotId}
                  >
                    <span role="cell">v{snapshot.snapshotVersion}</span>
                    <span role="cell">{snapshot.externalProjectName ?? "Unnamed project"}</span>
                    <span role="cell">
                      <StatusChip label={snapshotStatusLabels[snapshot.status]} />
                    </span>
                    <span role="cell">{snapshot.taskCount}</span>
                  </button>
                ))}
            </div>
          )}
        </ResourceView>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Snapshot" title="What the parser found">
          {detail.state.status === "loaded" && detail.state.value !== null ? (
            <StatusChip label={snapshotStatusLabels[detail.state.value.snapshot.status]} />
          ) : null}
        </PanelHeading>

        <ResourceView
          resource={detail}
          emptyWhen={(value) => value === null}
          emptyMessage="Select a snapshot to inspect what was imported."
        >
          {(value) =>
            value === null ? null : (
              <>
                <div className="detail-grid">
                  <DetailValue label="Project UID" value={value.snapshot.externalProjectUid ?? "—"} />
                  <DetailValue label="Status date" value={formatDateTime(value.snapshot.projectStatusDate)} />
                  <DetailValue
                    label="Parser"
                    value={`${value.snapshot.parserName ?? "unknown"} ${value.snapshot.parserVersion ?? ""}`.trim()}
                  />
                  <DetailValue label="Tasks" value={value.snapshot.taskCount} />
                  <DetailValue label="Summary tasks" value={value.snapshot.summaryTaskCount} />
                  <DetailValue label="Leaf tasks" value={value.snapshot.leafTaskCount} />
                  <DetailValue label="Resources" value={value.snapshot.resourceCount} />
                  <DetailValue label="Assignments" value={value.snapshot.assignmentCount} />
                  <DetailValue label="Custom fields" value={value.snapshot.extendedAttributeCount} />
                  <DetailValue label="Warnings" value={value.snapshot.warningCount} />
                  <DetailValue label="Errors" value={value.snapshot.errorCount} />
                </div>

                <h3 className="subheading">Imported tasks</h3>
                <div className="review-table dense" role="table" aria-label="Imported tasks">
                  <div className="review-row review-head" role="row">
                    <span role="columnheader">WBS</span>
                    <span role="columnheader">Task</span>
                    <span role="columnheader">Planned</span>
                    <span role="columnheader">Percent</span>
                  </div>
                  {value.tasks.slice(0, 200).map((task) => (
                    <div className="review-row" role="row" key={task.id}>
                      <span role="cell">{task.wbs ?? task.outlineNumber ?? task.externalId ?? "—"}</span>
                      <span role="cell" style={{ paddingLeft: `${(task.outlineLevel ?? 0) * 12}px` }}>
                        {task.summary ? <strong>{task.name ?? "Unnamed"}</strong> : (task.name ?? "Unnamed")}
                      </span>
                      <span role="cell">{formatDateTime(task.plannedStart)}</span>
                      <span role="cell">{formatPercent(task.percentComplete)}</span>
                    </div>
                  ))}
                </div>
                {value.tasks.length > 200 ? (
                  <BoundaryNote>
                    Showing the first 200 of {value.tasks.length} imported tasks.
                  </BoundaryNote>
                ) : null}

                <CapabilityGate
                  allowed={canDecide}
                  reason="Accepting or rejecting an imported snapshot is a planner or administrator decision."
                >
                  <div className="mobile-action-row">
                    <button type="button" disabled={accept.busy} onClick={() => void decide("accept")}>
                      Accept snapshot
                    </button>
                    <button type="button" disabled={accept.busy} onClick={() => void decide("reject")}>
                      Reject snapshot
                    </button>
                  </div>
                </CapabilityGate>
                <WriteFeedback state={accept.state} />
              </>
            )
          }
        </ResourceView>
      </article>
    </div>
  );
}

export function newestSnapshotId(snapshots: ImportReviewSnapshotSummary[]) {
  return [...snapshots].sort((left, right) => right.snapshotVersion - left.snapshotVersion)[0]?.id ?? null;
}
