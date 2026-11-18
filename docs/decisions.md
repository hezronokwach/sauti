# Decisions

One line per decision and why. Newest at the bottom.

- 2026-11-18: Repository split into `backend/`, `frontend/` and `eval/` so each part builds and deploys on its own.
- 2026-11-18: Backend package is `ke.sauti.hms`, organised by feature (common, security, staff, patient, visit, capture, note, ai, medication, billing, audit, jobs, integration).
- 2026-11-18: Login uses server sessions stored in PostgreSQL (Spring Session JDBC), not tokens, so logout and account disabling take effect at once; the OAuth2 resource server starter was removed.
- 2026-11-18: Secrets come only from environment variables; `.env.example` lists the names, never values.
