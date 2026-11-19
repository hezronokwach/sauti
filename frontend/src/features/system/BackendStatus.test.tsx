import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { jsonResponse, renderWithQuery } from '../../test/render';
import { BackendStatus } from './BackendStatus';

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('BackendStatus', () => {
  it('shows a loading message, then UP', async () => {
    stubFetch(jsonResponse(200, { status: 'UP' }));

    renderWithQuery(<BackendStatus />);

    expect(screen.getByRole('status')).toHaveTextContent('Checking the backend');
    expect(await screen.findByText('UP')).toBeInTheDocument();
  });

  it('calls the health endpoint on the same site', async () => {
    const fetchMock = stubFetch(jsonResponse(200, { status: 'UP' }));

    renderWithQuery(<BackendStatus />);
    await screen.findByText('UP');

    expect(fetchMock).toHaveBeenCalledWith(
      '/actuator/health',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
  });

  it('shows DOWN when the backend reports itself unhealthy (503)', async () => {
    stubFetch(jsonResponse(503, { status: 'DOWN' }));

    renderWithQuery(<BackendStatus />);

    expect(await screen.findByText('DOWN')).toBeInTheDocument();
  });

  it('shows the error and its request id when the call fails', async () => {
    stubFetch(
      jsonResponse(500, {
        code: 'INTERNAL_ERROR',
        message: 'Something went wrong',
        fieldErrors: [],
        requestId: '11111111-2222-4333-8444-555555555555',
      }),
    );

    renderWithQuery(<BackendStatus />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong');
    expect(alert).toHaveTextContent('11111111-2222-4333-8444-555555555555');
  });

  it('shows an error when the backend cannot be reached at all', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch')),
    );

    renderWithQuery(<BackendStatus />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to fetch');
  });
});
