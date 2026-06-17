# Concept and Architecture Pack v1.3 Summary

Shutdown Tracker is a live execution tracking platform for shutdown, turnaround, and construction work. It uses Microsoft Project schedule snapshots as source material while keeping Microsoft Project as the schedule authority.

## Master Console

The Master Console supports shutdown control, planners, coordinators, supervisors, package owners, and managers. It focuses on live execution tracking, operational reporting, problem/action follow-up, evidence review, handover visibility, and controlled export approval.

Baseline zones:

- Today
- Tasks
- Problems
- Evidence
- Exports

## Mobile Field App

The Mobile Field App is a mobile-first PWA for field supervisors, leading hands, contractors, inspectors, and execution crews. It supports assigned work, task updates, work state changes, problems, actions, evidence capture, and handover notes.

Baseline zones:

- My Work
- Today
- Problems
- Evidence
- Sync

Offline-capable field workflows are a core direction, but the initial scaffold does not implement offline logic.

## Microsoft Project Boundary

Microsoft Project remains the schedule authority. Shutdown Tracker imports schedule snapshots and does not live-feed changes back into Microsoft Project. Approved progress and actuals may be exported in controlled batches using MSPDI/XML.

The product must not perform CPM, critical-path calculation, resource levelling, recovery scheduling, automatic date movement, or hidden schedule recalculation.

## Critical Work Package Reporting

A Critical Watchlist is a named operational reporting list for one shutdown, area, or purpose. A Critical Work Package is a reporting object, not a scheduling object.

The default Critical Work Package source is a selected Microsoft Project summary task plus all descendants. Future extensions may support multi-summary grouping and manual grouping.

Reporting policies must be configurable and generic. Supported policy types should include none, ad hoc, fixed interval, fixed times, shift-based, event-triggered, and custom. Four-hour reporting is one template, not a hardcoded system rule.

## MVP Scope

The MVP should focus on project import/export, imported tasks and assignments, task execution state, problems, actions, evidence, handover, Critical Watchlists, configurable reporting policies, approval/export batches, audit events, users, roles, permissions, and offline sync foundations.

Excluded from the MVP: Gantt view, CPM view, dependency-map UI, scheduler/recovery engine, resource levelling, AI prediction, custom dashboard builder, native mobile app, live Microsoft Project integration, and chat clone behavior.
