# ADR-003: Frontend and Mobile

Status: Draft

## Context

The product needs two task-appropriate application experiences:

- a desktop/control-room Master Console;
- a mobile/field Field App.

Users also need browser access and installable application access without creating separate product models or divergent workflow authority.

## Decision

Use the web application architecture as the common delivery foundation for the Master Console and Field App.

The product delivery requirement is:

- **Master Console:** desktop-optimised browser application, with installable desktop delivery supported as a channel over the same platform/product model;
- **Field App:** mobile-optimised browser/PWA application, with installable iOS/Android delivery supported as a channel over the same platform/product model.

The same authenticated user, project data, permissions, execution records, review state, and audit model apply regardless of whether the experience is opened in a browser or installed application channel.

The Master Console remains desktop-first even when browser-delivered. It is not required to reproduce the Field App UX on a phone-sized screen. The Field App remains mobile/field-first and is not required to reproduce the complete desktop control-room workspace.

The current React/Vite implementation may remain the shared web foundation. The exact packaging technology for installed desktop or mobile delivery is an implementation decision and may evolve without changing this product boundary.

## Consequences

- Browser access is a product requirement for both application experiences.
- Installable desktop and iOS/Android delivery are supported product channels, not separate applications with separate domain rules.
- Shared platform/API/domain contracts remain authoritative across delivery channels.
- Offline-capable behavior can continue to use browser/PWA foundations while installed channels may add justified device integration.
- Device-specific capabilities may differ for camera/evidence capture, local/offline storage, notifications, background sync, and similar platform integration.
- Installed delivery must not fork permissions, schedule authority, approval/export rules, or execution-state semantics.
- Native-only functionality is not required merely to claim an installable channel; packaging technology should be selected when implementation requirements justify it.
