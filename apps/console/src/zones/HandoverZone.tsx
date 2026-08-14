import { useCallback, useState } from "react";
import type { HandoverNoteRecord } from "@shutdown-tracker/api-client";
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
import { leafTasks, taskLabel, useSnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

/**
 * Shift handover.
 *
 * A shutdown runs continuously while the people running it change every twelve hours. A note
 * that needs acknowledgement is not delivered until someone acknowledges it, and that
 * acknowledgement is attributed — otherwise "I was never told" is unanswerable.
 */
export function HandoverZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotTasks = tasks.state.status === "loaded" ? tasks.state.value : null;

  const notes = useAsyncResource<HandoverNoteRecord[]>(
    useCallback(() => client.handover.listUnacknowledged(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load handover notes." }
  );

  const write = useWriteAction();
  const [shiftLabel, setShiftLabel] = useState("");
  const [note, setNote] = useState("");
  const [importedTaskId, setImportedTaskId] = useState("");
  const [requiresAcknowledgement, setRequiresAcknowledgement] = useState(true);

  const create = async () => {
    if (shiftLabel.trim().length === 0 || note.trim().length === 0) {
      return;
    }
    const ok = await write.run("Handover note recorded.", () =>
      client.handover.create(projectId, {
        shiftLabel: shiftLabel.trim(),
        note: note.trim(),
        requiresAcknowledgement,
        importedTaskId: importedTaskId || null
      })
    );
    if (ok) {
      setNote("");
      await notes.reload();
    }
  };

  const acknowledge = async (record: HandoverNoteRecord) => {
    const ok = await write.run("Acknowledged. The acknowledgement is attributed to you.", () =>
      client.handover.acknowledge(projectId, record.id)
    );
    if (ok) {
      await notes.reload();
    }
  };

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Shift continuity" title="Awaiting acknowledgement">
          <StatusChip
            label={notes.state.status === "loaded" ? `${notes.state.value.length} outstanding` : "Loading"}
            tone={notes.state.status === "loaded" && notes.state.value.length === 0 ? "green" : "amber"}
          />
        </PanelHeading>
        <BoundaryNote>
          Notes stay outstanding until someone acknowledges them. Nothing here changes the
          schedule; it carries what the next shift needs to know.
        </BoundaryNote>

        <ResourceView
          resource={notes}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="Every handover note has been acknowledged."
        >
          {(value) => (
            <div className="handover-grid">
              {value.map((record) => (
                <article className="handover-card" key={record.id}>
                  <div>
                    <p className="eyebrow">{record.shiftLabel}</p>
                    <strong>
                      {record.importedTaskId === null
                        ? "Project level"
                        : snapshotTasks === null
                          ? "Task note"
                          : taskLabel(snapshotTasks, record.importedTaskId)}
                    </strong>
                    <p>{record.note}</p>
                  </div>
                  <div className="chip-list">
                    <StatusChip
                      label={record.requiresAcknowledgement ? "Acknowledgement required" : "For information"}
                    />
                    {record.acknowledgedAt ? (
                      <StatusChip label={`Acknowledged ${formatDateTime(record.acknowledgedAt)}`} tone="green" />
                    ) : null}
                  </div>
                  <CapabilityGate
                    allowed={session.canRecordHandover}
                    reason="Acknowledging handover is for field users, supervisors, coordinators, and shutdown control."
                  >
                    <button type="button" disabled={write.busy} onClick={() => void acknowledge(record)}>
                      Acknowledge
                    </button>
                  </CapabilityGate>
                </article>
              ))}
            </div>
          )}
        </ResourceView>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Record" title="New handover note" />
        <CapabilityGate
          allowed={session.canRecordHandover}
          reason="Your role on this project cannot record handover notes."
        >
          <form
            className="progress-form"
            onSubmit={(event) => {
              event.preventDefault();
              void create();
            }}
          >
            <label>
              <span>Shift</span>
              <input
                value={shiftLabel}
                onChange={(event) => setShiftLabel(event.target.value)}
                placeholder="Night shift, 14 Aug"
                required
              />
            </label>
            <label>
              <span>Task</span>
              <select value={importedTaskId} onChange={(event) => setImportedTaskId(event.target.value)}>
                <option value="">Project level</option>
                {snapshotTasks === null
                  ? null
                  : leafTasks(snapshotTasks)
                      .slice(0, 300)
                      .map((task) => (
                        <option value={task.id} key={task.id}>
                          {task.name ?? task.externalId ?? task.id}
                        </option>
                      ))}
              </select>
            </label>
            <label className="wide-field">
              <span>Note</span>
              <textarea
                rows={4}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="What the next shift needs to know"
                required
              />
            </label>
            <label className="wide-field checkbox-field">
              <input
                type="checkbox"
                checked={requiresAcknowledgement}
                onChange={(event) => setRequiresAcknowledgement(event.target.checked)}
              />
              <span>Someone must acknowledge this before it is considered handed over</span>
            </label>
            <div className="mobile-action-row">
              <button
                type="submit"
                disabled={write.busy || shiftLabel.trim().length === 0 || note.trim().length === 0}
              >
                Record note
              </button>
            </div>
          </form>
        </CapabilityGate>
        <WriteFeedback state={write.state} />
      </article>
    </div>
  );
}
