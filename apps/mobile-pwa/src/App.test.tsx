import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { mobileNavItems, syncSignals } from "./mobileData";

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
