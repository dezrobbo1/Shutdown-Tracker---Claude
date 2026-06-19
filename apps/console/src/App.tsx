import { ChevronDown, FileSearch, RefreshCw } from "lucide-react";
import { reviewApiConnection } from "./apiReviewClient";
import {
  consoleMetrics,
  consoleNavItems,
  exportPreviewRows,
  exportPreviewSignals,
  reviewRows
} from "./consoleData";

export function App() {
  return (
    <div className="console-shell">
      <aside className="sidebar" aria-label="Console navigation">
        <div className="brand">
          <span className="brand-mark">ST</span>
          <div>
            <strong>Shutdown Tracker</strong>
            <span>Master Console</span>
          </div>
        </div>
        <nav className="nav-list">
          {consoleNavItems.map((item) => (
            <button
              className={item.active ? "nav-item active" : "nav-item"}
              type="button"
              key={item.label}
              aria-current={item.active ? "page" : undefined}
            >
              <item.icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">Review scaffold</p>
            <h1>Execution review console</h1>
          </div>
          <div className="header-actions" aria-label="Console actions">
            <button type="button" className="icon-button" aria-label="Refresh review data">
              <RefreshCw size={18} aria-hidden="true" />
            </button>
            <button type="button" className="filter-button">
              <FileSearch size={18} aria-hidden="true" />
              <span>Review view</span>
              <ChevronDown size={16} aria-hidden="true" />
            </button>
          </div>
        </header>

        <section className="metric-grid" aria-label="Review status">
          {consoleMetrics.map((metric) => (
            <article className={`metric-card ${metric.tone}`} key={metric.label}>
              <div className="metric-icon" aria-hidden="true">
                <metric.icon size={20} />
              </div>
              <div>
                <p>{metric.label}</p>
                <strong>{metric.value}</strong>
                <span>{metric.detail}</span>
              </div>
            </article>
          ))}
        </section>

        <section className="api-surface" aria-label="Review API client wiring">
          <div>
            <p className="eyebrow">API client</p>
            <h2>Import/export review operations</h2>
            <span>{reviewApiConnection.baseUrlLabel}</span>
          </div>
          <div className="api-surface-list">
            {reviewApiConnection.highlightedSurfaces.map((surface) => (
              <span key={`${surface.method}-${surface.path}`}>
                <strong>{surface.method}</strong>
                {surface.label}
              </span>
            ))}
            <span>
              <strong>{reviewApiConnection.operationCount}</strong>
              Wired operations
            </span>
          </div>
        </section>

        <section className="content-grid">
          <article className="work-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Import review</p>
                <h2>Snapshot workbench</h2>
              </div>
              <span className="status-chip">Local profile</span>
            </div>
            <div className="review-table" role="table" aria-label="Snapshot review rows">
              <div className="review-row review-head" role="row">
                <span role="columnheader">Item</span>
                <span role="columnheader">Source</span>
                <span role="columnheader">State</span>
                <span role="columnheader">Owner</span>
              </div>
              {reviewRows.map((row) => (
                <div className="review-row" role="row" key={row.item}>
                  <span role="cell">{row.item}</span>
                  <span role="cell">{row.source}</span>
                  <span role="cell">{row.state}</span>
                  <span role="cell">{row.owner}</span>
                </div>
              ))}
            </div>
          </article>

          <article className="work-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Export preview</p>
                <h2>Eligible update candidates</h2>
              </div>
              <span className="status-chip amber">Draft</span>
            </div>
            <div className="preview-list">
              {exportPreviewRows.map((row) => (
                <div className="preview-line" key={`${row.field}-${row.candidate}`}>
                  <span>{row.field}</span>
                  <strong>{row.candidate}</strong>
                  <em>{row.eligibility}</em>
                </div>
              ))}
            </div>
            <div className="preview-signals" aria-label="Export preview status">
              {exportPreviewSignals.map((signal) => (
                <div key={signal.label}>
                  <span>{signal.label}</span>
                  <strong>{signal.value}</strong>
                </div>
              ))}
            </div>
          </article>
        </section>
      </main>
    </div>
  );
}
