import {
  evidenceActionIcon as EvidenceActionIcon,
  mobileProgressFlow,
  mobileNavItems,
  mobileSyncQueueItems,
  mobileWorkItems,
  primaryActionIcon as PrimaryActionIcon,
  submitActionIcon as SubmitActionIcon,
  syncSignals
} from "./mobileData";

export function App() {
  return (
    <div className="mobile-frame">
      <header className="mobile-header">
        <div>
          <p className="eyebrow">Field shell</p>
          <h1>My Work</h1>
        </div>
        <span className="connection-pill">Review mode</span>
      </header>

      <main className="mobile-content">
        <section className="sync-banner" aria-label="Sync status">
          <div className="sync-banner-main">
            <span className="sync-status-dot" aria-hidden="true" />
            <div>
              <span>{syncSignals[0].label}</span>
              <strong>{syncSignals[0].detail}</strong>
            </div>
          </div>
          <div className="sync-banner-details">
            {syncSignals.slice(1).map((signal) => (
              <span key={signal.label}>
                <strong>{signal.label}:</strong> {signal.detail}
              </span>
            ))}
          </div>
        </section>

        <section className="work-list" aria-label="Assigned work">
          {mobileWorkItems.map((item) => (
            <article className="work-card" key={item.title}>
              <div>
                <p>{item.workPackage}</p>
                <h2>{item.title}</h2>
                <span>{item.detail}</span>
                <div
                  className="mobile-chip-row"
                  aria-label={`${item.title}: ${item.reviewBadge}; ${item.blockerBadge}; ${item.evidenceBadge}; ${item.syncBadge}`}
                >
                  <MobileChip label={item.blockerBadge !== "No blocker" ? item.blockerBadge : item.evidenceBadge} />
                  <MobileChip label={item.syncBadge} />
                </div>
              </div>
              <aside className="work-card-side">
                <strong className="work-state">{item.state}</strong>
                <span className="percent-pill">{item.percentComplete}</span>
                <button type="button" disabled>{item.primaryAction}</button>
              </aside>
            </article>
          ))}
        </section>

        <section className="progress-flow" aria-label="Mobile Task Progress flow">
          <div className="section-heading">
            <p className="eyebrow">Task Progress</p>
            <h2>{mobileProgressFlow.taskName}</h2>
          </div>
          <p className="boundary-copy">Submit progress for supervisor review.</p>
          <form className="progress-form">
            <label>
              <span>Current state</span>
              <input value={mobileProgressFlow.currentState} readOnly />
            </label>
            <label>
              <span>Percent complete</span>
              <input value={mobileProgressFlow.percentComplete} readOnly inputMode="numeric" />
            </label>
            <label>
              <span>Actual start</span>
              <input value={mobileProgressFlow.actualStart} readOnly />
            </label>
            <label>
              <span>Actual finish</span>
              <input value={mobileProgressFlow.actualFinish} readOnly placeholder="Not set" />
            </label>
            <label className="wide-field">
              <span>Comment</span>
              <textarea value={mobileProgressFlow.comment} readOnly rows={3} />
            </label>
          </form>
          <div className="shortcut-grid" aria-label="Mobile progress shortcuts">
            <button type="button" disabled>Blocker shortcut</button>
            <button type="button" disabled>Evidence shortcut</button>
          </div>
          <div className="mobile-action-row">
            <button type="button" disabled>
              <SubmitActionIcon size={17} aria-hidden="true" />
              <span>Submit</span>
            </button>
            <button type="button" disabled>Save draft</button>
          </div>
          <div className="mobile-state-stack">
            {mobileProgressFlow.visualStates.map((state) => (
              <MobileChip label={state} key={state} />
            ))}
          </div>
        </section>

        <section className="sync-queue" aria-label="Mobile Sync Queue">
          <div className="section-heading">
            <p className="eyebrow">Sync Queue</p>
            <h2>Progress items</h2>
          </div>
          {mobileSyncQueueItems.map((item) => (
            <article className="sync-queue-card" key={`${item.label}-${item.task}`}>
              <div>
                <span>{item.label}</span>
                <strong>{item.task}</strong>
                <p>{item.detail}</p>
              </div>
              <MobileChip label={item.state} />
            </article>
          ))}
        </section>

        <section className="action-band" aria-label="Field actions">
          <button type="button">
            <PrimaryActionIcon size={18} aria-hidden="true" />
            <span>Refresh</span>
          </button>
          <button type="button">
            <EvidenceActionIcon size={18} aria-hidden="true" />
            <span>Draft note</span>
          </button>
        </section>
      </main>

      <nav className="bottom-nav" aria-label="Mobile navigation">
        {mobileNavItems.map((item) => (
          <button
            type="button"
            className={item.active ? "active" : undefined}
            key={item.label}
            aria-current={item.active ? "page" : undefined}
          >
            <item.icon size={19} aria-hidden="true" />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}

function MobileChip({ label }: { label: string }) {
  return <span className={`mobile-chip ${mobileChipTone(label)}`}>{label}</span>;
}

function mobileChipTone(label: string) {
  const value = label.toLowerCase();

  if (
    value.includes("blocked") ||
    value.includes("failed") ||
    value.includes("could not") ||
    value.includes("missing") ||
    value.includes("conflict")
  ) {
    return "red";
  }

  if (
    value.includes("queued") ||
    value.includes("awaiting") ||
    value.includes("needs") ||
    value.includes("draft") ||
    value.includes("out of date")
  ) {
    return "amber";
  }

  if (value.includes("server received") || value.includes("accepted") || value.includes("saved locally")) {
    return "green";
  }

  return "blue";
}
