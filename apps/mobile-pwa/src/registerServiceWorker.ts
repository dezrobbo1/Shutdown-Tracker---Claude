/**
 * Registering the field app's service worker.
 *
 * Deployed builds only. The dev server hands modules to the browser one at a time and replaces
 * them in place; a worker sitting in front of that serves a version of the app that no longer
 * exists on disk, and the resulting confusion outlasts the session because the worker survives
 * a reload.
 *
 * Registration is scoped to the app's own base. In deployment that is `/mobile/`, and the
 * restriction matters: the Master Console is served from `/` on the same origin, and a worker
 * with a wider scope would start answering the console's requests with the field app's rules.
 */
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD) {
    return;
  }

  // Absent in browsers without support and on any non-secure origin. Neither is a reason to fail
  // to start: without a worker the app simply needs a connection, which is what it needed before.
  if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) {
    return;
  }

  const base = import.meta.env.BASE_URL;

  // After load, so registration never competes with the first render for the connection a field
  // user may barely have.
  window.addEventListener("load", () => {
    void navigator.serviceWorker.register(`${base}sw.js`, { scope: base }).catch(() => {
      // Nothing to recover here and nothing a field user could act on. The app works online
      // either way, and reporting a failed registration on screen would say only that a thing
      // they did not ask for did not happen.
    });
  });
}
