import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { discardLegacyStoredIdentity } from "./session";
import "./styles.css";

// A browser that chose an identity under the old selector is holding a retired account.
discardLegacyStoredIdentity(typeof window === "undefined" ? undefined : window.localStorage);

createRoot(document.getElementById("root") as HTMLElement).render(
  <StrictMode>
    <App />
  </StrictMode>
);
