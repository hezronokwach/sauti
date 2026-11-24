import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { WaitingTimes } from './WaitingTimes';

const stage = (median: number | null, p90: number | null) => ({ median, p90 });

describe('WaitingTimes', () => {
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
    renderWithQuery(<WaitingTimes />);

    expect(await screen.findByText('6.0 / 7.6')).toBeInTheDocument();
    expect(screen.getByText('56.0 / 64.8')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '08:00' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'No ticket' })).toBeInTheDocument();
    expect(screen.getAllByText('- / -')).toHaveLength(4);
  });

  it('opens with headline numbers read exactly from the rows', async () => {
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
            lane: 'RETURNING',
            hour: 9,
            visits: 5,
            deskWait: stage(2, 3),
            registration: stage(1, 2),
            triageWait: stage(5, 8),
            doctorWait: stage(10, 12),
            doorToDoctor: stage(20, 24),
          },
        ]),
    });
    renderWithQuery(<WaitingTimes />);

    expect(await screen.findByText('7')).toBeInTheDocument();
    expect(screen.getByText(/Busiest hour/)).toHaveTextContent(
      'Busiest hour 09:00 (5 visits, Returning)',
    );
    expect(screen.getByText(/Slowest door to doctor/)).toHaveTextContent(
      'Slowest door to doctor: 9 in 10 seen within 64.8 min at 08:00 (New)',
    );
  });

  it('says when there are no visits', async () => {
    mockApi({ 'GET /api/admin/reports/waiting-times': () => jsonResponse(200, []) });
    renderWithQuery(<WaitingTimes />);

    expect(await screen.findByText('No visits in these dates.')).toBeInTheDocument();
  });
});
