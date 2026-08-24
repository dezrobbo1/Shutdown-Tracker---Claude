/**
 * What the service worker is allowed to cache.
 *
 * The field app has to open inside a vessel with no signal. That needs the shell — the HTML and
 * the hashed bundle it loads — to survive without a connection. It must not extend one step
 * further than that.
 *
 * The rule this file exists to enforce is that **the service worker never caches an API
 * response**. A cached `/api` read would put yesterday's task list, yesterday's problems and
 * yesterday's review state on screen with nothing marking them stale, and the product's central
 * offline rule is that sync state is visible and never inferred. A shell that loads and then
 * says it cannot reach the server is honest. A shell that loads and shows old execution data as
 * though it were current is the failure this whole layer is meant to prevent.
 *
 * The decisions live here rather than in the worker so they can be tested. A caching mistake in
 * a service worker is close to invisible in review and persists on a device across reloads.
 */

export type FetchStrategy =
  /** The worker does not handle it at all. The request goes to the network as if no worker existed. */
  | "pass-through"
  /**
   * Try the network, fall back to the cached copy, and store what the network returns.
   * For documents: online you get the current shell, offline you get the last one that loaded.
   */
  | "network-first"
  /**
   * Serve the cached copy if there is one, otherwise fetch and store it.
   * Only for content-hashed assets, whose URL changes whenever their bytes do.
   */
  | "cache-first";

export type RequestFacts = {
  method: string;
  /** `navigate` for a document request — the app being opened, rather than something it loads. */
  mode: string;
  url: string;
};

/** Paths owned by the server. Never cached, at any base, under any strategy. */
const serverPathPrefixes = ["/api/", "/actuator/"] as const;

export function isServerPath(pathname: string): boolean {
  return serverPathPrefixes.some((prefix) => pathname === prefix.slice(0, -1) || pathname.startsWith(prefix));
}

/**
 * Which strategy applies to one request.
 *
 * `origin` and `base` are the worker's own, so the same rules hold whether the app is served at
 * the site root in development or under `/mobile/` in deployment.
 */
export function strategyFor(request: RequestFacts, origin: string, base: string): FetchStrategy {
  // A queued report is a POST. Nothing about a write belongs in a cache, and replaying one from
  // a cache would double-report the progress the idempotency key exists to protect.
  if (request.method !== "GET") {
    return "pass-through";
  }

  let url: URL;
  try {
    url = new URL(request.url);
  } catch {
    return "pass-through";
  }

  if (url.origin !== origin) {
    return "pass-through";
  }

  // Checked before the base test, and that order is the point. In development the base is `/`,
  // so every same-origin path — `/api/tasks` included — sits "under" it. Without this the app
  // would cache API reads on a developer's machine and nowhere else, which is the worst place
  // for a caching bug to live.
  if (isServerPath(url.pathname)) {
    return "pass-through";
  }

  // Anything outside the app's own base belongs to something else on this origin — in
  // deployment that is the Master Console at `/`. The worker is registered with a scope that
  // should never deliver those requests here, but the boundary is stated rather than assumed:
  // scope is configured at registration and this is the file that is tested.
  if (!url.pathname.startsWith(base)) {
    return "pass-through";
  }

  if (request.mode === "navigate") {
    return "network-first";
  }

  // Source maps are megabytes and are read by devtools, never by the app. Caching them would
  // spend a field device's storage budget on something no field user can use.
  if (url.pathname.endsWith(".map")) {
    return "pass-through";
  }

  return "cache-first";
}

/**
 * The cached asset entries a new release has superseded.
 *
 * Every bundle filename carries a content hash, so a new release asks for URLs the cache has
 * never seen and the previous ones are never read again. That makes them harmless but not free:
 * nothing removed them, and the cache name only changes when these *rules* change, not when the
 * app does. A device that takes twenty releases would hold twenty bundles, and the quota eviction
 * that eventually follows takes the whole origin's storage with it — including the shell this
 * worker exists to keep. So each activation drops what the release it is activating replaced.
 *
 * Only `assets/` is considered. Two other things live in this cache and are current at every
 * release: the document itself, and the files Vite copies verbatim from `public/` — `pwa.svg`
 * and the manifest — which carry no hash, are not in the build's shell manifest, and would be
 * deleted on every release by a rule that simply kept the manifest.
 */
export function supersededAssetPaths(
  storedPaths: readonly string[],
  shellPaths: readonly string[],
  base: string
): string[] {
  const current = new Set(shellPaths);
  const assetPrefix = `${base}assets/`;
  return storedPaths.filter((path) => path.startsWith(assetPrefix) && !current.has(path));
}
