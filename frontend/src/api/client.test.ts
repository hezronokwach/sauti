import { describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/render';
import { ApiRequestError, apiFetch } from './client';

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
  it('returns the parsed body and sends same-site cookies', async () => {
    const fetchMock = stubFetch(jsonResponse(200, { name: 'Sauti Demo Clinic' }));

    const body = await apiFetch<{ name: string }>('/api/facility');

    expect(body).toEqual({ name: 'Sauti Demo Clinic' });
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.credentials).toBe('same-origin');
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
