# Apps

Application workspace.

Implemented scaffolds:

- `console`: React/Vite Master Console shell for desktop-oriented review workflows.
- `mobile-pwa`: React/Vite Mobile Field App shell with a web app manifest.

The console now imports the shared API client for import/export review operation wiring while still rendering synthetic scaffold data by default. The mobile PWA currently uses static synthetic scaffold data only. Neither app stores files, parses Project files, creates execution records, generates export artifacts, or writes back to Microsoft Project.

Run from the repository root after installing npm dependencies:

```text
npm test
npm run build
```
