import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { InvoicePanel } from './InvoicePanel';
import type { Invoice } from './types';

const invoice: Invoice = {
  id: 'inv1',
  visitId: 'v1',
  status: 'UNPAID',
  totalKes: 575,
  paymentMethod: null,
  lines: [
    {
      kind: 'CONSULTATION',
      description: 'Consultation',
      quantity: 1,
      unitPriceKes: 500,
      amountKes: 500,
    },
    {
      kind: 'MEDICINE',
      description: 'Paracetamol 500 mg',
      quantity: 15,
      unitPriceKes: 5,
      amountKes: 75,
    },
  ],
};

describe('InvoicePanel', () => {
  it('shows the charges and asks before recording cash', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/visits/v1/invoice': () => jsonResponse(200, invoice),
      'POST /api/visits/v1/invoice/cash': (init) => {
        sent = bodyOf(init);
        return jsonResponse(200, { ...invoice, status: 'PAID', paymentMethod: 'CASH' });
      },
    });
    renderWithQuery(<InvoicePanel visitId="v1" />);

    expect(await screen.findByText('Paracetamol 500 mg')).toBeInTheDocument();
    expect(screen.getByText(/Total: KES 575\.00/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cash' }));
    expect(sent).toBeUndefined();
    await user.click(screen.getByRole('button', { name: 'Confirm cash received' }));

    expect(await screen.findByText('Paid.')).toBeInTheDocument();
    expect(sent).toEqual({ idempotencyKey: expect.any(String) as string });
    expect(screen.queryByRole('button', { name: 'Cash' })).not.toBeInTheDocument();
  });

  it('retries with the same key after a failure', async () => {
    const user = userEvent.setup();
    const keys: unknown[] = [];
    let attempts = 0;
    mockApi({
      'GET /api/visits/v1/invoice': () => jsonResponse(200, invoice),
      'POST /api/visits/v1/invoice/insurance': (init) => {
        keys.push(bodyOf(init));
        attempts += 1;
        return attempts === 1
          ? jsonResponse(503, apiError('SERVICE_UNAVAILABLE', 'Try again'))
          : jsonResponse(200, { ...invoice, status: 'INSURANCE_PENDING' });
      },
    });
    renderWithQuery(<InvoicePanel visitId="v1" />);

    await user.click(await screen.findByRole('button', { name: 'Insurance' }));
    await user.click(screen.getByRole('button', { name: 'Confirm bill to insurance' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirm bill to insurance' }));

    expect(await screen.findByText(/The claim is pending/)).toBeInTheDocument();
    expect(keys[0]).toEqual(keys[1]);
  });
});
