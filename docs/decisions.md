# Decisions

One line per decision and why. Newest at the bottom.

- 2026-11-18: Repository split into `backend/`, `frontend/` and `eval/` so each part builds and deploys on its own.
- 2026-11-18: Backend package is `ke.sauti.hms`, organised by feature (common, security, staff, patient, visit, capture, note, ai, medication, billing, audit, jobs, integration).
- 2026-11-18: Login uses server sessions stored in PostgreSQL (Spring Session JDBC), not tokens, so logout and account disabling take effect at once; the OAuth2 resource server starter was removed.
- 2026-11-18: Secrets come only from environment variables; `.env.example` lists the names, never values.
- 2026-11-19: Local object storage uses SeaweedFS (S3-compatible) instead of MinIO, because MinIO stopped publishing container images; the app only speaks the S3 API, so the store can be swapped without code changes.
- 2026-11-19: Profiles: `dev` is the default (compose services, fakes), `test` for automated tests, `demo` for real services. `sauti.required-env` lists the variables a profile needs, and startup fails naming every missing one.
- 2026-11-19: Container images are pinned to exact tags (PostgreSQL 18.7) in both compose and Testcontainers, so dev, tests and CI run the same database version.
- 2026-11-19: Facility settings live in their own `facility` package; the table is limited to one row with a unique index on a constant, and V1 seeds a demonstration facility.
- 2026-11-19: An incoming `X-Request-Id` is reused only if it is a canonical UUID; anything else is replaced so outside text never reaches the logs.
