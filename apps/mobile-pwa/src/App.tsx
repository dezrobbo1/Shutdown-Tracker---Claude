import { useCallback, useEffect, useMemo, useState } from "react";
import { Camera, ClipboardList, RefreshCw, ShieldAlert, Sunrise, UploadCloud } from "lucide-react";
import type {
  AssignedWorkView,
  CriticalUpdateSubmitRequest,
  CriticalWorkPackageReportingSummary,
  EvidenceRecord,
  HandoverNoteRecord,
  ImportReviewTaskRow,
  ProblemRecord,
  ReviewIdentity,
  TaskExecutionState
} from "@shutdown-tracker/api-client";
import { projectRoleLabels } from "@shutdown-tracker/api-client";
import type { StatusClass } from "@shutdown-tracker/design-tokens";
import {
  createFieldApiClient,
  describeFieldSession,
  fieldBaseUrl,
  fieldSessionAllows,
  initialFieldSession,
  writeStoredFieldIdentity
} from "./fieldSession";
import type { FieldSession } from "./fieldSession";
import { useFieldQueue } from "./useFieldQueue";
import { pendingCount, rejectedCount } from "./offlineQueue";
import type { QueuedSubmission, SyncState } from "./offlineQueue";

/**
 * The Mobile Field App.
 *
 * Built for someone wearing gloves, standing in a plant, with no reliable signal. Every screen
 * works from what is already on the device: the assigned work is cached after the first load,
 * and a progress report is stored locally before anything is sent. Nothing here writes to
 * Microsoft Project — a report goes to a supervisor, who decides whether it is right.
 */

const reportableStates: TaskExecutionState[] = [
  "READY",
  "IN_PROGRESS",
  "PAUSED",
  "BLOCKED",
  "COMPLETED"
];

const executionStateLabels: Record<TaskExecutionState, string> = {
  NOT_STARTED: "Not started",
  READY: "Ready",
  IN_PROGRESS: "In progress",
  PAUSED: "Paused",
  BLOCKED: "Blocked",
  COMPLETED: "Complete"
};

const syncStateLabels: Record<SyncState, string> = {
  PENDING: "Waiting to send",
  SENDING: "Sending",
  SYNCED: "Server received",
  REJECTED: "Could not be sent"
};

/**
 * The five field zones, plus the progress detail reached from a task.
 *
 * Progress is deliberately not a tab. Reporting is something you do to a task you have
 * chosen, so it opens from My Work rather than sitting as a peer destination that has to ask
 * which task you meant.
 */
type Screen = "work" | "today" | "problems" | "evidence" | "sync" | "progress";

/**
 * How many tasks the work list renders.
 *
 * The list is now the reader's own work: the server resolves it from the resources linked to
 * them and returns only those leaf tasks. The cap stays because a resource on a large shutdown
 * can still hold hundreds of tasks, and the list says when it has truncated rather than quietly
 * ending.
 */
const WORK_LIST_LIMIT = 100;

export function App() {
  const [session, setSession] = useState<FieldSession>(initialFieldSession);
  const client = useMemo(() => createFieldApiClient(session, fieldBaseUrl), [session]);
  const queue = useFieldQueue(client, session.projectId);

  // Empty in any real deployment: the endpoint exists only alongside the review seeder.
  const [identities, setIdentities] = useState<ReviewIdentity[]>([]);
  useEffect(() => {
    let cancelled = false;
    client.reviewIdentities
      .list()
      .then((available) => {
        if (!cancelled) {
          setIdentities(available);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setIdentities([]);
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const changeIdentity = (identity: ReviewIdentity) => {
    writeStoredFieldIdentity(typeof window === "undefined" ? undefined : window.localStorage, {
      userId: identity.id,
      role: identity.role,
      displayName: identity.displayName,
      projectId: identity.projectId
    });
    setSession({
      projectId: identity.projectId,
      actor: { userId: identity.id, role: identity.role, displayName: identity.displayName },
      live: identity.projectId.length > 0
    });
  };

  const [screen, setScreen] = useState<Screen>("work");
  const [tasks, setTasks] = useState<ImportReviewTaskRow[]>([]);
  // The reader's own work, kept separate from `tasks`. `tasks` stays the whole schedule because
  // the problem and evidence pickers need it: somebody who sees a fault on a job that is not
  // theirs must still be able to report it against that job.
  const [assigned, setAssigned] = useState<AssignedWorkView | null>(null);
  const [problems, setProblems] = useState<ProblemRecord[]>([]);
  const [handover, setHandover] = useState<HandoverNoteRecord[]>([]);
  const [criticalPackages, setCriticalPackages] = useState<CriticalWorkPackageReportingSummary[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [loadMessage, setLoadMessage] = useState(
    session.live ? "Loading assigned work…" : "No project configured for this device."
  );

  const loadWork = useCallback(async () => {
    if (!session.live) {
      return;
    }
    setLoadMessage("Loading assigned work…");
    try {
      const snapshots = await client.importReview.listSnapshots(session.projectId);
      const newest = [...snapshots].sort((left, right) => right.snapshotVersion - left.snapshotVersion)[0];
      if (!newest) {
        setLoadMessage("No accepted schedule is available for this project yet.");
        return;
      }
      const detail = await client.importReview.getSnapshot(session.projectId, newest.id);
      setTasks(detail.tasks.filter((task) => !task.summary));
      // Failing to resolve assigned work must not cost the reader the rest of the screen, so this
      // falls back to null and My Work says it could not be resolved rather than showing nothing.
      setAssigned(await client.assignedWork.mine(session.projectId).catch(() => null));
      const open = await client.problems.listOpen(session.projectId).catch(() => []);
      setProblems(open);
      const unacknowledged = await client.handover
        .listUnacknowledged(session.projectId)
        .catch(() => []);
      setHandover(unacknowledged);
      // One request for every active package, rather than walking watchlists on a phone.
      setCriticalPackages(
        await client.criticalWatch.reportingSummary(session.projectId).catch(() => [])
      );
      setLoadMessage("");
    } catch (error) {
      // Work already on the device stays usable; only the refresh failed.
      setLoadMessage(
        tasks.length > 0
          ? "Could not refresh. Showing the work already on this device."
          : `Could not load work: ${error instanceof Error ? error.message : "unknown error"}`
      );
    }
  }, [client, session.live, session.projectId, tasks.length]);

  useEffect(() => {
    void loadWork();
    // Loading once on start; a refresh is an explicit action so the app does not fight a weak
    // connection while someone is trying to use it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const blockingTaskIds = useMemo(
    () =>
      new Set(
        problems
          .filter((problem) => problem.blocksExecution && problem.importedTaskId !== null)
          .map((problem) => problem.importedTaskId as string)
      ),
    [problems]
  );

  /**
   * The newest unsent report per task.
   *
   * Without this the work list shows the server's percentage straight after someone reports a
   * new one, which reads as if the report went nowhere. The queue already knows better.
   */
  const unsentByTaskId = useMemo(() => {
    const byTask = new Map<string, QueuedSubmission>();
    for (const item of queue.state.items) {
      // Only progress reports carry a task percentage; a queued Critical Update says nothing
      // about how far one task has got.
      if (item.syncState === "SYNCED" || item.kind !== "progress") {
        continue;
      }
      byTask.set(item.request.importedTaskId, item);
    }
    return byTask;
  }, [queue.state.items]);

  /**
   * Problems raised on this device that the server has not confirmed.
   *
   * The list on the Problems screen is the server's answer. Without these, a problem raised
   * with no signal disappears from the screen that was just used to raise it.
   */
  const unsentProblems = useMemo(
    () => queue.state.items.filter((item) => item.kind === "problem" && item.syncState !== "SYNCED"),
    [queue.state.items]
  );

  const selectedTask = tasks.find((task) => task.id === selectedTaskId) ?? null;
  const waiting = pendingCount(queue.state.items);
  const rejected = rejectedCount(queue.state.items);

  return (
    <div className="mobile-frame">
      <header className="mobile-header">
        <div>
          <p className="eyebrow">{describeFieldSession(session)}</p>
          <h1>{screenTitle(screen, selectedTask)}</h1>
        </div>
        <span className={queue.state.online ? "connection-pill" : "connection-pill offline"}>
          {queue.state.online ? "Online" : "Offline"}
        </span>
      </header>

      <main className="mobile-content">
        <section className="sync-banner" aria-label="Sync status">
          <div className="sync-banner-main">
            <span className={`sync-status-dot ${waiting > 0 ? "waiting" : "clear"}`} aria-hidden="true" />
            <div>
              <span>{waiting === 0 ? "Everything sent" : `${waiting} waiting to send`}</span>
              <strong>
                {queue.state.durable
                  ? "Reports are saved on this device until the server has them."
                  : "This browser cannot store reports. Do not close the app before sending."}
              </strong>
            </div>
          </div>
          {rejected > 0 ? (
            <div className="sync-banner-details">
              <span>
                <strong>{rejected}</strong> could not be sent and need attention in Sync.
              </span>
            </div>
          ) : null}
        </section>

        {screen === "work" ? (
          <WorkList
            assigned={assigned}
            blockingTaskIds={blockingTaskIds}
            unsentByTaskId={unsentByTaskId}
            loadMessage={loadMessage}
            onSelect={(task) => {
              setSelectedTaskId(task.id);
              setScreen("progress");
            }}
          />
        ) : null}

        {screen === "progress" ? (
          selectedTask === null ? (
            <p className="boundary-copy">Choose a task from My Work first.</p>
          ) : (
            <ProgressCapture
              key={selectedTask.id}
              task={selectedTask}
              blocked={blockingTaskIds.has(selectedTask.id)}
              canSubmit={fieldSessionAllows(session, "SUBMIT_TASK_PROGRESS")}
              onCapture={async (request) => {
                await queue.capture(request, selectedTask.name ?? "Task");
                setScreen("sync");
              }}
            />
          )
        ) : null}

        {screen === "today" ? (
          <TodayScreen
            assigned={assigned}
            problems={problems}
            handover={handover}
            waiting={waiting}
            rejected={rejected}
            loadMessage={loadMessage}
            criticalPackages={criticalPackages}
            canSubmitCriticalUpdate={fieldSessionAllows(session, "SUBMIT_CRITICAL_UPDATE")}
            onCriticalUpdate={async (request, packageName) => {
              await queue.captureCriticalUpdate(request, packageName);
            }}
          />
        ) : null}

        {screen === "problems" ? (
          <ProblemsScreen
            problems={problems}
            unsentProblems={unsentProblems}
            tasks={tasks}
            defaultTaskId={selectedTaskId}
            canRaise={fieldSessionAllows(session, "RAISE_PROBLEM")}
            onRaise={async (request) => {
              await queue.captureProblem(request);
              // The list above is the server's. Reloading it picks up the problem once the
              // queue has sent it; until then the unsent entry is what shows.
              await loadWork();
            }}
          />
        ) : null}

        {screen === "evidence" ? (
          <EvidenceScreen
            tasks={tasks}
            live={session.live}
            online={queue.state.online}
            loadEvidence={(taskId) => client.evidence.listForTask(session.projectId, taskId)}
            captureEvidence={(taskId, file, caption) =>
              captureFieldEvidence(client, session.projectId, taskId, file, caption)
            }
          />
        ) : null}

        {screen === "sync" ? (
          <SyncQueue
            items={queue.state.items}
            flushing={queue.state.flushing}
            online={queue.state.online}
            onFlush={() => void queue.flush()}
            onRetry={(localId) => void queue.retry(localId)}
            onClear={() => void queue.clearSettled()}
            identities={identities}
            currentUserId={session.actor?.userId ?? null}
            onChangeIdentity={changeIdentity}
          />
        ) : null}

        <section className="action-band" aria-label="Field actions">
          <button type="button" onClick={() => void loadWork()}>
            <RefreshCw size={18} aria-hidden="true" />
            <span>Refresh work</span>
          </button>
          <button type="button" onClick={() => setScreen("problems")}>
            <ShieldAlert size={18} aria-hidden="true" />
            <span>Raise problem</span>
          </button>
        </section>
      </main>

      <nav className="bottom-nav" aria-label="Field navigation">
        <NavButton
          icon={ClipboardList}
          label="My Work"
          active={screen === "work" || screen === "progress"}
          onClick={() => setScreen("work")}
        />
        <NavButton icon={Sunrise} label="Today" active={screen === "today"} onClick={() => setScreen("today")} />
        <NavButton
          icon={ShieldAlert}
          label="Problems"
          active={screen === "problems"}
          onClick={() => setScreen("problems")}
        />
        <NavButton
          icon={Camera}
          label="Evidence"
          active={screen === "evidence"}
          onClick={() => setScreen("evidence")}
        />
        <NavButton
          icon={UploadCloud}
          label={waiting > 0 ? `Sync (${waiting})` : "Sync"}
          active={screen === "sync"}
          onClick={() => setScreen("sync")}
        />
      </nav>
    </div>
  );
}

/**
 * My Work.
 *
 * An empty list is not one fact, so this does not render it as one. A schedule nobody has
 * accepted, an account no Project resource points at, a link whose resource the newest schedule
 * has lost, and a genuinely clear day all say different things to somebody standing on site with
 * a radio in their hand — and only the last one means "you are done". Each says which it is and,
 * where somebody else has to act, who.
 */
export function WorkList({
  assigned,
  blockingTaskIds,
  unsentByTaskId,
  loadMessage,
  onSelect
}: {
  assigned: AssignedWorkView | null;
  blockingTaskIds: Set<string>;
  unsentByTaskId: Map<string, QueuedSubmission>;
  loadMessage: string;
  onSelect: (task: ImportReviewTaskRow) => void;
}) {
  if (assigned === null) {
    return (
      <p className="boundary-copy">
        {loadMessage || "Your work could not be resolved. Open Sync to retry when you have signal."}
      </p>
    );
  }

  const tasks = assigned.tasks;

  if (assigned.projectSnapshotId === null) {
    return (
      <p className="boundary-copy">
        No schedule has been accepted for this project yet, so no work is assigned to anyone.
      </p>
    );
  }

  if (!assigned.linked) {
    return (
      <p className="boundary-copy">
        No Microsoft Project resource is linked to your account, so this app cannot tell which work
        is yours. A planner links you to your resource; until then this list stays empty rather than
        guessing.
      </p>
    );
  }

  return (
    <section className="work-list" aria-label="Work assigned to you">
      {loadMessage ? <p className="boundary-copy">{loadMessage}</p> : null}
      {assigned.unmatchedResourceUids.length > 0 ? (
        <p className="boundary-copy">
          {assigned.unmatchedResourceUids.length === assigned.linkedResourceUids.length
            ? "The accepted schedule does not carry the resource you are linked to, so none of your work can be found. Tell a planner."
            : `The accepted schedule does not carry ${assigned.unmatchedResourceUids.length} of the resources you are linked to, so some of your work may be missing. Tell a planner.`}
        </p>
      ) : null}
      {tasks.length === 0 ? (
        <p className="boundary-copy">None of the accepted schedule&apos;s work is assigned to you.</p>
      ) : null}
      {tasks.length > WORK_LIST_LIMIT ? (
        <p className="boundary-copy">
          {`Showing the first ${WORK_LIST_LIMIT} of ${tasks.length} tasks assigned to you.`}
        </p>
      ) : null}
      {tasks.slice(0, WORK_LIST_LIMIT).map((task) => (
        <article className="work-card" key={task.id}>
          <div>
            <p>{task.wbs ?? task.outlineNumber ?? task.externalId ?? "—"}</p>
            <h2>{task.name ?? "Unnamed task"}</h2>
            <span>{task.plannedStart ? `Planned ${formatShort(task.plannedStart)}` : "No planned start"}</span>
            <div className="mobile-chip-row">
              {blockingTaskIds.has(task.id) ? (
                <MobileChip label="Blocked" />
              ) : (
                <MobileChip label="No blocker" />
              )}
              {unsentByTaskId.has(task.id) ? (
                <MobileChip label={syncStateLabels[unsentByTaskId.get(task.id)!.syncState]} />
              ) : null}
            </div>
          </div>
          <aside className="work-card-side">
            <span className="percent-pill">{workCardPercent(task, unsentByTaskId.get(task.id))}</span>
            <button type="button" onClick={() => onSelect(task)}>
              Report
            </button>
          </aside>
        </article>
      ))}
    </section>
  );
}

/**
 * Capturing a progress report.
 *
 * The submit button says what actually happens. "Save" would suggest the report is finished;
 * it is not — it goes to a supervisor for review, and the reporter should expect that.
 */
function ProgressCapture({
  task,
  blocked,
  canSubmit,
  onCapture
}: {
  task: ImportReviewTaskRow;
  blocked: boolean;
  canSubmit: boolean;
  onCapture: (request: {
    importedTaskId: string;
    executionState: TaskExecutionState;
    percentComplete: number | null;
    actualStart: string | null;
    actualFinish: string | null;
    comment: string | null;
  }) => Promise<void>;
}) {
  const [executionState, setExecutionState] = useState<TaskExecutionState>("IN_PROGRESS");
  const [percentComplete, setPercentComplete] = useState("");
  const [actualStart, setActualStart] = useState("");
  const [actualFinish, setActualFinish] = useState("");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);

  const problem = validateFieldProgress(percentComplete, actualStart, actualFinish);

  return (
    <section className="progress-flow" aria-label="Task progress">
      <div className="section-heading">
        <p className="eyebrow">{task.wbs ?? task.externalId ?? "Task"}</p>
        <h2>{task.name ?? "Unnamed task"}</h2>
      </div>
      <p className="boundary-copy">
        Submitting sends this to your supervisor for review. It does not change the schedule.
      </p>

      {blocked ? (
        <p className="field-alert" role="status">
          An open blocker is recorded against this task.
        </p>
      ) : null}

      <form
        className="progress-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (problem !== null || busy) {
            return;
          }
          setBusy(true);
          void onCapture({
            importedTaskId: task.id,
            executionState,
            percentComplete: percentComplete === "" ? null : Number(percentComplete),
            actualStart: toInstant(actualStart),
            actualFinish: toInstant(actualFinish),
            comment: comment || null
          }).finally(() => setBusy(false));
        }}
      >
        <label>
          <span>State</span>
          <select
            value={executionState}
            onChange={(event) => setExecutionState(event.target.value as TaskExecutionState)}
          >
            {reportableStates.map((state) => (
              <option value={state} key={state}>
                {executionStateLabels[state]}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Percent complete</span>
          <input
            value={percentComplete}
            inputMode="numeric"
            onChange={(event) => setPercentComplete(event.target.value)}
            placeholder="0-100"
          />
        </label>
        <label>
          <span>Actual start</span>
          <input type="datetime-local" value={actualStart} onChange={(event) => setActualStart(event.target.value)} />
        </label>
        <label>
          <span>Actual finish</span>
          <input type="datetime-local" value={actualFinish} onChange={(event) => setActualFinish(event.target.value)} />
        </label>
        <label className="wide-field">
          <span>Comment</span>
          <textarea rows={3} value={comment} onChange={(event) => setComment(event.target.value)} />
        </label>

        {problem !== null ? (
          <p className="field-alert" role="alert">
            {problem}
          </p>
        ) : null}

        <div className="mobile-action-row">
          <button type="submit" disabled={!canSubmit || busy || problem !== null}>
            Submit for review
          </button>
        </div>
        {canSubmit ? null : (
          <p className="boundary-copy">Your role on this project cannot report progress.</p>
        )}
      </form>
    </section>
  );
}

function ProblemCapture({
  tasks,
  defaultTaskId,
  canRaise,
  onRaise
}: {
  tasks: ImportReviewTaskRow[];
  defaultTaskId: string | null;
  canRaise: boolean;
  onRaise: (request: {
    importedTaskId: string | null;
    title: string;
    description: string | null;
    blocksExecution: boolean;
  }) => Promise<void>;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [importedTaskId, setImportedTaskId] = useState(defaultTaskId ?? "");
  const [blocksExecution, setBlocksExecution] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  return (
    <section className="progress-flow" aria-label="Raise a problem">
      <div className="section-heading">
        <p className="eyebrow">Problem</p>
        <h2>What is wrong?</h2>
      </div>
      <p className="boundary-copy">
        A problem is a record someone has to answer, not a message. Marking it blocking says
        work cannot continue. It is kept on this device first and sent when there is a
        connection, so it survives being raised where there is none.
      </p>

      <form
        className="progress-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (title.trim().length === 0 || busy) {
            return;
          }
          setBusy(true);
          setMessage(null);
          void onRaise({
            importedTaskId: importedTaskId || null,
            title: title.trim(),
            description: description.trim() || null,
            blocksExecution
          })
            .then(() => {
              setTitle("");
              setDescription("");
              // "Saved on this device" and "the server has it" are different facts. Sync owns
              // the second, and the entry above this form shows which one this problem is at.
              setMessage("Saved on this device. Its progress is in Sync.");
            })
            .catch((error: unknown) => setMessage(describeRaiseFailure(error)))
            .finally(() => setBusy(false));
        }}
      >
        <label className="wide-field">
          <span>Title</span>
          <input value={title} onChange={(event) => setTitle(event.target.value)} required />
        </label>
        <label className="wide-field">
          <span>Detail</span>
          <textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} />
        </label>
        <label className="wide-field">
          <span>Task</span>
          <select value={importedTaskId} onChange={(event) => setImportedTaskId(event.target.value)}>
            <option value="">Not task specific</option>
            {tasks.slice(0, 200).map((task) => (
              <option value={task.id} key={task.id}>
                {task.name ?? task.id}
              </option>
            ))}
          </select>
        </label>
        <label className="wide-field checkbox-field">
          <input
            type="checkbox"
            checked={blocksExecution}
            onChange={(event) => setBlocksExecution(event.target.checked)}
          />
          <span>Work cannot continue</span>
        </label>
        <div className="mobile-action-row">
          <button type="submit" disabled={!canRaise || busy || title.trim().length === 0}>
            Raise problem
          </button>
        </div>
        {message ? <p className="field-alert" role="status">{message}</p> : null}
        {canRaise ? null : <p className="boundary-copy">Your role cannot raise problems on this project.</p>}
      </form>
    </section>
  );
}

/**
 * The sync queue.
 *
 * Shown as its own screen rather than a background detail. "Did my report get through?" is a
 * question a field user genuinely needs answered, and the honest answer distinguishes saved on
 * the device from received by the server.
 */
/**
 * Whether the acting identity may be changed right now.
 *
 * The offline queue is memoised on the API client, so changing identity while reports are waiting
 * would flush work captured by one person under another's actor header. That is both a
 * misattribution and, for a report a planner may not submit, a guaranteed refusal whose message
 * would say nothing about the real cause.
 */
export function canSwitchIdentity(items: QueuedSubmission[]) {
  return pendingCount(items) === 0;
}

function SyncQueue({
  items,
  flushing,
  online,
  onFlush,
  onRetry,
  onClear,
  identities,
  currentUserId,
  onChangeIdentity
}: {
  items: QueuedSubmission[];
  flushing: boolean;
  online: boolean;
  onFlush: () => void;
  onRetry: (localId: string) => void;
  onClear: () => void;
  identities: ReviewIdentity[];
  currentUserId: string | null;
  onChangeIdentity: (identity: ReviewIdentity) => void;
}) {
  const switchable = canSwitchIdentity(items);
  return (
    <section className="sync-queue" aria-label="Sync queue">
      {identities.length > 0 ? (
        <div className="section-heading">
          <p className="eyebrow">Signed in as</p>
          <select
            value={currentUserId ?? ""}
            disabled={!switchable}
            aria-label="Acting identity"
            onChange={(event) => {
              const chosen = identities.find((identity) => identity.id === event.target.value);
              if (chosen) {
                onChangeIdentity(chosen);
              }
            }}
          >
            {identities.map((identity) => (
              <option value={identity.id} key={identity.id}>
                {identity.displayName} · {projectRoleLabels[identity.role]}
              </option>
            ))}
          </select>
          <p className="boundary-copy">
            {switchable
              ? "This device sends reports as whoever is signed in here. The server checks that person's real membership."
              : "Reports on this device were captured by the person signed in now. Send them before switching."}
          </p>
        </div>
      ) : null}

      <div className="section-heading">
        <p className="eyebrow">Sync queue</p>
        <h2>Reports on this device</h2>
      </div>

      {items.length === 0 ? (
        <p className="boundary-copy">Nothing has been captured on this device.</p>
      ) : (
        items.map((item) => (
          <article className="sync-queue-card" key={item.localId}>
            <div>
              <span>{formatShort(item.capturedAt)}</span>
              <strong>{item.subject}</strong>
              <p>{queuedItemDetail(item)}</p>
              {item.lastError ? <p className="field-alert">{item.lastError}</p> : null}
            </div>
            <div className="sync-queue-side">
              <MobileChip label={syncStateLabels[item.syncState]} />
              {item.syncState === "REJECTED" ? (
                <button type="button" onClick={() => onRetry(item.localId)}>
                  Try again
                </button>
              ) : null}
            </div>
          </article>
        ))
      )}

      <div className="mobile-action-row">
        <button type="button" disabled={flushing || !online} onClick={onFlush}>
          {flushing ? "Sending…" : "Send now"}
        </button>
        <button type="button" onClick={onClear}>
          Clear sent
        </button>
      </div>
      {online ? null : <p className="boundary-copy">Offline. Reports will send when a connection returns.</p>}
    </section>
  );
}

/**
 * What this shift needs from this person.
 *
 * Deliberately short: what is blocked, what has not been sent, what someone is waiting to be
 * told. Anything longer belongs on the screen that owns it.
 */
function TodayScreen({
  assigned,
  problems,
  handover,
  waiting,
  rejected,
  loadMessage,
  criticalPackages,
  canSubmitCriticalUpdate,
  onCriticalUpdate
}: {
  assigned: AssignedWorkView | null;
  problems: ProblemRecord[];
  handover: HandoverNoteRecord[];
  waiting: number;
  rejected: number;
  loadMessage: string;
  criticalPackages: CriticalWorkPackageReportingSummary[];
  canSubmitCriticalUpdate: boolean;
  onCriticalUpdate: (
    request: Omit<CriticalUpdateSubmitRequest, "idempotencyKey" | "offlineLocalId">,
    packageName: string
  ) => Promise<void>;
}) {
  const blocking = problems.filter((problem) => problem.blocksExecution);

  if (assigned === null && loadMessage) {
    return <p className="boundary-copy">{loadMessage}</p>;
  }

  return (
    <section className="today-list" aria-label="Today">
      <TodayRow
        count={blocking.length}
        label="problems are stopping work"
        settled="Nothing is blocking work right now."
      />
      <TodayRow count={waiting} label="reports are waiting to send" settled="Every report has been sent." />
      <TodayRow
        count={rejected}
        label="reports could not be sent"
        settled="No report has been refused."
      />
      <TodayRow
        count={handover.length}
        label="handover notes need acknowledging"
        settled="No handover note is waiting on you."
      />
      {/*
        Counting the reader's own work, not the schedule's. "No work is assigned to you" is only
        true once a resource is linked to them; before that the count is not zero, it is unknown,
        and saying zero would read as a clear day.
      */}
      {assigned?.linked ? (
        <TodayRow
          count={assigned.tasks.length}
          label="tasks are assigned to you"
          settled="No work in the accepted schedule is assigned to you."
        />
      ) : (
        <p className="boundary-copy">
          No Microsoft Project resource is linked to your account, so your work cannot be counted.
        </p>
      )}

      {blocking.length > 0 ? (
        <div className="today-detail">
          <h2>Blocking now</h2>
          {blocking.slice(0, 5).map((problem) => (
            <article className="sync-queue-card" key={problem.id}>
              <strong>{problem.title}</strong>
              <span>{problem.description ?? "No detail recorded."}</span>
            </article>
          ))}
        </div>
      ) : null}

      <CriticalUpdateCapture
        packages={criticalPackages}
        canSubmit={canSubmitCriticalUpdate}
        onSubmit={onCriticalUpdate}
      />
    </section>
  );
}

/**
 * Filing a Critical Update from the field.
 *
 * On Today rather than as a sixth destination: the field zones are fixed at My Work, Today,
 * Problems, Evidence and Sync, and `docs/product/frontend-visual-review-scope.md` places Critical
 * Watch on Today. A shift update is a "what is happening now" act, which is what Today is.
 *
 * The package is chosen directly rather than derived from the task in hand. Resolving a task to
 * its package would mean a request per package to read its reported tasks, on the connection least
 * able to afford it, and a reporter knows which package they are reporting on.
 *
 * Queued, not sent: this goes through the same offline queue as progress, because it is captured
 * in the same places under the same conditions and the server pairs the idempotency key with the
 * project, so a retry returns the original report rather than filing a second one.
 */
function CriticalUpdateCapture({
  packages,
  canSubmit,
  onSubmit
}: {
  packages: CriticalWorkPackageReportingSummary[];
  canSubmit: boolean;
  onSubmit: (
    request: Omit<CriticalUpdateSubmitRequest, "idempotencyKey" | "offlineLocalId">,
    packageName: string
  ) => Promise<void>;
}) {
  const [workPackageId, setWorkPackageId] = useState("");
  const [currentFocus, setCurrentFocus] = useState("");
  const [blocker, setBlocker] = useState("");
  const [nextTarget, setNextTarget] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  if (packages.length === 0) {
    return null;
  }

  const chosen = packages.find((entry) => entry.workPackageId === workPackageId) ?? null;

  const submit = async () => {
    if (chosen === null || currentFocus.trim() === "") {
      return;
    }
    setSaving(true);
    setMessage("");
    try {
      await onSubmit(
        {
          criticalWorkPackageId: chosen.workPackageId,
          updateMode: "shift",
          currentFocus: currentFocus.trim(),
          currentBlockerSummary: blocker.trim() === "" ? null : blocker.trim(),
          nextTarget: nextTarget.trim() === "" ? null : nextTarget.trim()
        },
        chosen.name
      );
      setCurrentFocus("");
      setBlocker("");
      setNextTarget("");
      // "Saved on this device" and "the server has it" are different facts. Sync owns the second.
      setMessage("Saved on this device. Its progress is in Sync.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="today-detail critical-update-capture">
      <h2>Critical update</h2>
      {canSubmit ? null : (
        <p className="boundary-copy">
          Filing a Critical Update is a reporting responsibility your role on this project does not
          carry.
        </p>
      )}
      <label className="wide-field">
        <span>Work package</span>
        <select value={workPackageId} onChange={(event) => setWorkPackageId(event.target.value)}>
          <option value="">Choose a package</option>
          {packages.map((entry) => (
            <option value={entry.workPackageId} key={entry.workPackageId}>
              {entry.name}
            </option>
          ))}
        </select>
      </label>
      {chosen === null ? null : (
        <div className="progress-form critical-update-form">
          <label className="wide-field">
            <span>What the crew is on</span>
            <input
              value={currentFocus}
              onChange={(event) => setCurrentFocus(event.target.value)}
              placeholder="Blanking plates on the north face"
            />
          </label>
          <label className="wide-field">
            <span>What is holding it up</span>
            <input
              value={blocker}
              onChange={(event) => setBlocker(event.target.value)}
              placeholder="Leave empty if nothing is"
            />
          </label>
          <label className="wide-field">
            <span>Next target</span>
            <input
              value={nextTarget}
              onChange={(event) => setNextTarget(event.target.value)}
              placeholder="Cover back on before shift end"
            />
          </label>
          <button
            type="button"
            disabled={!canSubmit || saving || currentFocus.trim() === ""}
            onClick={() => void submit()}
          >
            {saving ? "Saving…" : "File update"}
          </button>
          {message ? <p className="boundary-copy">{message}</p> : null}
        </div>
      )}
    </div>
  );
}

function TodayRow({ count, label, settled }: { count: number; label: string; settled: string }) {
  if (count === 0) {
    return (
      <div className="today-row settled">
        <span>{settled}</span>
      </div>
    );
  }
  return (
    <div className="today-row">
      <strong>{count}</strong>
      <span>{label}</span>
    </div>
  );
}

/** Open problems, and the form to raise another. */
function ProblemsScreen({
  problems,
  unsentProblems,
  tasks,
  defaultTaskId,
  canRaise,
  onRaise
}: {
  problems: ProblemRecord[];
  unsentProblems: QueuedSubmission[];
  tasks: ImportReviewTaskRow[];
  defaultTaskId: string | null;
  canRaise: boolean;
  onRaise: (request: { title: string; description: string | null; importedTaskId: string | null; blocksExecution: boolean }) => Promise<void>;
}) {
  return (
    <>
      <section className="problem-list" aria-label="Open problems">
        {problems.length === 0 && unsentProblems.length === 0 ? (
          <p className="boundary-copy">No problems are open on this project.</p>
        ) : null}

        {/* Raised here and not yet confirmed. Shown first, and never as though the project
            already holds them, because nobody else can see one of these yet. */}
        {unsentProblems.map((item) => (
          <article className="sync-queue-card" key={item.localId}>
            <div className="sync-queue-card-head">
              <strong>{item.subject}</strong>
              <MobileChip label={syncStateLabels[item.syncState]} />
            </div>
            <span>{queuedItemDetail(item)}</span>
          </article>
        ))}

        {problems.slice(0, 30).map((problem) => (
          <article className="sync-queue-card" key={problem.id}>
            <div className="sync-queue-card-head">
              <strong>{problem.title}</strong>
              {problem.blocksExecution ? <MobileChip label="Blocked" /> : null}
            </div>
            <span>{problem.description ?? "No detail recorded."}</span>
          </article>
        ))}
      </section>

      <ProblemCapture
        tasks={tasks}
        defaultTaskId={defaultTaskId}
        canRaise={canRaise}
        onRaise={onRaise}
      />
    </>
  );
}

/**
 * Registers an evidence record and sends its file against it, in that order.
 *
 * Two calls rather than one because the record has to exist before there is anything to attach a
 * file to, and because a record whose file did not arrive is a state the product shows rather than
 * hides. Shared shape with the console: the same sequence, so the two apps cannot drift on what
 * "captured" means.
 */
export async function captureFieldEvidence(
  client: FieldApiClient,
  projectId: string,
  importedTaskId: string,
  file: File,
  caption: string
) {
  const registered = await client.evidence.register(projectId, {
    importedTaskId,
    originalFilename: file.name,
    contentType: file.type === "" ? null : file.type,
    sizeBytes: file.size,
    caption: caption.trim() === "" ? null : caption.trim()
  });
  await client.evidence.uploadContent(projectId, registered.id, file, file.name);
}

type FieldApiClient = ReturnType<typeof createFieldApiClient>;

/**
 * Evidence against a task, and capturing more of it.
 *
 * Capture needs a connection, and says so rather than queueing. The progress queue holds small
 * JSON reports; a photo is megabytes, and a queue that fills a phone's storage and then fails to
 * send is worse than one that never accepted the photo. Offline evidence capture is its own piece
 * of work, with its own eviction and retry rules.
 */
function EvidenceScreen({
  tasks,
  live,
  online,
  loadEvidence,
  captureEvidence
}: {
  tasks: ImportReviewTaskRow[];
  live: boolean;
  online: boolean;
  loadEvidence: (taskId: string) => Promise<EvidenceRecord[]>;
  captureEvidence: (taskId: string, file: File, caption: string) => Promise<void>;
}) {
  const [taskId, setTaskId] = useState<string>("");
  const [records, setRecords] = useState<EvidenceRecord[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [caption, setCaption] = useState("");
  const [capturing, setCapturing] = useState(false);
  const [captureMessage, setCaptureMessage] = useState("");
  const [message, setMessage] = useState(
    live ? "Choose a task to see its evidence." : "No project configured for this device."
  );

  const send = async () => {
    if (taskId === "" || file === null) {
      return;
    }
    setCapturing(true);
    setCaptureMessage("Sending…");
    try {
      await captureEvidence(taskId, file, caption);
      setFile(null);
      setCaption("");
      setCaptureMessage("Sent.");
      await refresh(taskId);
    } catch (error) {
      // The record may already exist with its file still missing. Saying "not sent" would be a
      // guess; saying what is true lets the list show which of the two happened.
      setCaptureMessage(
        `Could not send: ${error instanceof Error ? error.message : "unknown error"}. Check the list below.`
      );
      await refresh(taskId);
    } finally {
      setCapturing(false);
    }
  };

  const refresh = async (currentTaskId: string) => {
    if (currentTaskId === "" || !live) {
      return;
    }
    try {
      setRecords(await loadEvidence(currentTaskId));
    } catch {
      // A failed refresh leaves the previous list rather than blanking it.
    }
  };

  const choose = async (nextTaskId: string) => {
    setTaskId(nextTaskId);
    setRecords([]);
    if (nextTaskId === "" || !live) {
      setMessage(live ? "Choose a task to see its evidence." : "No project configured for this device.");
      return;
    }
    setMessage("Loading evidence…");
    try {
      const loaded = await loadEvidence(nextTaskId);
      setRecords(loaded);
      setMessage(loaded.length === 0 ? "No evidence has been recorded against this task." : "");
    } catch (error) {
      setMessage(`Could not load evidence: ${error instanceof Error ? error.message : "unknown error"}`);
    }
  };

  return (
    <section className="evidence-list" aria-label="Evidence">
      <p className="boundary-copy">
        {online
          ? "A photo is sent as you take it. It is not queued, so it needs a connection."
          : "Offline. Evidence needs a connection to send, so capture it again when you are back on."}
      </p>
      <label className="wide-field">
        <span>Task</span>
        <select value={taskId} onChange={(event) => void choose(event.target.value)}>
          <option value="">Choose a task</option>
          {tasks.slice(0, 200).map((task) => (
            <option value={task.id} key={task.id}>
              {task.name ?? "Unnamed task"}
            </option>
          ))}
        </select>
      </label>

      {taskId === "" ? null : (
        <div className="progress-form evidence-capture">
          <label className="wide-field">
            <span>Photo or file</span>
            <input
              type="file"
              accept="image/*"
              capture="environment"
              aria-label="Evidence photo"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <label className="wide-field">
            <span>What it shows</span>
            <input
              value={caption}
              onChange={(event) => setCaption(event.target.value)}
              placeholder="Blanking plate fitted"
            />
          </label>
          <button type="button" disabled={!live || !online || capturing || file === null} onClick={() => void send()}>
            {capturing ? "Sending…" : "Send evidence"}
          </button>
          {captureMessage ? <p className="boundary-copy">{captureMessage}</p> : null}
        </div>
      )}

      {message ? <p className="boundary-copy">{message}</p> : null}

      {records.map((record) => (
        <article className="sync-queue-card" key={record.id}>
          <strong>{record.originalFilename}</strong>
          <span>{record.caption ?? "No caption recorded."}</span>
          <MobileChip
            label={record.storageUri === null ? "File still to send" : "Server received"}
          />
        </article>
      ))}
    </section>
  );
}

function NavButton({
  icon: Icon,
  label,
  active,
  onClick
}: {
  icon: typeof ClipboardList;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={active ? "active" : undefined}
      aria-current={active ? "page" : undefined}
      onClick={onClick}
    >
      <Icon size={19} aria-hidden="true" />
      <span>{label}</span>
    </button>
  );
}

/**
 * What went wrong when a problem could not even be captured.
 *
 * Sending is no longer part of raising one: the capture is written to the queue and the network
 * is Sync's business, so a dead connection never reaches here. What can still reach here is the
 * device failing to store it, and that has to be said plainly — there is nothing holding the
 * problem afterwards, and the person has to know to raise it again.
 */
export function describeRaiseFailure(error: unknown) {
  const detail = error instanceof Error ? error.message : "";
  if (detail === "") {
    return "This device could not keep the problem. Raise it again.";
  }
  return `This device could not keep the problem: ${detail}. Raise it again.`;
}

/**
 * The percentage a reporter should see.
 *
 * An unsent report is the newer fact for the person holding the device, so it wins over the
 * server value — with the sync chip beside it saying the server does not have it yet.
 */
export function workCardPercent(
  task: ImportReviewTaskRow,
  unsent: QueuedSubmission | undefined
) {
  // A Critical Update reports on a work package, not on how far one task has got.
  const pending = unsent?.kind === "progress" ? unsent.request.percentComplete : undefined;
  if (pending !== null && pending !== undefined) {
    return `${pending}%`;
  }
  return task.percentComplete === null ? "—" : `${task.percentComplete}%`;
}

/**
 * The one line under a queued item's subject in the sync list.
 *
 * The kinds report different things, so they say different things: a progress report is a state
 * and a percentage, a Critical Update is what the crew is on, and a problem is what is wrong and
 * whether it stops work. A shared phrasing would have to leave out whichever half did not fit.
 */
export function queuedItemDetail(item: QueuedSubmission) {
  if (item.kind === "progress") {
    const percent = item.request.percentComplete;
    return `${executionStateLabels[item.request.executionState]}${
      percent === null || percent === undefined ? "" : ` · ${percent}%`
    }`;
  }
  if (item.kind === "problem") {
    const detail = item.request.description?.trim();
    // Blocking is the half a supervisor reads first, so it leads.
    const lead = item.request.blocksExecution ? "Blocked" : "Problem";
    return detail === undefined || detail === "" ? lead : `${lead} · ${detail}`;
  }
  return item.request.currentFocus?.trim() || "Critical Update";
}

function MobileChip({ label }: { label: string }) {
  return <span className={`status-chip mobile-chip ${mobileChipTone(label)}`}>{label}</span>;
}

/**
 * The operational state class for a label.
 *
 * Named for what the state means, not for the colour it happens to be. A class called `red` cannot
 * be re-themed and tells a reader nothing about why it is red; the six classes here are the ones
 * `docs/product/design-language-and-status-semantics.md` defines, and the console maps to the same
 * set so a state reads the same in both applications.
 */
export function mobileChipTone(label: string): StatusClass {
  const value = label.toLowerCase();

  if (value.includes("blocked") || value.includes("could not") || value.includes("failed")) {
    return "critical";
  }
  if (value.includes("waiting") || value.includes("sending") || value.includes("queued")) {
    return "warning";
  }
  if (value.includes("server received") || value.includes("no blocker")) {
    return "success";
  }
  if (value.includes("not started") || value.includes("superseded")) {
    return "neutral";
  }
  return "info";
}

export function screenTitle(screen: Screen, task: ImportReviewTaskRow | null) {
  if (screen === "work") {
    return "My Work";
  }
  if (screen === "progress") {
    return task?.name ?? "Progress";
  }
  if (screen === "today") {
    return "Today";
  }
  if (screen === "problems") {
    return "Problems";
  }
  return screen === "evidence" ? "Evidence" : "Sync";
}

/**
 * Checks a report on the device.
 *
 * Validated here because the device may be offline: a report captured with an impossible value
 * would sit in the queue and be rejected hours later, by which time the person who could
 * correct it has gone home.
 */
export function validateFieldProgress(
  percentComplete: string,
  actualStart: string,
  actualFinish: string
): string | null {
  if (percentComplete !== "") {
    const value = Number(percentComplete);
    if (!Number.isFinite(value) || value < 0 || value > 100) {
      return "Percent complete must be between 0 and 100.";
    }
  }
  if (actualStart === "" && actualFinish !== "") {
    return "Record an actual start before an actual finish.";
  }
  if (actualStart !== "" && actualFinish !== "" && new Date(actualFinish) < new Date(actualStart)) {
    return "Actual finish cannot be before actual start.";
  }
  return null;
}

export function toInstant(localValue: string): string | null {
  if (localValue === "") {
    return null;
  }
  const parsed = new Date(localValue);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

function formatShort(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString(undefined, {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}
