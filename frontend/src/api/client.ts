import type { ApiError, ApiFieldError } from './types';

const REQUEST_ID_HEADER = 'X-Request-Id';

/** Spring Security's single-page-app CSRF setting: a readable cookie, echoed in a header. */
export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Any response sets the CSRF cookie; this one is cheap and always allowed. */
const TOKEN_SOURCE = '/api/auth/me';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** A 401 from these means "wrong details", not "your session ended". */
const LOGIN_STEPS = new Set(['/api/auth/login', '/api/auth/code']);

/** A failed API call, carrying the backend's error code and the request id to quote in support. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];
  readonly requestId: string | null;
  /** The whole response body, for errors that carry more than the standard fields. */
  readonly body: unknown;

  constructor(status: number, error: ApiError, body: unknown = error) {
    super(error.message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = error.code;
    this.fieldErrors = error.fieldErrors;
    this.requestId = error.requestId;
    this.body = body;
  }
}

/** What the rest of the app does when a call shows the session has changed. */
export interface ApiEventHandlers {
  /** The session is gone (logged out elsewhere, expired, account disabled). */
  onUnauthorized: () => void;
  /** The person still has a one-time password and must change it first. */
  onPasswordChangeRequired: () => void;
}

const noHandlers: ApiEventHandlers = {
  onUnauthorized: () => undefined,
  onPasswordChangeRequired: () => undefined,
};

let handlers: ApiEventHandlers = noHandlers;

/** Connects the client to the session cache and the router (see app/wireApiEvents). */
export function setApiEventHandlers(next: ApiEventHandlers): void {
  handlers = next;
}

/** Reads a cookie the page is allowed to see. */
export function readCookie(name: string): string | null {
  for (const part of document.cookie.split(';')) {
    const [key, ...value] = part.trim().split('=');
    if (key === name) {
      return decodeURIComponent(value.join('='));
    }
  }
  return null;
}

/**
 * Calls the backend and returns the parsed JSON body. Requests go to the same site
 * (through the dev proxy or the hosting rewrite) and always carry the session cookie.
 * Changing requests carry the CSRF token; if the backend refuses the token, the call
 * gets a fresh one and is tried once more. Any non-2xx response becomes an
 * {@link ApiRequestError}; a 401 or a required password change is also reported to the
 * handlers set with {@link setApiEventHandlers}.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init);
  if (response.status === 403 && isChanging(init)) {
    const error = await toApiRequestError(response.clone());
    if (error.code === 'CSRF_TOKEN_INVALID') {
      await refreshCsrfToken();
      response = await send(path, init);
    }
  }
  if (!response.ok) {
    const error = await toApiRequestError(response);
    if (error.status === 401 && !LOGIN_STEPS.has(path)) {
      handlers.onUnauthorized();
    }
    if (error.code === 'PASSWORD_CHANGE_REQUIRED') {
      handlers.onPasswordChangeRequired();
    }
    throw error;
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** POSTs a JSON body (see {@link apiFetch}). */
export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'POST',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** PUTs a JSON body (see {@link apiFetch}). */
export function apiPut<T>(path: string, body: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

async function send(path: string, init: RequestInit): Promise<Response> {
  // Headers accepts every HeadersInit form (object, array of pairs, Headers).
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (isChanging(init)) {
    const token = readCookie(CSRF_COOKIE) ?? (await refreshCsrfToken());
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }
  return fetch(path, { ...init, credentials: 'include', headers });
}

function isChanging(init: RequestInit): boolean {
  return !SAFE_METHODS.has((init.method ?? 'GET').toUpperCase());
}

/** Asks the backend for a CSRF cookie; whether the call itself succeeds does not matter. */
async function refreshCsrfToken(): Promise<string | null> {
  await fetch(TOKEN_SOURCE, { credentials: 'include', headers: { Accept: 'application/json' } });
  return readCookie(CSRF_COOKIE);
}

/** Builds an error from a failed response, whether or not its body is an ApiError. */
export async function toApiRequestError(response: Response): Promise<ApiRequestError> {
  const headerRequestId = response.headers.get(REQUEST_ID_HEADER);
  try {
    const body: unknown = await response.json();
    if (isApiError(body)) {
      return new ApiRequestError(
        response.status,
        { ...body, requestId: body.requestId ?? headerRequestId },
        body,
      );
    }
  } catch {
    // Not JSON (for example a proxy error page): fall through to a generic error.
  }
  return new ApiRequestError(response.status, {
    code: `HTTP_${String(response.status)}`,
    message: `Request failed with status ${String(response.status)}`,
    fieldErrors: [],
    requestId: headerRequestId,
  });
}

/** The text to show for a failed call: the backend's safe message, or a plain one. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'FORBIDDEN') {
      return "You don't have access to this";
    }
    return error.message;
  }
  return 'Something went wrong; check your connection and try again';
}

function isApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    Array.isArray(candidate.fieldErrors)
  );
}
