import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { SystemStatus, type SystemStatusData } from './SystemStatus';

const healthy: SystemStatusData = {
  jobs: { queued: 2, running: 1, failedLast24Hours: 0, oldestWaitingSince: null, failedByType: [] },
  backup: {
    lastRunAt: '2026-11-24T00:00:00Z',
    lastResult: 'SUCCESS',
    lastSuccessAt: '2026-11-24T00:00:00Z',
    overdue: false,
  },
  rateLimitBlocks: [],
};

function show(data: SystemStatusData) {
  mockApi({ 'GET /api/admin/status': () => jsonResponse(200, data) });
  renderWithQuery(<SystemStatus />);
}

describe('SystemStatus', () => {
  it('raises no alarm when all is well', async () => {
    show(healthy);

    expect(await screen.findByText(/Queued: 2/)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a failed job and a failed backup', async () => {
    show({
      ...healthy,
      jobs: {
        ...healthy.jobs,
        failedLast24Hours: 1,
        failedByType: [{ type: 'TRANSCRIBE', count: 1 }],
      },
      backup: { ...healthy.backup, lastResult: 'FAILED', overdue: true },
    });

    expect(await screen.findByText(/1 background job\(s\) failed/)).toBeInTheDocument();
    expect(screen.getByText('TRANSCRIBE: 1')).toBeInTheDocument();
    expect(screen.getByText('The last backup failed.')).toBeInTheDocument();
  });

  it('warns when no backup has ever run', async () => {
    show({
      ...healthy,
      backup: { lastRunAt: null, lastResult: null, lastSuccessAt: null, overdue: true },
    });

    expect(await screen.findByText('No backup has run yet.')).toBeInTheDocument();
  });

  it('lists refused requests by limit', async () => {
    show({ ...healthy, rateLimitBlocks: [{ limit: 'LOGIN_ACCOUNT', count: 3 }] });

    expect(await screen.findByText('LOGIN_ACCOUNT: 3')).toBeInTheDocument();
  });
});
