import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { reviewApiConnection } from "./apiReviewClient";
import { consoleNavItems, exportPreviewSignals } from "./consoleData";

describe("console scaffold", () => {
  it("renders the planned console navigation", () => {
    const html = renderToString(<App />);

    for (const item of consoleNavItems) {
      expect(html).toContain(item.label);
    }
  });

  it("renders export preview status signals", () => {
    const html = renderToString(<App />);

    for (const signal of exportPreviewSignals) {
      expect(html).toContain(signal.label);
      expect(html).toContain(signal.value);
    }
  });

  it("renders the wired import and export review API client operations", () => {
    const html = renderToString(<App />);

    expect(reviewApiConnection.operationCount).toBeGreaterThanOrEqual(10);
    expect(html).toContain("Import/export review operations");
    expect(html).toContain("List import snapshots");
    expect(html).toContain("Create export preview");
    expect(html).toContain("Wired operations");
  });

  it("does not surface schedule-authoring language", () => {
    const html = renderToString(<App />);
    const forbidden = [
      /critical path/i,
      /\bfloat\b/i,
      /resource levell?ing/i,
      /recovery scheduling/i,
      /automatic date movement/i,
      /schedule optimization/i
    ];

    for (const pattern of forbidden) {
      expect(html).not.toMatch(pattern);
    }
  });
});
