import { useCallback } from "react";
import type {
  ActionRecord,
  HandoverNoteRecord,
  ImportReviewSnapshotSummary,
  ProblemRecord,
  TaskProgressUpdateRecord
} from "@shutdown-tracker/api-client";
import { BoundaryNote, PanelHeading, ResourceView, StatusChip } from "../components";
import { formatDateTime, snapshotStatusLabels } from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import { zoneHref } from "../router";
import type { ConsoleZoneId } from "../router";
import type { ZoneProps } from "./ZoneProps";

/**
 * The control-room attention surface.
 *
 * Today answers one question — what needs someone now — and then hands off. It deliberately
 * carries no workflow of its own: every item links to the zone that owns the decision, so
 * this screen cannot become the place where work is quietly done outside its own review
 * chain. Counts and exceptions only; the full tables live where they belong.
 */
export function TodayZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;

  const supervisorQueue = useAsyncResource<TaskProgressUpdateRecord[]>(
    useCallback(() => client.taskProgress.supervisorQueue(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load this shift." }
  );

  const plannerQueue = useAsyncResource<TaskProgressUpdateRecord[]>(
    useCallback(() => client.taskProgress.plannerQueue(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load this shift." }
  );

  const problems = useAsyncResource<ProblemRecord[]>(
    useCallback(() => client.problems.listOpen(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load this shift." }
  );

  const actions = useAsyncResource<ActionRecord[]>(
    useCallback(() => client.actions.listOpen(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load this shift." }
  );

  const handover = useAsyncResource<HandoverNoteRecord[]>(
    useCallback(() => client.handover.listUnacknowledged(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load this shift." }
  );

  const snapshots = useAsyncResource<ImportReviewSnapshotSummary[]>(
    useCallback(() => client.importReview.listSnapshots(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load this shift." }
  );

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Needs a decision" title="Awaiting review">
          <StatusChip label={attentionLabel(countOf(supervisorQueue.state) + countOf(plannerQueue.state))} />
        </PanelHeading>
        <BoundaryNote>
          Nothing here changes the schedule. Supervisor and planner review are separate
          decisions, and neither writes to Microsoft Project.
        </BoundaryNote>

        <ResourceView
          resource={supervisorQueue}
          emptyWhen={() => false}
          emptyMessage="No field updates are awaiting supervisor review."
        >
          {(value) => (
            <AttentionRow
              label="Awaiting supervisor review"
              count={value.length}
              settledMessage="Every field update has been reviewed."
              zoneId="tasks"
              sectionId="supervisor-review"
              linkLabel="Open supervisor review"
            />
          )}
        </ResourceView>

        <ResourceView
          resource={plannerQueue}
          emptyWhen={() => false}
          emptyMessage="Nothing is awaiting planner review."
        >
          {(value) => (
            <AttentionRow
              label="Awaiting planner approval"
              count={value.length}
              settledMessage="Nothing is waiting on a planner."
              zoneId="exports"
              sectionId="planner-review"
              linkLabel="Open planner review"
            />
          )}
        </ResourceView>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Blocked and outstanding" title="Operational exceptions" />

        <ResourceView
          resource={problems}
          emptyWhen={() => false}
          emptyMessage="No problems are open."
        >
          {(value) => (
            <>
              <AttentionRow
                label="Blocking problems"
                count={value.filter((problem) => problem.blocksExecution).length}
                settledMessage="No open problem is stopping work."
                zoneId="problems"
                sectionId="records"
                linkLabel="Open problems"
              />
              <AttentionRow
                label="Open problems"
                count={value.length}
                settledMessage="No problems are open."
                zoneId="problems"
                sectionId="records"
                linkLabel="Open problems"
              />
            </>
          )}
        </ResourceView>

        <ResourceView resource={actions} emptyWhen={() => false} emptyMessage="No actions are open.">
          {(value) => (
            <AttentionRow
              label="Open actions"
              count={value.length}
              settledMessage="No follow-up actions are outstanding."
              zoneId="problems"
              sectionId="records"
              linkLabel="Open actions"
            />
          )}
        </ResourceView>

        <ResourceView
          resource={handover}
          emptyWhen={() => false}
          emptyMessage="No handover notes are waiting."
        >
          {(value) => (
            <AttentionRow
              label="Handover awaiting acknowledgement"
              count={value.length}
              settledMessage="Every handover note has been acknowledged."
              zoneId="problems"
              sectionId="handover"
              linkLabel="Open handover"
            />
          )}
        </ResourceView>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Schedule intake" title="Latest import" />
        <ResourceView
          resource={snapshots}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="No Project snapshot has been imported for this project yet."
        >
          {(value) => {
            const newest = newestSnapshot(value);
            if (newest === null) {
              return <p className="resource-note">No Project snapshot has been imported yet.</p>;
            }
            return (
              <div className="detail-grid">
                <div className="detail-value">
                  <span>Snapshot</span>
                  <strong>Version {newest.snapshotVersion}</strong>
                </div>
                <div className="detail-value">
                  <span>Status</span>
                  <strong>{snapshotStatusLabels[newest.status]}</strong>
                </div>
                <div className="detail-value">
                  <span>Tasks</span>
                  <strong>{newest.taskCount}</strong>
                </div>
                <div className="detail-value">
                  <span>Project status date</span>
                  <strong>{formatDateTime(newest.projectStatusDate)}</strong>
                </div>
              </div>
            );
          }}
        </ResourceView>
        <p className="resource-note">
          <a href={zoneHref("exports", "import-review")}>Open import review</a>
        </p>
      </article>
    </div>
  );
}

/**
 * One attention line.
 *
 * A settled state reads as a sentence rather than a zero, because "0 blocking problems" and
 * "no open problem is stopping work" carry the same number but not the same reassurance.
 */
function AttentionRow({
  label,
  count,
  settledMessage,
  zoneId,
  sectionId,
  linkLabel
}: {
  label: string;
  count: number;
  settledMessage: string;
  zoneId: ConsoleZoneId;
  sectionId: string;
  linkLabel: string;
}) {
  if (count === 0) {
    return (
      <div className="attention-row settled">
        <span>{settledMessage}</span>
      </div>
    );
  }
  return (
    <div className="attention-row">
      <div>
        <strong>{count}</strong>
        <span>{label}</span>
      </div>
      <a href={zoneHref(zoneId, sectionId)}>{linkLabel}</a>
    </div>
  );
}

export function attentionLabel(count: number) {
  if (count === 0) {
    return "Nothing waiting";
  }
  return count === 1 ? "1 waiting" : `${count} waiting`;
}

export function newestSnapshot(
  snapshots: readonly ImportReviewSnapshotSummary[]
): ImportReviewSnapshotSummary | null {
  return snapshots.reduce<ImportReviewSnapshotSummary | null>(
    (newest, snapshot) =>
      newest === null || snapshot.snapshotVersion > newest.snapshotVersion ? snapshot : newest,
    null
  );
}

function countOf(state: { status: string; value?: unknown }) {
  return state.status === "loaded" && Array.isArray(state.value) ? state.value.length : 0;
}
