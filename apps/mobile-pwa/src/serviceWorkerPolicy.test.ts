import { describe, expect, it } from "vitest";
import { isServerPath, strategyFor } from "./serviceWorkerPolicy";
import type { RequestFacts } from "./serviceWorkerPolicy";

const origin = "https://dez.tsenior.uk";

function get(url: string, mode = "cors"): RequestFacts {
  return { method: "GET", mode, url };
}

function navigation(url: string): RequestFacts {
  return { method: "GET", mode: "navigate", url };
}

/**
 * The deployed shape: the field app under /mobile/, the API at the origin root, the console at /.
 */
describe("deployed under /mobile/", () => {
  const base = "/mobile/";

  it("serves the app shell network first, so a connected user never gets a stale app", () => {
    expect(strategyFor(navigation(`${origin}/mobile/`), origin, base)).toBe("network-first");
  });

  it("serves hashed assets cache first, because their name changes when their bytes do", () => {
    expect(strategyFor(get(`${origin}/mobile/assets/index-Bx3w97wr.js`), origin, base)).toBe("cache-first");
    expect(strategyFor(get(`${origin}/mobile/assets/index-VJOn1hSj.css`), origin, base)).toBe("cache-first");
    expect(strategyFor(get(`${origin}/mobile/pwa.svg`), origin, base)).toBe("cache-first");
  });

  it("never caches an API read", () => {
    for (const path of ["/api/tasks", "/api/projects/p1/problems", "/api/review-identities"]) {
      expect(strategyFor(get(origin + path), origin, base)).toBe("pass-through");
    }
  });

  it("never caches actuator", () => {
    expect(strategyFor(get(`${origin}/actuator/health`), origin, base)).toBe("pass-through");
  });

  it("leaves the console alone, so the field app's rules cannot answer for it", () => {
    expect(strategyFor(navigation(`${origin}/`), origin, base)).toBe("pass-through");
    expect(strategyFor(get(`${origin}/assets/index--xnhdI5V.js`), origin, base)).toBe("pass-through");
  });
});

/**
 * Development serves the app at the root, which puts /api "under" the base. These are the cases
 * that would have made a caching mistake visible only on a developer's machine.
 */
describe("served at the root in development", () => {
  const base = "/";

  it("still never caches an API read, even though /api sits under the base", () => {
    expect(strategyFor(get(`${origin}/api/tasks`), origin, base)).toBe("pass-through");
    expect(strategyFor(get(`${origin}/actuator/health`), origin, base)).toBe("pass-through");
  });

  it("still caches the shell and its assets", () => {
    expect(strategyFor(navigation(`${origin}/`), origin, base)).toBe("network-first");
    expect(strategyFor(get(`${origin}/assets/index-abc123.js`), origin, base)).toBe("cache-first");
  });
});

describe("what is never cached, at any base", () => {
  const base = "/mobile/";

  it("passes through every write, so no queued report can be replayed from a cache", () => {
    for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
      expect(strategyFor({ method, mode: "cors", url: `${origin}/mobile/assets/index.js` }, origin, base)).toBe(
        "pass-through"
      );
      expect(strategyFor({ method, mode: "cors", url: `${origin}/api/task-progress` }, origin, base)).toBe(
        "pass-through"
      );
    }
  });

  it("passes through source maps, which are megabytes a field device cannot use", () => {
    expect(strategyFor(get(`${origin}/mobile/assets/index-Bx3w97wr.js.map`), origin, base)).toBe("pass-through");
  });

  it("passes through another origin", () => {
    expect(strategyFor(get("https://example.invalid/mobile/assets/index.js"), origin, base)).toBe("pass-through");
  });

  it("passes through a URL it cannot parse rather than guessing", () => {
    expect(strategyFor(get("not-a-url"), origin, base)).toBe("pass-through");
  });
});

describe("isServerPath", () => {
  it("matches the server's paths and their exact roots", () => {
    expect(isServerPath("/api/tasks")).toBe(true);
    expect(isServerPath("/api")).toBe(true);
    expect(isServerPath("/actuator/health")).toBe(true);
    expect(isServerPath("/actuator")).toBe(true);
  });

  it("does not match a path that merely starts with the same letters", () => {
    expect(isServerPath("/apiary")).toBe(false);
    expect(isServerPath("/mobile/api-client.js")).toBe(false);
  });
});
