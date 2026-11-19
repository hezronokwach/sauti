import type { ApiError, ApiFieldError } from './types';

const REQUEST_ID_HEADER = 'X-Request-Id';

/** A failed API call, carrying the backend's error code and the request id to quote in support. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];
  readonly requestId: string | null;

  constructor(status: number, error: ApiError) {
    super(error.message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = error.code;
    this.fieldErrors = error.fieldErrors;
    this.requestId = error.requestId;
  }
}

/**
 * Calls the backend and returns the parsed JSON body. Requests go to the same site
 * (through the dev proxy or the hosting rewrite), so the session cookie is sent.
 * Any non-2xx response becomes an {@link ApiRequestError}.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  // Headers accepts every HeadersInit form (object, array of pairs, Headers).
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  const response = await fetch(path, { ...init, credentials: 'same-origin', headers });
  if (!response.ok) {
    throw await toApiRequestError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Builds an error from a failed response, whether or not its body is an ApiError. */
export async function toApiRequestError(response: Response): Promise<ApiRequestError> {
  const headerRequestId = response.headers.get(REQUEST_ID_HEADER);
  try {
    const body: unknown = await response.json();
    if (isApiError(body)) {
      return new ApiRequestError(response.status, {
        ...body,
        requestId: body.requestId ?? headerRequestId,
      });
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
