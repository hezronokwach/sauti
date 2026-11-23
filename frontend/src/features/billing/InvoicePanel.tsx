import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { useInvoice, useSettleInvoice } from './api';
import type { SettleMethod } from './types';

const money = (amount: number) =>
  `KES ${amount.toLocaleString('en-KE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const SETTLED = {
  PAID: 'Paid.',
  INSURANCE_PENDING: 'Billed to insurance. The claim is pending.',
} as const;

const CONFIRM: Record<SettleMethod, string> = {
  cash: 'Confirm cash received',
  insurance: 'Confirm bill to insurance',
};

/** One visit's bill: charges only, nothing clinical. */
export function InvoicePanel({ visitId }: { visitId: string }) {
  const invoice = useInvoice(visitId);
  const settle = useSettleInvoice(visitId);
  const [choice, setChoice] = useState<{ method: SettleMethod; idempotencyKey: string } | null>(
    null,
  );

  if (invoice.isPending) return <p role="status">Loading the bill...</p>;
  if (invoice.error) return <p role="alert">{errorMessage(invoice.error)}</p>;
  const current = invoice.data;

  const choose = (method: SettleMethod) => {
    settle.reset();
    setChoice({ method, idempotencyKey: crypto.randomUUID() });
  };

  return (
    <section aria-labelledby="invoice-heading">
      <h2 id="invoice-heading">Bill</h2>
      <table>
        <thead>
          <tr>
            <th scope="col">Item</th>
            <th scope="col">Qty</th>
            <th scope="col">Price</th>
            <th scope="col">Amount</th>
          </tr>
        </thead>
        <tbody>
          {current.lines.map((line, index) => (
            <tr key={`${line.description}-${String(index)}`}>
              <td>{line.description}</td>
              <td>{line.quantity}</td>
              <td>{money(line.unitPriceKes)}</td>
              <td>{money(line.amountKes)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <strong>Total: {money(current.totalKes)}</strong>
      </p>
      {current.status === 'UNPAID' ? null : <p role="status">{SETTLED[current.status]}</p>}
      {settle.error ? <p role="alert">{errorMessage(settle.error)}</p> : null}
      {current.status === 'UNPAID' ? (
        <div className="actions">
          {choice ? (
            <>
              <button
                type="button"
                disabled={settle.isPending}
                onClick={() => {
                  settle.mutate(choice);
                }}
              >
                {CONFIRM[choice.method]}
              </button>
              <button
                type="button"
                className="secondary"
                disabled={settle.isPending}
                onClick={() => {
                  settle.reset();
                  setChoice(null);
                }}
              >
                Back
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  choose('cash');
                }}
              >
                Cash
              </button>
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  choose('insurance');
                }}
              >
                Insurance
              </button>
            </>
          )}
        </div>
      ) : null}
    </section>
  );
}
