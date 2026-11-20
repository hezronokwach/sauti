import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { EmergencyAlerts } from './EmergencyAlerts';
import { TicketDesk } from './TicketDesk';
import type { TicketResponse } from './types';
import { WaitingAreaPage } from './WaitingAreaPage';

function ticket(overrides: Partial<TicketResponse>): TicketResponse {
  return {
    id: 't1',
    code: 'N-001',
    lane: 'NEW',
    status: 'WAITING',
    arrivedAt: new Date().toISOString(),
    calledAt: null,
    visitId: null,
    ...overrides,
  };
}

describe('TicketDesk', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('gives a ticket in the chosen lane and shows its code', async () => {
    const issued: unknown[] = [];
    mockApi({
      'GET /api/queue/tickets': () => jsonResponse(200, []),
      'POST /api/queue/tickets': (init) => {
        issued.push(bodyOf(init));
        return jsonResponse(201, ticket({ code: 'R-004', lane: 'RETURNING' }));
      },
    });
    renderWithQuery(<TicketDesk current={null} onCurrent={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Returning' }));

    expect(await screen.findByText('R-004')).toBeInTheDocument();
    expect(issued).toEqual([{ lane: 'RETURNING' }]);
  });

  it('calls the next ticket and hands it to reception', async () => {
    const called = ticket({ status: 'CALLED', code: 'P-002', lane: 'PRE_REGISTERED' });
    mockApi({
      'GET /api/queue/tickets': () => jsonResponse(200, [ticket({})]),
      'POST /api/queue/tickets/call-next': () => jsonResponse(200, called),
    });
    const onCurrent = vi.fn();
    renderWithQuery(<TicketDesk current={null} onCurrent={onCurrent} />);

    expect(await screen.findByText('1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Call next' }));

    await vi.waitFor(() => {
      expect(onCurrent).toHaveBeenCalledWith(called);
    });
  });

  it('says when nobody is waiting', async () => {
    mockApi({
      'GET /api/queue/tickets': () => jsonResponse(200, []),
      'POST /api/queue/tickets/call-next': () => new Response(null, { status: 204 }),
    });
    renderWithQuery(<TicketDesk current={null} onCurrent={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Call next' }));

    expect(await screen.findByText('Nobody is waiting.')).toBeInTheDocument();
  });

  it('cancels a called ticket whose patient did not come', async () => {
    mockApi({
      'GET /api/queue/tickets': () => jsonResponse(200, []),
      'POST /api/queue/tickets/t1/cancel': () => jsonResponse(200, ticket({ status: 'CANCELLED' })),
    });
    const onCurrent = vi.fn();
    renderWithQuery(<TicketDesk current={ticket({ status: 'CALLED' })} onCurrent={onCurrent} />);

    expect(screen.getByText('N-001')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Did not come: cancel N-001' }));

    await vi.waitFor(() => {
      expect(onCurrent).toHaveBeenCalledWith(null);
    });
  });
});

describe('EmergencyAlerts', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('shows waiting emergencies to clinical staff, who attend them', async () => {
    const attended: string[] = [];
    mockApi({
      'GET /api/queue/tickets': () =>
        jsonResponse(200, [
          ticket({ id: 'e1', code: 'E-001', lane: 'EMERGENCY' }),
          ticket({ id: 'n1', code: 'N-001' }),
        ]),
      'POST /api/queue/tickets/e1/attend': () => {
        attended.push('e1');
        return jsonResponse(200, ticket({ id: 'e1', lane: 'EMERGENCY', status: 'CALLED' }));
      },
    });
    renderWithQuery(<EmergencyAlerts />);

    fireEvent.click(await screen.findByRole('button', { name: 'Attend E-001' }));

    await vi.waitFor(() => {
      expect(attended).toEqual(['e1']);
    });
    expect(screen.queryByText('N-001')).not.toBeInTheDocument();
  });

  it('shows nothing when there is no emergency', async () => {
    mockApi({ 'GET /api/queue/tickets': () => jsonResponse(200, [ticket({})]) });
    const { container } = renderWithQuery(<EmergencyAlerts />);

    await vi.waitFor(() => {
      expect(container).toBeEmptyDOMElement();
    });
  });
});

describe('WaitingAreaPage', () => {
  it('shows the code being called large, and the codes still waiting', async () => {
    mockApi({
      'GET /api/public/queue-board': () =>
        jsonResponse(200, { called: ['R-002', 'N-001'], waiting: ['N-002', 'N-003'] }),
    });
    renderWithQuery(<WaitingAreaPage />);

    expect(await screen.findByText('R-002')).toHaveClass('board-now');
    expect(screen.getByText('N-002')).toBeInTheDocument();
    expect(screen.getByText('N-003')).toBeInTheDocument();
  });
});
