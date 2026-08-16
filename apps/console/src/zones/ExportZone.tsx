import { useCallback, useMemo, useState } from "react";
import type {
  ExportBatchState,
  ExportCandidateCreateRequest,
  ExportPreviewDetail,
  TaskProgressUpdateRecord
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
import { exportBatchStateLabels, formatDateTime, formatPercent, shortId } from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import { taskLabel, useSnapshotTasks } from "../useSnapshotTasks";
import type { SnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

/**
 * The controlled return to Microsoft Project.
 *
 * The sequence is deliberately manual at its most important step. Shutdown Tracker generates
 * an MSPDI/XML artifact and records what it contains; it never writes to the master `.mpp`.
 * The planner opens the file themselves, checks it in Microsoft Project, and decides whether
 * to save. Verification below records that this happened — it does not perform it.
 */
export function ExportZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotTasks = tasks.state.status === "loaded" ? tasks.state.value : null;
  const snapshotId = snapshotTasks?.snapshotId ?? null;

  const [batchId, setBatchId] = useState<string | null>(null);
  const [artifactNote, setArtifactNote] = useState<string | null>(null);

  const plannerQueue = useAsyncResource<TaskProgressUpdateRecord[]>(
    useCallback(() => client.taskProgress.plannerQueue(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load export candidates." }
  );

  const preview = useAsyncResource<ExportPreviewDetail | null>(
    useCallback(
      () => (batchId === null ? Promise.resolve(null) : client.exportPreview.get(projectId, batchId)),
      [client, projectId, batchId]
    ),
    { enabled: session.live && batchId !== null, idleMessage: "Create a preview to see what would be exported." }
  );

  const write = useWriteAction();

  /**
   * Approved updates that could change a schedule field.
   *
   * Only updates the planner has already approved appear here. Building a preview from
   * anything else would put an unreviewed field change in front of a planner as if it had
   * passed the chain.
   */
  const candidates = useMemo(() => {
    if (plannerQueue.state.status !== "loaded") {
      return [];
    }
    return plannerQueue.state.value.filter(
      (update) => update.plannerReviewState === "PLANNER_APPROVED" || update.exportState === "ELIGIBLE"
    );
  }, [plannerQueue.state]);

  /**
   * Register each approved field change as an authoritative candidate, then preview those.
   *
   * A preview can only reference candidates the server already holds; it cannot describe a
   * change inline. Registering first is what lets the server re-check at approval that the
   * value still matches the source it was taken from.
   */
  const createPreview = async () => {
    if (snapshotId === null) {
      return;
    }
    const requests = candidates.flatMap((update) => candidateRequestsFor(update, snapshotId));
    if (requests.length === 0) {
      return;
    }
    await write.run("Preview created. Nothing has been sent to Microsoft Project.", async () => {
      const registered = [];
      for (const request of requests) {
        registered.push(await client.exportCandidates.create(projectId, request));
      }
      const created = await client.exportPreview.create(projectId, {
        projectSnapshotId: snapshotId,
        candidateIds: registered.map((candidate) => candidate.id)
      });
      setBatchId(created.batch.id);
      preview.replace(created);
    });
  };

  const runOnBatch = async (
    successMessage: string,
    operation: (currentBatchId: string) => Promise<ExportPreviewDetail>
  ) => {
    if (batchId === null) {
      return;
    }
    const ok = await write.run(successMessage, async () => {
      const detail = await operation(batchId);
      preview.replace(detail);
    });
    return ok;
  };

  const generateArtifact = async () => {
    if (batchId === null) {
      return;
    }
    await write.run("Artifact generated. Open it in Microsoft Project to apply it.", async () => {
      const response = await client.exportPreview.generateArtifact(projectId, batchId);
      preview.replace(response.exportPreview);
      setArtifactNote(
        `${response.workerResponse.artifactSummary.outputFilename} · ` +
          `${response.workerResponse.artifactSummary.taskCount} tasks · ` +
          `${response.workerResponse.artifactSummary.exportedFieldCount} fields · ` +
          `sha256 ${response.workerResponse.exportFileHash.slice(0, 16)}…`
      );
    });
  };

  const detail = preview.state.status === "loaded" ? preview.state.value : null;
  const batchState = detail?.batch.status ?? null;

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Controlled return" title="Export candidates">
          <StatusChip label={`${candidates.length} approved`} tone={candidates.length === 0 ? "blue" : "green"} />
        </PanelHeading>
        <BoundaryNote>
          Only planner-approved updates on leaf tasks become export lines. Shutdown Tracker
          produces MSPDI/XML; it does not write the master `.mpp`, and it recalculates nothing.
        </BoundaryNote>

        <ResourceView
          resource={plannerQueue}
          emptyWhen={() => candidates.length === 0}
          emptyMessage="No planner-approved updates are waiting to be exported."
        >
          {() => (
            <div className="preview-list">
              {candidates.map((update) => (
                <div className="preview-line" key={update.id}>
                  <span>
                    {snapshotTasks === null
                      ? `Task ${shortId(update.importedTaskId)}`
                      : taskLabel(snapshotTasks, update.importedTaskId)}
                  </span>
                  <strong>{describeCandidate(update)}</strong>
                  <em>{exportBatchStateHint(update.exportState)}</em>
                </div>
              ))}
            </div>
          )}
        </ResourceView>

        <CapabilityGate
          allowed={session.canCreateExportPreview}
          reason="Creating an export preview is a planner responsibility."
        >
          <div className="mobile-action-row">
            <button
              type="button"
              disabled={write.busy || snapshotId === null || candidates.length === 0}
              onClick={() => void createPreview()}
            >
              Create export preview
            </button>
          </div>
        </CapabilityGate>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Export batch" title={batchId === null ? "No batch selected" : `Batch ${shortId(batchId)}`}>
          {batchState ? <StatusChip label={exportBatchStateLabels[batchState]} /> : null}
        </PanelHeading>

        <ResourceView
          resource={preview}
          emptyWhen={(value) => value === null}
          emptyMessage="Create a preview to see exactly which fields would change."
        >
          {(value) =>
            value === null ? null : (
              <>
                <div className="detail-grid">
                  <DetailValue label="Lines" value={value.batch.lineCount} />
                  <DetailValue label="Eligible" value={value.batch.eligibleLineCount} />
                  <DetailValue label="Ineligible" value={value.batch.ineligibleLineCount} />
                  <DetailValue label="Approved" value={formatDateTime(value.batch.approvedAt)} />
                  <DetailValue label="Generated" value={formatDateTime(value.batch.generatedAt)} />
                  <DetailValue label="Verified" value={formatDateTime(value.batch.verifiedAt)} />
                </div>

                <h3 className="subheading">What would change</h3>
                <div className="review-table dense" role="table" aria-label="Export preview lines">
                  <div className="review-row review-head" role="row">
                    <span role="columnheader">Task</span>
                    <span role="columnheader">Field</span>
                    <span role="columnheader">Old</span>
                    <span role="columnheader">New</span>
                  </div>
                  {value.lines.map((line) => (
                    <div
                      className={line.exportEligible ? "review-row" : "review-row ineligible"}
                      role="row"
                      key={line.id}
                    >
                      <span role="cell">
                        {line.importedTaskName ?? shortId(line.importedTaskId)}
                        {line.leafTask ? null : <StatusChip label="Summary — not exportable" tone="red" />}
                      </span>
                      <span role="cell">{line.fieldName}</span>
                      <span role="cell">{line.oldValue ?? "—"}</span>
                      <span role="cell">{line.newValue}</span>
                    </div>
                  ))}
                </div>

                <ExportSequence
                  state={value.batch.status}
                  busy={write.busy}
                  session={session}
                  onApprove={() =>
                    void runOnBatch("Approved for export.", (id) => client.exportPreview.approve(projectId, id))
                  }
                  onReject={() =>
                    void runOnBatch("Rejected. Nothing will be generated from this batch.", (id) =>
                      client.exportPreview.reject(projectId, id)
                    )
                  }
                  onGenerate={() => void generateArtifact()}
                  onMarkOpened={() =>
                    void runOnBatch("Recorded that the artifact was opened in Microsoft Project.", (id) =>
                      client.exportPreview.markOpenedInMicrosoftProject(projectId, id, {})
                    )
                  }
                  onVerify={() =>
                    void runOnBatch("Verification recorded against your user.", (id) =>
                      client.exportPreview.verify(projectId, id, {})
                    )
                  }
                />

                {artifactNote ? <p className="write-feedback done">{artifactNote}</p> : null}
                {value.batch.exportFileUri ? (
                  <BoundaryNote>
                    Artifact at <code>{value.batch.exportFileUri}</code>. Open it in Microsoft Project
                    yourself; saving the master plan stays your decision.
                  </BoundaryNote>
                ) : null}
              </>
            )
          }
        </ResourceView>
        <WriteFeedback state={write.state} />
      </article>
    </div>
  );
}

/**
 * The steps of the controlled export, in order.
 *
 * Each step is enabled only in the state that precedes it, so the sequence cannot be skipped:
 * an artifact cannot be generated before approval, and verification cannot be recorded before
 * anyone has opened the file.
 */
function ExportSequence({
  state,
  busy,
  session,
  onApprove,
  onReject,
  onGenerate,
  onMarkOpened,
  onVerify
}: {
  state: ExportBatchState;
  busy: boolean;
  session: ZoneProps["session"];
  onApprove: () => void;
  onReject: () => void;
  onGenerate: () => void;
  onMarkOpened: () => void;
  onVerify: () => void;
}) {
  return (
    <>
      <CapabilityGate
        allowed={session.canApproveExport}
        reason="Approving an export is planner-owned; an administrator is not a routine approver."
      >
        <div className="mobile-action-row">
          <button type="button" disabled={busy || !canApprove(state)} onClick={onApprove}>
            Approve batch
          </button>
          <button type="button" disabled={busy || !canApprove(state)} onClick={onReject}>
            Reject batch
          </button>
        </div>
      </CapabilityGate>

      <CapabilityGate allowed={session.canGenerateArtifact} reason="Generating the artifact is planner-owned.">
        <div className="mobile-action-row">
          <button type="button" disabled={busy || !canGenerate(state)} onClick={onGenerate}>
            Generate MSPDI/XML
          </button>
        </div>
      </CapabilityGate>

      <CapabilityGate
        allowed={session.canRecordVerification}
        reason="Recording what happened in Microsoft Project is planner-owned."
      >
        <div className="mobile-action-row">
          <button type="button" disabled={busy || !canMarkOpened(state)} onClick={onMarkOpened}>
            I opened it in Microsoft Project
          </button>
          <button type="button" disabled={busy || !canVerify(state)} onClick={onVerify}>
            Record verification
          </button>
        </div>
      </CapabilityGate>
    </>
  );
}

export function canApprove(state: ExportBatchState) {
  return state === "DRAFT_PREVIEW" || state === "AWAITING_APPROVAL";
}

export function canGenerate(state: ExportBatchState) {
  return state === "APPROVED";
}

export function canMarkOpened(state: ExportBatchState) {
  return state === "GENERATED";
}

export function canVerify(state: ExportBatchState) {
  return state === "OPENED_IN_MICROSOFT_PROJECT";
}

/**
 * The export candidates an approved update implies.
 *
 * Only fields the update actually carries are emitted. Sending an absent value would blank a
 * field in the schedule that nobody meant to change.
 *
 * A progress update is never edited in place — a correction supersedes it — so the update's own
 * id is its source version, and the server can tell later whether the value it captured still
 * comes from the record it was taken from.
 */
export function candidateRequestsFor(
  update: TaskProgressUpdateRecord,
  projectSnapshotId: string
): ExportCandidateCreateRequest[] {
  const requests: ExportCandidateCreateRequest[] = [];
  const base = {
    projectSnapshotId,
    importedTaskId: update.importedTaskId,
    sourceEntityType: "task_progress_update",
    sourceEntityId: update.id,
    sourceVersion: update.id
  };

  if (update.percentComplete !== null) {
    requests.push({ ...base, fieldName: "percent_complete", proposedValue: String(update.percentComplete) });
  }
  if (update.physicalPercentComplete !== null) {
    requests.push({
      ...base,
      fieldName: "physical_percent_complete",
      proposedValue: String(update.physicalPercentComplete)
    });
  }
  if (update.actualStart !== null) {
    requests.push({ ...base, fieldName: "actual_start", proposedValue: update.actualStart });
  }
  if (update.actualFinish !== null) {
    requests.push({ ...base, fieldName: "actual_finish", proposedValue: update.actualFinish });
  }
  return requests;
}

export function describeCandidate(update: TaskProgressUpdateRecord) {
  const parts: string[] = [];
  if (update.percentComplete !== null) {
    parts.push(formatPercent(update.percentComplete));
  }
  if (update.actualStart !== null) {
    parts.push(`start ${formatDateTime(update.actualStart)}`);
  }
  if (update.actualFinish !== null) {
    parts.push(`finish ${formatDateTime(update.actualFinish)}`);
  }
  return parts.length === 0 ? "No schedule field" : parts.join(" · ");
}

function exportBatchStateHint(exportState: string) {
  return exportState === "ELIGIBLE" ? "Ready for a preview" : exportState.toLowerCase().replace(/_/g, " ");
}
