import { useCallback, useState } from "react";
import type { EvidenceRecord, EvidenceStatus } from "@shutdown-tracker/api-client";
import {
  BoundaryNote,
  CapabilityGate,
  PanelHeading,
  ResourceView,
  StatusChip,
  WriteFeedback,
  useWriteAction
} from "../components";
import { useAsyncResource } from "../useAsyncResource";
import { leafTasks, taskLabel, useSnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

/**
 * Evidence recorded against a task.
 *
 * The product stores evidence metadata and a pointer to where the file lives; it does not
 * carry the file itself. Until an object-storage upload path exists, this screen registers
 * the record and says plainly that the binary is not handled here, rather than offering a
 * file picker that would imply an upload the product cannot perform.
 */
export function EvidenceZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotTasks = tasks.state.status === "loaded" ? tasks.state.value : null;
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const evidence = useAsyncResource<EvidenceRecord[]>(
    useCallback(
      () =>
        selectedTaskId === null
          ? Promise.resolve([])
          : client.evidence.listForTask(projectId, selectedTaskId),
      [client, projectId, selectedTaskId]
    ),
    {
      enabled: session.live && selectedTaskId !== null,
      // Idle has two causes here, and they need different answers: nothing to act on yet
      // versus nothing chosen yet.
      idleMessage: session.live
        ? "Choose a task to see the evidence recorded against it."
        : "Configure a project and actor to load evidence."
    }
  );

  const write = useWriteAction();
  const [filename, setFilename] = useState("");
  const [storageUri, setStorageUri] = useState("");
  const [caption, setCaption] = useState("");

  const registerEvidence = async () => {
    if (selectedTaskId === null || filename.trim() === "") {
      return;
    }
    const ok = await write.run("Evidence registered. The file itself is not stored here.", () =>
      client.evidence.register(projectId, {
        importedTaskId: selectedTaskId,
        originalFilename: filename.trim(),
        storageUri: storageUri.trim() === "" ? null : storageUri.trim(),
        caption: caption.trim() === "" ? null : caption.trim()
      })
    );
    if (ok) {
      setFilename("");
      setStorageUri("");
      setCaption("");
      await evidence.reload();
    }
  };

  const options = snapshotTasks === null ? [] : leafTasks(snapshotTasks).slice(0, 300);

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Verification records" title="Evidence for a task">
          {selectedTaskId === null ? null : (
            <StatusChip
              label={evidenceCountLabel(
                evidence.state.status === "loaded" ? evidence.state.value.length : null
              )}
            />
          )}
        </PanelHeading>
        <BoundaryNote>
          Evidence is recorded against one task and read back per task. There is no
          project-wide evidence list yet, so choose the task you are checking.
        </BoundaryNote>

        <label className="wide-field">
          <span>Task</span>
          <select
            value={selectedTaskId ?? ""}
            onChange={(event) => setSelectedTaskId(event.target.value === "" ? null : event.target.value)}
            disabled={options.length === 0}
          >
            <option value="">
              {options.length === 0 ? "No imported tasks are available" : "Choose a task"}
            </option>
            {options.map((task) => (
              <option value={task.id} key={task.id}>
                {snapshotTasks === null ? task.id : taskLabel(snapshotTasks, task.id)}
              </option>
            ))}
          </select>
        </label>

        <ResourceView
          resource={evidence}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="No evidence has been recorded against this task."
        >
          {(value) => (
            <div className="queue-list">
              {value.map((record) => (
                <article className="queue-card" key={record.id}>
                  <div className="queue-card-heading">
                    <div>
                      <p className="eyebrow">{record.contentType ?? "Unknown type"}</p>
                      <h3>{record.originalFilename}</h3>
                    </div>
                    <StatusChip label={evidenceStatusLabels[record.status]} />
                  </div>
                  {record.caption ? <p className="queue-comment">{record.caption}</p> : null}
                  <div className="detail-grid">
                    <div className="detail-value">
                      <span>Stored at</span>
                      <strong>{record.storageUri ?? "Not uploaded"}</strong>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </ResourceView>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Record evidence" title="Register a file" />
        <BoundaryNote>
          This records that evidence exists and where it is kept. Shutdown Tracker does not
          upload or store the file itself yet, so a record without a location is a note that
          the evidence is still outstanding.
        </BoundaryNote>

        <CapabilityGate
          allowed={session.canCaptureEvidence}
          reason="Capturing evidence is a field, contractor, supervisor, or inspector responsibility."
        >
          <div className="progress-form">
            <label className="wide-field">
              <span>File name</span>
              <input
                value={filename}
                onChange={(event) => setFilename(event.target.value)}
                placeholder="Refractory crew lead — blanking plate fitted.jpg"
              />
            </label>
            <label className="wide-field">
              <span>Stored at</span>
              <input
                value={storageUri}
                onChange={(event) => setStorageUri(event.target.value)}
                placeholder="Where the file is kept, if it has been stored"
              />
            </label>
            <label className="wide-field">
              <span>Caption</span>
              <textarea
                rows={2}
                value={caption}
                onChange={(event) => setCaption(event.target.value)}
                placeholder="What this evidence shows"
              />
            </label>
            <button
              type="button"
              disabled={write.busy || selectedTaskId === null || filename.trim() === ""}
              onClick={() => void registerEvidence()}
            >
              Register evidence
            </button>
            {selectedTaskId === null ? (
              <p className="capability-reason">Choose a task first.</p>
            ) : null}
          </div>
        </CapabilityGate>
        <WriteFeedback state={write.state} />
      </article>
    </div>
  );
}

export const evidenceStatusLabels: Record<EvidenceStatus, string> = {
  PENDING_UPLOAD: "Awaiting upload",
  UPLOADED: "Uploaded",
  LINKED: "Linked",
  UNLINKED: "Unlinked",
  SUPERSEDED: "Superseded",
  FAILED: "Upload failed"
};

export function evidenceCountLabel(count: number | null) {
  if (count === null) {
    return "Loading";
  }
  return count === 1 ? "1 record" : `${count} records`;
}
