import react from "@vitejs/plugin-react";
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

export default defineConfig({
  plugins: [react()],
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
    sourcemap: true
  },
  test: {
    environment: "node",
    globals: true
  }
});
