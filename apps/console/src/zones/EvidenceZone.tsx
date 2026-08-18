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
 * Attaching evidence is two calls, not one: the record is registered, then the file is uploaded
 * against it. They are separate because they can be separated in time — a capture made with no
 * connection is registered when one returns and the file follows — and because a record whose
 * file never arrived is a real state the product has to be able to show. If the upload half fails
 * here, the record is left saying the evidence is still outstanding rather than being rolled back
 * into silence.
 *
 * Downloads go through the client rather than a plain link, because the actor headers travel on
 * the request. The blob is saved rather than opened: evidence is whatever someone attached, and a
 * blob URL opened inline runs in this application's origin.
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
  const download = useWriteAction();
  const [file, setFile] = useState<File | null>(null);
  const [caption, setCaption] = useState("");

  const attachEvidence = async () => {
    if (selectedTaskId === null || file === null) {
      return;
    }
    const ok = await write.run(`${file.name} uploaded.`, () =>
      attachEvidenceFile(client, projectId, selectedTaskId, file, caption)
    );
    if (ok) {
      setFile(null);
      setCaption("");
    }
    // Reloaded either way: a failed upload still leaves a registered record awaiting its file,
    // and hiding that would be the one thing this screen must not do.
    await evidence.reload();
  };

  const saveEvidence = async (record: EvidenceRecord) => {
    await download.run(`${record.originalFilename} downloaded.`, async () => {
      const blob = await client.evidence.downloadContent(projectId, record.id);
      saveBlob(blob, record.originalFilename);
    });
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
                      <span>File</span>
                      <strong>
                        {record.storageUri === null
                          ? "Not uploaded — the evidence itself is still outstanding"
                          : fileSizeLabel(record.sizeBytes)}
                      </strong>
                    </div>
                  </div>
                  {record.storageUri === null ? null : (
                    <button type="button" disabled={download.busy} onClick={() => void saveEvidence(record)}>
                      Download
                    </button>
                  )}
                </article>
              ))}
            </div>
          )}
        </ResourceView>
        <WriteFeedback state={download.state} />
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Record evidence" title="Attach a file" />
        <BoundaryNote>
          The record is registered first and the file uploaded against it. If the upload does not
          complete, the record stays as evidence that is still outstanding rather than disappearing.
        </BoundaryNote>

        <CapabilityGate
          allowed={session.canCaptureEvidence}
          reason="Capturing evidence is a field, contractor, supervisor, or inspector responsibility."
        >
          <div className="progress-form">
            <label className="wide-field">
              <span>File</span>
              <input
                type="file"
                aria-label="Evidence file"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            </label>
            {file === null ? null : (
              <p className="capability-reason">
                {file.name} — {fileSizeLabel(file.size)}
              </p>
            )}
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
              disabled={write.busy || selectedTaskId === null || file === null}
              onClick={() => void attachEvidence()}
            >
              Upload evidence
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

/**
 * Registers an evidence record and uploads its file against it, in that order.
 *
 * The order is the point, and is why this is a function rather than two calls inline: registering
 * second would mean a stored file with nothing referencing it, and uploading against a record that
 * does not exist yet is not possible. If the upload fails the record survives in
 * `pending_upload`, which is the product saying the evidence is still outstanding.
 */
export async function attachEvidenceFile(
  client: EvidenceClient,
  projectId: string,
  importedTaskId: string,
  file: EvidenceFile,
  caption: string
) {
  const registered = await client.evidence.register(projectId, {
    importedTaskId,
    originalFilename: file.name,
    contentType: file.type === "" ? null : file.type,
    sizeBytes: file.size,
    caption: caption.trim() === "" ? null : caption.trim()
  });
  return await client.evidence.uploadContent(projectId, registered.id, file, file.name);
}

/** Only the parts of a picked file this flow reads, so a test can hand it a plain object. */
export type EvidenceFile = Blob & { name: string; type: string; size: number };

type EvidenceClient = {
  evidence: {
    register: ZoneProps["client"]["evidence"]["register"];
    uploadContent: ZoneProps["client"]["evidence"]["uploadContent"];
  };
};

/**
 * Saves a downloaded evidence blob.
 *
 * `download` is what keeps this safe: a blob URL opened in a tab runs in this application's
 * origin, and evidence is a file somebody else chose. Saving it never renders it.
 */
function saveBlob(blob: Blob, filename: string) {
  if (typeof URL.createObjectURL !== "function") {
    return;
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function fileSizeLabel(sizeBytes: number | null) {
  if (sizeBytes === null) {
    return "Uploaded";
  }
  if (sizeBytes < 1024) {
    return `${sizeBytes} bytes`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${Math.round(sizeBytes / 1024)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
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
