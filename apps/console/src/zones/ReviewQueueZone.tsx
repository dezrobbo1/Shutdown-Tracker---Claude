import { useCallback, useState } from "react";
import type { TaskProgressUpdateRecord } from "@shutdown-tracker/api-client";
import {
  BoundaryNote,
  CapabilityGate,
  PanelHeading,
  ResourceView,
  StatusChip,
  WriteFeedback,
  useWriteAction
} from "../components";
import {
  executionStateLabels,
  exportStateLabels,
  formatDateTime,
  formatPercent,
  plannerReviewStateLabels,
  progressReviewStateLabels
} from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import { taskLabel, useSnapshotTasks } from "../useSnapshotTasks";
import type { SnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

/**
 * The two progress review queues.
 *
 * They are kept side by side because the distinction between them is the point of the
 * product. A supervisor confirms that what was reported actually happened in the field. A
 * planner separately decides whether that confirmed fact should change the master schedule.
 * Neither decision implies the other, and neither one writes to Microsoft Project.
 */
export function ReviewQueueZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotTasks = tasks.state.status === "loaded" ? tasks.state.value : null;

  const supervisorQueue = useAsyncResource<TaskProgressUpdateRecord[]>(
    useCallback(() => client.taskProgress.supervisorQueue(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load the supervisor queue." }
  );

  const plannerQueue = useAsyncResource<TaskProgressUpdateRecord[]>(
    useCallback(() => client.taskProgress.plannerQueue(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load the planner queue." }
  );

  const supervisorWrite = useWriteAction();
  const plannerWrite = useWriteAction();
  const [notes, setNotes] = useState<Record<string, string>>({});

  const noteFor = (id: string) => notes[id] ?? "";
  const setNote = (id: string, value: string) => setNotes((current) => ({ ...current, [id]: value }));

  const decideSupervisor = async (
    update: TaskProgressUpdateRecord,
    decision: "SUPERVISOR_ACCEPTED" | "CORRECTION_REQUESTED" | "REJECTED"
  ) => {
    const ok = await supervisorWrite.run(supervisorOutcomeMessage(decision), () =>
      client.taskProgress.supervisorReview(projectId, update.id, {
        decision,
        note: noteFor(update.id) || null
      })
    );
    if (ok) {
      setNote(update.id, "");
      await supervisorQueue.reload();
      await plannerQueue.reload();
    }
  };

  const decidePlanner = async (update: TaskProgressUpdateRecord, approved: boolean) => {
    const ok = await plannerWrite.run(
      approved
        ? "Approved. The update becomes an export candidate; nothing has been written to Microsoft Project."
        : "Rejected. The reported progress stays on record and is not export eligible.",
      () =>
        client.taskProgress.plannerReview(projectId, update.id, {
          approved,
          note: noteFor(update.id) || null
        })
    );
    if (ok) {
      setNote(update.id, "");
      await plannerQueue.reload();
    }
  };

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Step one" title="Supervisor review">
          <StatusChip label={queueLabel(supervisorQueue.state.status === "loaded" ? supervisorQueue.state.value.length : null)} />
        </PanelHeading>
        <BoundaryNote>
          Supervisor acceptance confirms the work was done as reported. It is not approval to
          change the schedule — that decision belongs to the planner queue below.
        </BoundaryNote>

        <ResourceView
          resource={supervisorQueue}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="No field updates are awaiting supervisor review."
        >
          {(value) => (
            <div className="queue-list">
              {value.map((update) => (
                <article className="queue-card" key={update.id}>
                  <ProgressSummary update={update} tasks={snapshotTasks} />
                  <label className="wide-field">
                    <span>Review note</span>
                    <textarea
                      rows={2}
                      value={noteFor(update.id)}
                      onChange={(event) => setNote(update.id, event.target.value)}
                      placeholder="Why this is accepted, or what needs correcting"
                    />
                  </label>
                  <CapabilityGate
                    allowed={session.canReviewProgress}
                    reason="Reviewing field progress is a supervisor, coordinator, or shutdown control responsibility."
                  >
                    <div className="mobile-action-row">
                      <button
                        type="button"
                        disabled={supervisorWrite.busy}
                        onClick={() => void decideSupervisor(update, "SUPERVISOR_ACCEPTED")}
                      >
                        Accept
                      </button>
                      <button
                        type="button"
                        disabled={supervisorWrite.busy}
                        onClick={() => void decideSupervisor(update, "CORRECTION_REQUESTED")}
                      >
                        Request correction
                      </button>
                      <button
                        type="button"
                        disabled={supervisorWrite.busy}
                        onClick={() => void decideSupervisor(update, "REJECTED")}
                      >
                        Reject
                      </button>
                    </div>
                  </CapabilityGate>
                </article>
              ))}
            </div>
          )}
        </ResourceView>
        <WriteFeedback state={supervisorWrite.state} />
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Step two" title="Planner review">
          <StatusChip label={queueLabel(plannerQueue.state.status === "loaded" ? plannerQueue.state.value.length : null)} />
        </PanelHeading>
        <BoundaryNote>
          Approving here makes an update eligible for an export batch. The planner still opens
          the generated file in Microsoft Project and decides whether to save the master plan.
        </BoundaryNote>

        <ResourceView
          resource={plannerQueue}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="Nothing is awaiting planner review."
        >
          {(value) => (
            <div className="queue-list">
              {value.map((update) => (
                <article className="queue-card" key={update.id}>
                  <ProgressSummary update={update} tasks={snapshotTasks} />
                  <label className="wide-field">
                    <span>Planner note</span>
                    <textarea
                      rows={2}
                      value={noteFor(update.id)}
                      onChange={(event) => setNote(update.id, event.target.value)}
                      placeholder="Recorded with the decision"
                    />
                  </label>
                  <CapabilityGate
                    allowed={session.canPlannerReview}
                    reason="Only a planner decides what may be returned to Microsoft Project."
                  >
                    <div className="mobile-action-row">
                      <button
                        type="button"
                        disabled={plannerWrite.busy}
                        onClick={() => void decidePlanner(update, true)}
                      >
                        Approve for export
                      </button>
                      <button
                        type="button"
                        disabled={plannerWrite.busy}
                        onClick={() => void decidePlanner(update, false)}
                      >
                        Reject
                      </button>
                    </div>
                  </CapabilityGate>
                </article>
              ))}
            </div>
          )}
        </ResourceView>
        <WriteFeedback state={plannerWrite.state} />
      </article>
    </div>
  );
}

function ProgressSummary({
  update,
  tasks
}: {
  update: TaskProgressUpdateRecord;
  tasks: SnapshotTasks | null;
}) {
  return (
    <>
      <div className="queue-card-heading">
        <div>
          <p className="eyebrow">{executionStateLabels[update.executionState]}</p>
          <h3>{tasks === null ? `Task ${update.importedTaskId.slice(0, 8)}` : taskLabel(tasks, update.importedTaskId)}</h3>
        </div>
        <StatusChip label={progressReviewStateLabels[update.progressReviewState]} />
      </div>
      <div className="detail-grid">
        <div className="detail-value">
          <span>Percent complete</span>
          <strong>{formatPercent(update.percentComplete)}</strong>
        </div>
        <div className="detail-value">
          <span>Physical percent</span>
          <strong>{formatPercent(update.physicalPercentComplete)}</strong>
        </div>
        <div className="detail-value">
          <span>Actual start</span>
          <strong>{formatDateTime(update.actualStart)}</strong>
        </div>
        <div className="detail-value">
          <span>Actual finish</span>
          <strong>{formatDateTime(update.actualFinish)}</strong>
        </div>
      </div>
      {update.comment ? <p className="queue-comment">“{update.comment}”</p> : null}
      <div className="chip-list">
        <StatusChip label={plannerReviewStateLabels[update.plannerReviewState]} />
        <StatusChip label={exportStateLabels[update.exportState]} />
      </div>
    </>
  );
}

export function queueLabel(count: number | null) {
  if (count === null) {
    return "Loading";
  }
  return count === 1 ? "1 awaiting" : `${count} awaiting`;
}

export function supervisorOutcomeMessage(
  decision: "SUPERVISOR_ACCEPTED" | "CORRECTION_REQUESTED" | "REJECTED"
) {
  if (decision === "SUPERVISOR_ACCEPTED") {
    return "Accepted. Where the update could change the schedule it now awaits planner review.";
  }
  if (decision === "CORRECTION_REQUESTED") {
    return "Correction requested. The update returns to the reporter and is not export eligible.";
  }
  return "Rejected. The report stays on record and will not reach an export batch.";
}
