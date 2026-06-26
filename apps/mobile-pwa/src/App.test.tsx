import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { mobileNavItems, mobileProgressFlow, mobileSyncQueueItems, mobileWorkItems, syncSignals } from "./mobileData";

describe("mobile PWA scaffold", () => {
  it("renders the planned mobile navigation", () => {
    const html = renderToString(<App />);

    for (const item of mobileNavItems) {
      expect(html).toContain(item.label);
    }
  });

  it("renders sync status signals", () => {
    const html = renderToString(<App />);

    for (const signal of syncSignals) {
      expect(html).toContain(signal.label);
      expect(html).toContain(signal.detail);
    }
  });

  it("renders mobile task progress states and sync queue examples", () => {
    const html = renderToString(<App />);

    for (const item of mobileWorkItems) {
      expect(html).toContain(item.title);
      expect(html).toContain(item.state);
      expect(html).toContain(item.reviewBadge);
      expect(html).toContain(item.syncBadge);
    }

    expect(html).toContain("Submit progress for supervisor review.");

    for (const state of mobileProgressFlow.visualStates) {
      expect(html).toContain(state);
    }

    for (const item of mobileSyncQueueItems) {
      expect(html).toContain(item.label);
      expect(html).toContain(item.detail);
      expect(html).toContain(item.state);
    }
  });

  it("does not surface schedule-authoring language", () => {
    const html = renderToString(<App />);
    const forbidden = [
      /critical path/i,
      /\bfloat\b/i,
      /\bCPM\b/i,
      /\bGantt\b/i,
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
