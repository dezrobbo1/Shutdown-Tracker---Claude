# Services

Backend services workspace.

- [API](api) contains a Spring Boot API shell with local-profile import/export review plumbing and worker handoff boundaries.
- [Project Worker](project-worker) contains a Spring Boot worker shell with local-only MPXJ import summary and MSPDI/XML export artifact handoff spikes.

Both services include `local` profile PostgreSQL/Flyway runtime wiring. No task execution domain logic, scheduler logic, Microsoft Project write-back, or frontend code exists here.
