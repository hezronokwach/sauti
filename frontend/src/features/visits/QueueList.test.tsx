import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { QueueList } from './QueueList';
import { minutesWaiting } from './time';
import type { QueueEntryResponse } from './types';

function entry(overrides: Partial<QueueEntryResponse> = {}): QueueEntryResponse {
  return {
    visitId: 'visit-1',
    queueNumber: 7,
    status: 'WAITING_TRIAGE',
    patientId: 'patient-1',
    patientName: 'Achieng Odhiambo',
    patientNumber: 'SH-000042',
    waitingSince: new Date(Date.now() - 12 * 60_000).toISOString(),
    ...overrides,
  };
}

describe('QueueList', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the number, name and waiting time only', async () => {
    mockApi({ 'GET /api/visits': () => jsonResponse(200, [entry()]) });

    renderWithQuery(<QueueList title="Waiting for triage" status="WAITING_TRIAGE" />);

    expect(await screen.findByText('Achieng Odhiambo')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('SH-000042')).toBeInTheDocument();
    expect(screen.getByText('12 min')).toBeInTheDocument();
  });

  it('takes a patient', async () => {
    const user = userEvent.setup();
    document.cookie = 'XSRF-TOKEN=t; path=/';
    const fetchMock = mockApi({
      'GET /api/visits': () => jsonResponse(200, [entry()]),
      'POST /api/visits/visit-1/take': () => jsonResponse(200, { id: 'visit-1' }),
    });
    renderWithQuery(
      <QueueList
        title="Waiting"
        status="WAITING_TRIAGE"
        action={{ kind: 'take', label: 'Take' }}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Take Achieng Odhiambo' }));

    expect(fetchMock.mock.calls.some(([path]) => path === '/api/visits/visit-1/take')).toBe(true);
  });

  it('opens the patient once taken, and takes the first in line with one button', async () => {
    const user = userEvent.setup();
    document.cookie = 'XSRF-TOKEN=t; path=/';
    const fetchMock = mockApi({
      'GET /api/visits': () =>
        jsonResponse(200, [
          entry(),
          entry({ visitId: 'visit-2', queueNumber: 8, patientName: 'Baraka Simiyu' }),
        ]),
      'POST /api/visits/visit-1/take': () => jsonResponse(200, { id: 'visit-1' }),
    });
    const onTaken = vi.fn();
    renderWithQuery(
      <QueueList
        title="Waiting"
        status="WAITING_TRIAGE"
        action={{ kind: 'take', label: 'Take', onTaken }}
      />,
    );

    await user.click(
      await screen.findByRole('button', { name: 'Take next patient: Achieng Odhiambo' }),
    );

    await vi.waitFor(() => {
      expect(onTaken).toHaveBeenCalledWith('visit-1');
    });
    expect(fetchMock.mock.calls.some(([path]) => path === '/api/visits/visit-2/take')).toBe(false);
  });

  it('does not open a patient someone else took first', async () => {
    const user = userEvent.setup();
    document.cookie = 'XSRF-TOKEN=t; path=/';
    mockApi({
      'GET /api/visits': () => jsonResponse(200, [entry()]),
      'POST /api/visits/visit-1/take': () =>
        jsonResponse(409, apiError('CONFLICT', 'This patient has already been taken')),
    });
    const onTaken = vi.fn();
    renderWithQuery(
      <QueueList
        title="Waiting"
        status="WAITING_TRIAGE"
        action={{ kind: 'take', label: 'Take', onTaken }}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Take Achieng Odhiambo' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(onTaken).not.toHaveBeenCalled();
  });

  it('says plainly when someone else took the patient first', async () => {
    const user = userEvent.setup();
    document.cookie = 'XSRF-TOKEN=t; path=/';
    mockApi({
      'GET /api/visits': () => jsonResponse(200, [entry()]),
      'POST /api/visits/visit-1/take': () =>
        jsonResponse(409, apiError('CONFLICT', 'This patient has already been taken')),
    });
    renderWithQuery(
      <QueueList
        title="Waiting"
        status="WAITING_TRIAGE"
        action={{ kind: 'take', label: 'Take' }}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Take Achieng Odhiambo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Someone else has already taken this patient.',
    );
  });

  it('moves the visit on with the configured status', async () => {
    const user = userEvent.setup();
    document.cookie = 'XSRF-TOKEN=t; path=/';
    const fetchMock = mockApi({
      'GET /api/visits': () => jsonResponse(200, [entry({ status: 'IN_TRIAGE' })]),
      'POST /api/visits/visit-1/status': () => jsonResponse(200, { id: 'visit-1' }),
    });
    renderWithQuery(
      <QueueList
        title="In triage"
        status="IN_TRIAGE"
        action={{ kind: 'move', label: 'Send to doctor', to: 'WAITING_DOCTOR' }}
      />,
    );

    await user.click(
      await screen.findByRole('button', { name: 'Send to doctor Achieng Odhiambo' }),
    );

    const call = fetchMock.mock.calls.find(([path]) => path === '/api/visits/visit-1/status');
    expect(bodyOf(call?.[1])).toEqual({ to: 'WAITING_DOCTOR' });
  });

  it('refreshes every 5 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = mockApi({ 'GET /api/visits': () => jsonResponse(200, []) });
    renderWithQuery(<QueueList title="Waiting" status="WAITING_DOCTOR" />);
    await screen.findByText('Nobody waiting.');
    const before = fetchMock.mock.calls.length;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });

    expect(fetchMock.mock.calls.length).toBeGreaterThan(before);
    expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/visits?status=WAITING_DOCTOR');
  });

  it('counts whole minutes and never goes negative', () => {
    const now = Date.parse('2026-01-01T08:30:00Z');
    expect(minutesWaiting('2026-01-01T08:00:30Z', now)).toBe(29);
    expect(minutesWaiting('2026-01-01T08:31:00Z', now)).toBe(0);
  });
});
