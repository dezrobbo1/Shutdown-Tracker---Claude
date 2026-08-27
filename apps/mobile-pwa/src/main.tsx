import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { discardLegacyFieldIdentity } from "./fieldSession";
import { registerServiceWorker } from "./registerServiceWorker";
import "./styles.css";

// A device that chose an identity under the old picker is holding a retired account.
discardLegacyFieldIdentity(typeof window === "undefined" ? undefined : window.localStorage);

createRoot(document.getElementById("root") as HTMLElement).render(
  <StrictMode>
    <App />
  </StrictMode>
);

// So the app opens again inside a vessel with no signal.
registerServiceWorker();
