import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/render';
import {
  ApiRequestError,
  CSRF_HEADER,
  apiFetch,
  errorMessage,
  readCookie,
  setApiEventHandlers,
} from './client';

const REQUEST_ID = '0f8fad5b-d9cb-469f-a165-70867728950e';

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function expectApiError(promise: Promise<unknown>): Promise<ApiRequestError> {
  const error = await promise.then(
    () => {
      throw new Error('expected the call to fail');
    },
    (reason: unknown) => reason,
  );
  expect(error).toBeInstanceOf(ApiRequestError);
  return error as ApiRequestError;
}

describe('apiFetch', () => {
  it('returns the parsed body and always sends the session cookie', async () => {
    const fetchMock = stubFetch(jsonResponse(200, { name: 'Sauti Demo Clinic' }));

    const body = await apiFetch<{ name: string }>('/api/facility');

    expect(body).toEqual({ name: 'Sauti Demo Clinic' });
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.credentials).toBe('include');
    expect(new Headers(init?.headers).get('Accept')).toBe('application/json');
  });

  it('keeps caller headers given in any form', async () => {
    const fetchMock = stubFetch(jsonResponse(200, {}));

    await apiFetch('/api/things', { headers: [['X-Custom', 'yes']] });

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.get('X-Custom')).toBe('yes');
    expect(headers.get('Accept')).toBe('application/json');
  });

  it('returns undefined for 204 No Content', async () => {
    stubFetch(new Response(null, { status: 204 }));

    await expect(apiFetch('/api/nothing')).resolves.toBeUndefined();
  });

  it('turns an ApiError response into an ApiRequestError', async () => {
    stubFetch(
      jsonResponse(409, {
        code: 'CONFLICT',
        message: 'Already taken by another doctor',
        fieldErrors: [],
        requestId: REQUEST_ID,
      }),
    );

    const error = await expectApiError(apiFetch('/api/visits/1/take'));

    expect(error.status).toBe(409);
    expect(error.code).toBe('CONFLICT');
    expect(error.message).toBe('Already taken by another doctor');
    expect(error.requestId).toBe(REQUEST_ID);
  });

  it('keeps field errors from a validation failure', async () => {
    stubFetch(
      jsonResponse(400, {
        code: 'VALIDATION_FAILED',
        message: 'The request has invalid fields',
        fieldErrors: [{ field: 'phone', message: 'must be a Kenyan number' }],
        requestId: REQUEST_ID,
      }),
    );

    const error = await expectApiError(apiFetch('/api/patients'));

    expect(error.fieldErrors).toEqual([{ field: 'phone', message: 'must be a Kenyan number' }]);
  });

  it('falls back to the X-Request-Id header when the body has no request id', async () => {
    stubFetch(
      jsonResponse(
        404,
        { code: 'NOT_FOUND', message: 'Not found', fieldErrors: [], requestId: null },
        { 'X-Request-Id': REQUEST_ID },
      ),
    );

    const error = await expectApiError(apiFetch('/api/patients/missing'));

    expect(error.requestId).toBe(REQUEST_ID);
  });

  it('still gives a usable error when the body is not JSON', async () => {
    stubFetch(
      new Response('<html>Bad Gateway</html>', {
        status: 502,
        headers: { 'Content-Type': 'text/html', 'X-Request-Id': REQUEST_ID },
      }),
    );

    const error = await expectApiError(apiFetch('/api/anything'));

    expect(error.status).toBe(502);
    expect(error.code).toBe('HTTP_502');
    expect(error.requestId).toBe(REQUEST_ID);
  });
});

function apiError(code: string, message = 'Refused') {
  return { code, message, fieldErrors: [], requestId: REQUEST_ID };
}

function setCookie(value: string) {
  document.cookie = `XSRF-TOKEN=${value}; path=/`;
}

function clearCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
}

describe('CSRF token', () => {
  afterEach(clearCookie);

  it('copies the cookie into the header on changing requests', async () => {
    setCookie('token-1');
    const fetchMock = stubFetch(jsonResponse(200, {}));

    await apiFetch('/api/things', { method: 'POST', body: '{}' });

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get(CSRF_HEADER)).toBe('token-1');
  });

  it('sends no token on reads', async () => {
    setCookie('token-1');
    const fetchMock = stubFetch(jsonResponse(200, {}));

    await apiFetch('/api/things');

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).has(CSRF_HEADER)).toBe(false);
  });

  it('fetches a token first when there is no cookie yet', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation((input) => {
      if (input === '/api/auth/me') {
        setCookie('fresh');
        return Promise.resolve(jsonResponse(401, apiError('UNAUTHENTICATED')));
      }
      return Promise.resolve(jsonResponse(200, {}));
    });
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/api/auth/login', { method: 'POST', body: '{}' });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/me');
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get(CSRF_HEADER)).toBe('fresh');
  });

  it('gets a new token and tries once more when the token is refused', async () => {
    setCookie('stale');
    let calls = 0;
    const fetchMock = vi.fn<typeof fetch>().mockImplementation((input) => {
      if (input === '/api/auth/me') {
        setCookie('renewed');
        return Promise.resolve(jsonResponse(200, {}));
      }
      calls += 1;
      return Promise.resolve(
        calls === 1
          ? jsonResponse(403, apiError('CSRF_TOKEN_INVALID'))
          : jsonResponse(200, { saved: true }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const body = await apiFetch<{ saved: boolean }>('/api/things', { method: 'POST' });

    expect(body).toEqual({ saved: true });
    const lastHeaders = new Headers(fetchMock.mock.calls.at(-1)?.[1]?.headers);
    expect(lastHeaders.get(CSRF_HEADER)).toBe('renewed');
  });

  it('reads cookies by exact name', () => {
    document.cookie = 'OTHER-XSRF-TOKEN=nope; path=/';
    setCookie('a%3Db');

    expect(readCookie('XSRF-TOKEN')).toBe('a=b');
    document.cookie = 'OTHER-XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });
});

describe('session events', () => {
  afterEach(() => {
    setApiEventHandlers({
      onUnauthorized: () => undefined,
      onPasswordChangeRequired: () => undefined,
    });
  });

  function watch() {
    const events = { unauthorized: 0, passwordChange: 0 };
    setApiEventHandlers({
      onUnauthorized: () => {
        events.unauthorized += 1;
      },
      onPasswordChangeRequired: () => {
        events.passwordChange += 1;
      },
    });
    return events;
  }

  it('reports a 401 as the session being gone', async () => {
    const events = watch();
    stubFetch(jsonResponse(401, apiError('UNAUTHENTICATED')));

    await expectApiError(apiFetch('/api/patients'));

    expect(events.unauthorized).toBe(1);
  });

  it('does not treat a refused login step as the session being gone', async () => {
    const events = watch();
    setCookie('t');
    stubFetch(jsonResponse(401, apiError('LOGIN_FAILED')));

    await expectApiError(apiFetch('/api/auth/login', { method: 'POST' }));

    expect(events.unauthorized).toBe(0);
    clearCookie();
  });

  it('reports a required password change', async () => {
    const events = watch();
    stubFetch(jsonResponse(403, apiError('PASSWORD_CHANGE_REQUIRED')));

    await expectApiError(apiFetch('/api/patients'));

    expect(events.passwordChange).toBe(1);
  });
});

describe('errorMessage', () => {
  it('says plainly when access is refused', () => {
    expect(errorMessage(new ApiRequestError(403, apiError('FORBIDDEN')))).toBe(
      "You don't have access to this",
    );
  });

  it('shows the backend message otherwise, and a plain one for network failures', () => {
    expect(errorMessage(new ApiRequestError(422, apiError('BUSINESS_RULE_VIOLATED', 'Nope')))).toBe(
      'Nope',
    );
    expect(errorMessage(new TypeError('Failed to fetch'))).toContain('check your connection');
  });
});
