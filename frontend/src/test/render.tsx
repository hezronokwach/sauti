import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { createMemoryRouter } from 'react-router';
import { vi } from 'vitest';
import { RouterProvider } from 'react-router/dom';
import { routes } from '../app/routes';
import { wireApiEvents } from '../app/wireApiEvents';

/** A fresh query client per test: no retries, no shared cache. */
function testQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

export function renderWithQuery(ui: ReactNode) {
  return render(<QueryClientProvider client={testQueryClient()}>{ui}</QueryClientProvider>);
}

/** Renders the real routes at the given path, wired to the API client as in the app. */
export function renderRoute(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = testQueryClient();
  wireApiEvents(queryClient, router);
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { ...view, router, queryClient };
}

/** A JSON response, as fetch would return it. */
export function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

/** Answers one mocked API call. */
export type MockRoute = (init: RequestInit | undefined) => Response | Promise<Response>;

/**
 * Replaces fetch with a small fake backend: each key is "METHOD /path". Calls with no
 * matching key get a 404 ApiError, so a missing mock shows up as a clear failure.
 */
export function mockApi(routes: Record<string, MockRoute>) {
  const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
    const method = (init?.method ?? 'GET').toUpperCase();
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const path = new URL(url, 'http://localhost').pathname;
    const route = routes[`${method} ${path}`];
    if (!route) {
      return jsonResponse(404, {
        code: 'NOT_FOUND',
        message: `No mock for ${method} ${path}`,
        fieldErrors: [],
        requestId: null,
      });
    }
    return route(init);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** The body of a mocked call, parsed. */
export function bodyOf(init: RequestInit | undefined): unknown {
  return typeof init?.body === 'string' ? JSON.parse(init.body) : undefined;
}

/** An ApiError body, as the backend sends it. */
export function apiError(code: string, message: string) {
  return { code, message, fieldErrors: [], requestId: '0f8fad5b-d9cb-469f-a165-70867728950e' };
}
