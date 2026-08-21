import { useCallback, useRef, useState } from "react";
import type {
  ImportBatchRecord,
  ImportReviewSnapshotDetail,
  ImportReviewSnapshotSummary,
  SourceFileUploadResponse
} from "@shutdown-tracker/api-client";
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
 * What an upload actually said.
 *
 * The endpoint answers 200 whether or not it accepted the file: `accepted: false` with a
 * `rejectionReason` is how "that is not a Project file" arrives. Reading only the HTTP status
 * would report a rejection as a success.
 */
export function describeUploadOutcome(response: SourceFileUploadResponse) {
  if (!response.accepted || response.importBatch === null) {
    return {
      accepted: false,
      message: response.rejectionReason ?? response.message
    };
  }
  return {
    accepted: true,
    message: `${response.originalFilename ?? "File"} accepted · ${response.detectedExtension} · ` +
      `${response.sizeBytes} bytes. Parse it to read what it contains.`
  };
}

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
  const upload = useWriteAction();
  const parse = useWriteAction();
  const fileInput = useRef<HTMLInputElement | null>(null);
  // The batch the upload created, held only for as long as this page is open. See the note in the
  // panel: there is no endpoint that lists pending batches, so it cannot be recovered after a reload.
  const [pendingBatch, setPendingBatch] = useState<ImportBatchRecord | null>(null);
  const [intakeNote, setIntakeNote] = useState<string | null>(null);
  const canDecide = session.canAcceptSnapshot;

  const uploadSourceFile = async (file: File) => {
    setIntakeNote(null);
    await upload.run("Schedule uploaded.", async () => {
      const response = await client.sourceFiles.upload(projectId, file, file.name);
      // A rejected upload is a 200 with accepted:false, so branching on a thrown error would
      // treat "this is not a Project file" as a success and leave the planner with no batch and
      // no explanation.
      if (!describeUploadOutcome(response).accepted) {
        setPendingBatch(null);
        setIntakeNote(describeUploadOutcome(response).message);
        throw new Error(describeUploadOutcome(response).message);
      }
      setPendingBatch(response.importBatch);
      setIntakeNote(describeUploadOutcome(response).message);
    });
  };

  const requestParse = async () => {
    if (pendingBatch === null) {
      return;
    }
    const batchId = pendingBatch.id;
    const ok = await parse.run("Parse summary requested.", async () => {
      const response = await client.importBatches.requestParseSummary(projectId, batchId);
      const summary = response.parseSummary;
      setIntakeNote(
        `${summary.taskCount} tasks · ${summary.resourceCount} resources · ` +
          `${summary.assignmentCount} assignments · ${summary.warningCount} warnings · ` +
          `${summary.errorCount} errors`
      );
    });
    if (ok) {
      setPendingBatch(null);
      await snapshots.reload();
      // Point the review panel at what was just imported rather than leaving the planner to find it.
      const reloaded = await client.importReview.listSnapshots(projectId);
      const newest = newestSnapshotId(reloaded);
      if (newest !== null) {
        setSelectedId(newest);
      }
    }
  };

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
        <PanelHeading eyebrow="Schedule intake" title="Bring a schedule in" />
        <BoundaryNote>
          Parsing runs in the project worker. If it is not connected the request is refused and the
          batch is recorded as failed; upload the file again to retry.
        </BoundaryNote>
        <BoundaryNote>
          Request the summary in this session. Nothing lists pending import batches, so reloading
          between these two steps means uploading the file again.
        </BoundaryNote>
        <CapabilityGate
          allowed={session.canUploadSourceFile}
          reason="Importing a schedule is a planner responsibility."
        >
          <div className="mobile-action-row">
            <input
              ref={fileInput}
              type="file"
              accept=".mpp,.xml"
              aria-label="Microsoft Project file"
              disabled={upload.busy || !session.live}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) {
                  void uploadSourceFile(file);
                }
                event.target.value = "";
              }}
            />
          </div>
        </CapabilityGate>
        <WriteFeedback state={upload.state} />
        {pendingBatch ? (
          <CapabilityGate
            allowed={session.canRequestParse}
            reason="Asking the worker to parse a schedule is a planner responsibility."
          >
            <div className="mobile-action-row">
              <button type="button" disabled={parse.busy} onClick={() => void requestParse()}>
                Parse this file
              </button>
            </div>
          </CapabilityGate>
        ) : null}
        <WriteFeedback state={parse.state} />
        {intakeNote ? <p className="write-feedback done">{intakeNote}</p> : null}
      </article>

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
