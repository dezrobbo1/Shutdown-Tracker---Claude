import { describe, expect, it } from "vitest";
import { isServerPath, strategyFor, supersededAssetPaths } from "./serviceWorkerPolicy";
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


/**
 * What a release leaves behind.
 *
 * The bundles are the storage: a shell is a few hundred kilobytes, and nothing was removing the
 * previous one. The risk in fixing that is deleting too much — the cache also holds the document
 * and the unhashed files copied from `public/`, none of which appear in a build's shell manifest
 * and all of which are still current.
 */
describe("superseded bundles are dropped, and nothing else is", () => {
  const base = "/mobile/";
  const shell = ["/mobile/", "/mobile/assets/index-NEW11111.js", "/mobile/assets/index-NEW22222.css"];

  it("drops the bundles a previous release cached", () => {
    const stored = [
      "/mobile/",
      "/mobile/assets/index-OLD11111.js",
      "/mobile/assets/index-OLD22222.css",
      "/mobile/assets/index-NEW11111.js",
      "/mobile/assets/index-NEW22222.css"
    ];

    expect(supersededAssetPaths(stored, shell, base)).toEqual([
      "/mobile/assets/index-OLD11111.js",
      "/mobile/assets/index-OLD22222.css"
    ]);
  });

  /**
   * The document is the entry the offline start depends on, and it is not in the manifest under
   * its own URL — the build lists it as the empty string, which becomes the base. Deleting it
   * would defeat the whole worker on the next release.
   */
  it("never drops the document", () => {
    expect(supersededAssetPaths(["/mobile/"], shell, base)).toEqual([]);
    expect(supersededAssetPaths(["/mobile/"], ["/mobile/assets/index-NEW11111.js"], base)).toEqual([]);
  });

  /**
   * `pwa.svg` and the manifest are copied verbatim by Vite, carry no content hash, and are cached
   * at runtime rather than at install — so they are absent from every shell manifest. A rule that
   * kept only what the manifest lists would delete them on every single release.
   */
  it("never drops the unhashed files copied from public/", () => {
    const stored = ["/mobile/pwa.svg", "/mobile/manifest.webmanifest"];

    expect(supersededAssetPaths(stored, shell, base)).toEqual([]);
  });

  it("leaves another app's assets on this origin alone", () => {
    // The console's bundles sit at /assets/ and are not this worker's to delete. Its scope should
    // never have put them here, and the rule does not rely on that.
    expect(supersededAssetPaths(["/assets/index-CONSOLE1.js"], shell, base)).toEqual([]);
  });

  it("holds at the development base too, where every path sits under it", () => {
    const devShell = ["/", "/assets/index-NEW11111.js"];
    const stored = ["/", "/assets/index-OLD11111.js", "/assets/index-NEW11111.js", "/pwa.svg"];

    expect(supersededAssetPaths(stored, devShell, "/")).toEqual(["/assets/index-OLD11111.js"]);
  });

  it("drops nothing when the release is the one already cached", () => {
    expect(supersededAssetPaths(shell, shell, base)).toEqual([]);
  });
});
