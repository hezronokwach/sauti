import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { WaitingTimesPage } from './WaitingTimesPage';

const stage = (median: number | null, p90: number | null) => ({ median, p90 });

describe('WaitingTimesPage', () => {
  it('shows each lane and hour with the median and 90th percentile of every stage', async () => {
    mockApi({
      'GET /api/admin/reports/waiting-times': () =>
        jsonResponse(200, [
          {
            lane: 'NEW',
            hour: 8,
            visits: 2,
            deskWait: stage(6, 7.6),
            registration: stage(5, 5.8),
            triageWait: stage(15, 19),
            doctorWait: stage(25, 29),
            doorToDoctor: stage(56, 64.8),
          },
          {
            lane: 'NO_TICKET',
            hour: 10,
            visits: 1,
            deskWait: stage(0, 0),
            registration: stage(null, null),
            triageWait: stage(null, null),
            doctorWait: stage(null, null),
            doorToDoctor: stage(null, null),
          },
        ]),
    });
    renderWithQuery(<WaitingTimesPage />);

    expect(await screen.findByText('6.0 / 7.6')).toBeInTheDocument();
    expect(screen.getByText('56.0 / 64.8')).toBeInTheDocument();
    expect(screen.getByText('08:00')).toBeInTheDocument();
    expect(screen.getByText('No ticket')).toBeInTheDocument();
    expect(screen.getAllByText('- / -')).toHaveLength(4);
  });

  it('says when there are no visits', async () => {
    mockApi({ 'GET /api/admin/reports/waiting-times': () => jsonResponse(200, []) });
    renderWithQuery(<WaitingTimesPage />);

    expect(await screen.findByText('No visits in these dates.')).toBeInTheDocument();
  });
});
