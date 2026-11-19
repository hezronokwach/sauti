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
- 2026-11-19: Errors use one `ApiError` shape built only in `BaseExceptionHandler`; every handler extends it, feature code throws `ApiException` subclasses, and a feature adds its own handler only for a genuinely new exception type.
- 2026-11-19: Client errors log one WARN line without a stack trace; server errors log at ERROR with the stack trace; exception messages from libraries never reach the response.
- 2026-11-19: An incoming `X-Request-Id` is reused only if it is a canonical UUID; anything else is replaced so outside text never reaches the logs.
- 2026-11-19: Entity ids are version 7 UUIDs (time-ordered, index-friendly); timestamps come from the injected `Clock` through JPA auditing.
- 2026-11-19: Java quality gates run inside `./mvnw verify`: Spotless with palantir-java-format, compiler warnings as errors (`-Xlint:all` minus serial and processing), Error Prone, and a JaCoCo coverage report with no threshold yet.
- 2026-11-19: Feature packages are the first package below `ke.sauti.hms` (common, facility, security, staff, patient, visit, capture, note, ai, medication, billing, audit, jobs, integration). ArchUnit enforces that controllers never use repositories and that a feature uses only its own repositories; other features' data goes through their services.
- 2026-11-19: Frontend is Vite + React 19 + TypeScript (strict) with React Router, TanStack Query, React Hook Form and Zod; TypeScript stays on 6.0 until typescript-eslint supports 7. Dependencies are exact pins at least two weeks old, installed with `npm install --before`; minimatch is overridden to 11.0.4 for a moderate advisory in lint tooling.
- 2026-11-19: The dev proxy forwards `/api` and, in development only, `/actuator` (for the health check on the home page) to the backend, so the browser sees one site.
- 2026-11-19: Security scanning: gitleaks on every PR and push to staging (full history), CodeQL security-extended for Java and TypeScript (no build needed), Dependabot weekly for Maven, npm, Actions and compose images with a 14-day cooldown and grouped minor/patch updates, plus Dependabot security updates (not delayed). GitHub secret scanning and push protection stay on.
