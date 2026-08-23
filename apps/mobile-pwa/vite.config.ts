import react from "@vitejs/plugin-react";
import type { Plugin } from "vite";
import { defineConfig } from "vitest/config";

/**
 * Where the dev server sends /api.
 *
 * The app asks its own origin for the API — see the base URL resolution in src — because in a
 * deployment the two are served together. A dev server is a second origin, and the API declares no
 * CORS, so without this proxy every request from `npm run dev` fails before it reaches a handler.
 * Point it elsewhere with SHUTDOWN_TRACKER_API_ORIGIN when the API is not on this machine.
 */
const apiOrigin = process.env.SHUTDOWN_TRACKER_API_ORIGIN ?? "http://127.0.0.1:8080";


/**
 * Tells the service worker what the shell is.
 *
 * The worker has to cache the shell while it installs, and the filenames carry content hashes
 * that do not exist until the bundle is written. So the list is substituted into the built
 * worker here, where both are known.
 *
 * `index.html` becomes the empty string, because a navigation asks for the base itself —
 * `/mobile/` — and a cache entry for `/mobile/index.html` would never match it. Source maps and
 * the worker itself are left out: the first are megabytes no field device can use, and the
 * second is fetched by the browser rather than served from a cache.
 */
function serviceWorkerShellManifest(): Plugin {
  return {
    name: "shutdown-tracker-service-worker-shell",
    apply: "build",
    enforce: "post",
    generateBundle(_options, bundle) {
      const worker = bundle["sw.js"];
      if (!worker || worker.type !== "chunk") {
        throw new Error("sw.js was not emitted; the service worker entry is missing from the build.");
      }
      const shell = Object.keys(bundle)
        .filter((name) => name !== "sw.js" && !name.endsWith(".map"))
        .map((name) => (name === "index.html" ? "" : name))
        .sort();
      if (!worker.code.includes("SHELL_ASSETS")) {
        throw new Error("SHELL_ASSETS placeholder not found in the built service worker.");
      }
      worker.code = worker.code.replace(/\bSHELL_ASSETS\b/g, JSON.stringify(shell));
    }
  };
}

export default defineConfig({
  plugins: [react(), serviceWorkerShellManifest()],
  server: {
    // Fixed and strict, because the console and the field app are meant to run at the same time.
    // Vite's default is to pick the next free port, which would silently put whichever started
    // second on the other's port and make the two indistinguishable in a browser tab.
    port: 5174,
    strictPort: true,
    proxy: {
      "/api": { target: apiOrigin, changeOrigin: true },
      "/actuator": { target: apiOrigin, changeOrigin: true }
    }
  },
  build: {
    sourcemap: true,
    rollupOptions: {
      // The service worker is a second entry rather than a hand-written file in public/, so it
      // can import the caching policy that the tests assert. Its output name is fixed and
      // unhashed because a worker is fetched by URL: a hashed name would register a new worker
      // on every build and leave the old one controlling the page.
      input: {
        index: "index.html",
        sw: "src/sw.ts"
      },
      output: {
        entryFileNames: (chunk) => (chunk.name === "sw" ? "sw.js" : "assets/[name]-[hash].js")
      }
    }
  },
  test: {
    environment: "node",
    globals: true
  }
});
