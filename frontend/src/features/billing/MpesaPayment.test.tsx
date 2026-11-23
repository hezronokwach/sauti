import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { InvoicePanel } from './InvoicePanel';
import type { Invoice, PaymentProgress } from './types';

const invoice: Invoice = {
  id: 'inv1',
  visitId: 'v1',
  status: 'UNPAID',
  totalKes: 500,
  paymentMethod: null,
  lines: [
    {
      kind: 'CONSULTATION',
      description: 'Consultation',
      quantity: 1,
      unitPriceKes: 500,
      amountKes: 500,
    },
  ],
};

const progress = (paymentStatus: PaymentProgress['paymentStatus']): PaymentProgress => ({
  invoiceStatus: 'UNPAID',
  paymentStatus,
});

describe('M-Pesa on the bill', () => {
  it('sends a prompt and shows it is waiting, without calling the bill paid', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/visits/v1/invoice': () => jsonResponse(200, invoice),
      'GET /api/visits/v1/invoice/payment': () => jsonResponse(200, progress(null)),
      'POST /api/visits/v1/invoice/mpesa': (init) => {
        sent = bodyOf(init);
        return jsonResponse(200, progress('PENDING'));
      },
    });
    renderWithQuery(<InvoicePanel visitId="v1" />);

    const phone = await screen.findByLabelText('M-Pesa phone number');
    const button = screen.getByRole('button', { name: 'Send M-Pesa prompt' });
    await user.type(phone, '123');
    expect(button).toBeDisabled();
    await user.clear(phone);
    await user.type(phone, '0712345678');
    await user.click(button);

    expect(await screen.findByText(/Waiting for the customer/)).toBeInTheDocument();
    expect(sent).toEqual({ phone: '0712345678', idempotencyKey: expect.any(String) as string });
    expect(screen.queryByText('Paid.')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cash' })).not.toBeInTheDocument();
  });

  it('asks for a statement check when a payment is unconfirmed', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    let paid = false;
    mockApi({
      'GET /api/visits/v1/invoice': () =>
        jsonResponse(200, paid ? { ...invoice, status: 'PAID', paymentMethod: 'MPESA' } : invoice),
      'GET /api/visits/v1/invoice/payment': () =>
        jsonResponse(
          200,
          paid ? { invoiceStatus: 'PAID', paymentStatus: 'PAID' } : progress('UNCONFIRMED'),
        ),
      'POST /api/visits/v1/invoice/mpesa/resolve': (init) => {
        sent = bodyOf(init);
        paid = true;
        return jsonResponse(200, { invoiceStatus: 'PAID', paymentStatus: 'PAID' });
      },
    });
    renderWithQuery(<InvoicePanel visitId="v1" />);

    const received = await screen.findByRole('button', { name: 'Received in full' });
    expect(screen.queryByRole('button', { name: 'Cash' })).not.toBeInTheDocument();
    expect(received).toBeDisabled();
    await user.type(screen.getByLabelText(/M-Pesa receipt/), 'SAB1CD2EF3');
    await user.type(screen.getByLabelText(/What did you check/), 'Seen on the statement');
    await user.click(received);

    expect(await screen.findByText('Paid.')).toBeInTheDocument();
    expect(sent).toEqual({ paid: true, receipt: 'SAB1CD2EF3', reason: 'Seen on the statement' });
  });

  it('says plainly when a payment failed and offers another try', async () => {
    mockApi({
      'GET /api/visits/v1/invoice': () => jsonResponse(200, invoice),
      'GET /api/visits/v1/invoice/payment': () => jsonResponse(200, progress('FAILED')),
    });
    renderWithQuery(<InvoicePanel visitId="v1" />);

    expect(await screen.findByText(/did not go through/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send M-Pesa prompt' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cash' })).toBeInTheDocument();
  });
});
