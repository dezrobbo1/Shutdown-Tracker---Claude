import {
  evidenceActionIcon as EvidenceActionIcon,
  mobileNavItems,
  mobileWorkItems,
  primaryActionIcon as PrimaryActionIcon,
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
        <section className="sync-strip" aria-label="Sync status">
          {syncSignals.map((signal) => (
            <article className="sync-card" key={signal.label}>
              <span>{signal.label}</span>
              <strong>{signal.detail}</strong>
            </article>
          ))}
        </section>

        <section className="work-list" aria-label="Assigned work">
          {mobileWorkItems.map((item) => (
            <article className="work-card" key={item.title}>
              <div>
                <p>{item.workPackage}</p>
                <h2>{item.title}</h2>
                <span>{item.detail}</span>
              </div>
              <strong className="work-state">{item.state}</strong>
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
