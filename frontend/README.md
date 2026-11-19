# Sauti frontend

The React + TypeScript app for Sauti HMS, built with Vite.

## Running it

Requires Node 26 (see `.nvmrc`). Start the backend first (`./mvnw spring-boot:run` in `backend/`), then:

```bash
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server forwards every `/api` request (and `/actuator` for the health check) to the backend on port 8080, so the browser sees a single site and the session cookie works without any CORS setup. Set `BACKEND_URL` to point the proxy somewhere else.

## Checks

| Command                | What it does                                                     |
| ---------------------- | ---------------------------------------------------------------- |
| `npm run lint`         | ESLint with strict type-checked rules                            |
| `npm run typecheck`    | TypeScript in strict mode                                        |
| `npm run format:check` | Prettier; `npm run format` fixes it                              |
| `npm test`             | Vitest with Testing Library (`npm run test:watch` while working) |
| `npm run build`        | Production build into `dist/`                                    |

CI runs all of them on every pull request.

## Layout

| Folder         | Contents                                                |
| -------------- | ------------------------------------------------------- |
| `src/app`      | Routes and the query client                             |
| `src/api`      | The fetch wrapper and the backend's `ApiError` type     |
| `src/features` | One folder per feature (components, hooks, their tests) |
| `src/pages`    | Top-level pages used by the routes                      |
| `src/test`     | Test setup and render helpers                           |

Dependencies are pinned to exact versions, chosen at least two weeks after release.
