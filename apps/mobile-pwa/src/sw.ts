import {
  shellCacheName,
  strategyFor,
  supersededAssetPaths,
  supersededCacheNames
} from "./serviceWorkerPolicy";

/**
 * The field app's service worker.
 *
 * Its whole job is that the app opens with no signal. What it does *not* do is described in
 * `serviceWorkerPolicy.ts`, which owns every decision about what may be cached; this file only
 * carries them out.
 *
 * Built as its own Vite entry so it can share that policy module with the tests that assert it.
 * A worker written as a standalone file in `public/` cannot import from `src`, which would have
 * meant keeping the rules in two places and hoping they stayed the same.
 */

// The DOM lib gives us caches, fetch, Request and Response. The worker globals are declared here
// rather than by adding "WebWorker" to tsconfig lib, which collides with DOM on shared names.
type ExtendableEventLike = { waitUntil: (promise: Promise<unknown>) => void };
type FetchEventLike = ExtendableEventLike & {
  request: Request;
  respondWith: (response: Response | Promise<Response>) => void;
};

declare const self: {
  addEventListener: {
    (type: "install" | "activate", listener: (event: ExtendableEventLike) => void): void;
    (type: "fetch", listener: (event: FetchEventLike) => void): void;
  };
  skipWaiting: () => Promise<void>;
  clients: { claim: () => Promise<void> };
  location: { origin: string; pathname: string };
  registration: { scope: string };
};

/**
 * The shell filenames, relative to the base, replaced at build time with what the build emitted.
 *
 * Declared rather than defined because the hashes are not known until after bundling. The Vite
 * plugin that substitutes it fails the build if it cannot find this name, so an empty precache
 * cannot ship quietly.
 */
declare const SHELL_ASSETS: readonly string[];

/** The scope path the worker was registered at — `/mobile/` in deployment, `/` in development. */
const base = new URL(self.registration.scope).pathname;

self.addEventListener("install", (event) => {
  // The shell is fetched here rather than left to accumulate as the app is used, and that is the
  // difference between working in the field and not. A worker only takes control after the load
  // that registered it has already fetched everything, so a worker that cached opportunistically
  // would leave the app needing a *second* online load before it survived offline — and the
  // second one happens in the plant, where there is no signal. Caching at install means one trip
  // through the gate is enough.
  event.waitUntil(
    (async () => {
      const cache = await caches.open(shellCacheName);
      await cache.addAll(SHELL_ASSETS.map((asset) => base + asset));
      await self.skipWaiting();
    })()
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      // Drop what a previous version of these rules left behind — and only ours. The console is
      // served from `/` on this same origin, so a sweep of every name that is not this one would
      // delete another application's storage from inside the field app's activation.
      const names = await caches.keys();
      await Promise.all(supersededCacheNames(names).map((name) => caches.delete(name)));
      // And, inside the cache these rules own, the bundles this release replaced. Install has
      // already added the new ones, so what is superseded is known exactly here.
      await dropSupersededAssets();
      await self.clients.claim();
    })()
  );
});

self.addEventListener("fetch", (event) => {
  const strategy = strategyFor(
    { method: event.request.method, mode: event.request.mode, url: event.request.url },
    self.location.origin,
    base
  );

  if (strategy === "pass-through") {
    return;
  }

  event.respondWith(
    strategy === "network-first" ? networkFirst(event.request) : cacheFirst(event.request)
  );
});

/**
 * The document request.
 *
 * Network first, so a field user who has signal is never served an app older than the one on the
 * server. The cached copy is the fallback, and the only reason this worker exists.
 */
async function networkFirst(request: Request): Promise<Response> {
  try {
    const response = await fetch(request);
    if (response.ok) {
      await put(request, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }
    // No cached shell means the app has never opened on this device. Say so, rather than
    // returning the browser's offline page, which reads as though the app is broken.
    if (request.mode === "navigate") {
      return offlineShellMissing();
    }
    throw error;
  }
}

/**
 * A hashed asset.
 *
 * Cache first is safe only because the filename changes whenever the bytes do; a stale hit is
 * therefore not possible. A miss is fetched and kept.
 */
async function cacheFirst(request: Request): Promise<Response> {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (response.ok) {
    await put(request, response.clone());
  }
  return response;
}

/**
 * Stores a response, and never lets a failed store become a failed request.
 *
 * The Cache API rejects when storage is unavailable, restricted, or over quota, and both callers
 * await it *after* the network has already answered. Left to throw, that rejection would be
 * caught by `networkFirst` as though the request had failed — serving a stale document, or the
 * "no offline copy" notice, to somebody who has a connection — and would reject outright in
 * `cacheFirst`, breaking an asset load on a perfectly online page. Caching here is an
 * optimisation for the next load; it is not part of answering this one.
 */
async function put(request: Request, response: Response): Promise<void> {
  try {
    const cache = await caches.open(shellCacheName);
    await cache.put(request, response);
  } catch {
    // Nothing to recover, and nothing a field user could act on: the response they asked for is
    // already on its way to the page. The next load simply fetches it again.
  }
}

/**
 * Removes the content-hashed bundles that earlier releases left in this cache.
 *
 * Which entries those are is decided in `serviceWorkerPolicy.ts` with the rest of the caching
 * rules, and asserted there.
 */
async function dropSupersededAssets(): Promise<void> {
  try {
    const cache = await caches.open(shellCacheName);
    const stored = await cache.keys();
    const superseded = new Set(
      supersededAssetPaths(
        stored.map((request) => new URL(request.url).pathname),
        SHELL_ASSETS.map((asset) => base + asset),
        base
      )
    );
    await Promise.all(
      stored
        .filter((request) => superseded.has(new URL(request.url).pathname))
        .map((request) => cache.delete(request))
    );
  } catch {
    // A cache that cannot be read or written is not a reason to fail activation. The worker still
    // serves what it has, which is the behaviour this whole file exists for.
  }
}

function offlineShellMissing(): Response {
  return new Response(
    "<!doctype html><meta charset=utf-8><title>Shutdown Tracker Mobile</title>" +
      "<p>This device has no offline copy of the field app yet. Open it once with a connection.</p>",
    { status: 503, headers: { "Content-Type": "text/html; charset=utf-8" } }
  );
}
