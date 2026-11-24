import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { invoiceKey, useResolvePayment, useSendMpesaPrompt } from './api';
import type { PaymentProgress } from './types';

const PHONE = /^(?:254|0)[17]\d{8}$/;
const MIN_REASON = 10;

/** The cashier's finding after checking the M-Pesa statement. */
function ResolveUnconfirmed({ visitId }: { visitId: string }) {
  const resolve = useResolvePayment(visitId);
  const [receipt, setReceipt] = useState('');
  const [reason, setReason] = useState('');
  const typed = reason.trim();
  const enoughReason = typed.length >= MIN_REASON;

  return (
    <form
      aria-label="Resolve this payment"
      onSubmit={(event) => {
        event.preventDefault();
      }}
    >
      <p role="alert">
        This payment could not be confirmed. Check the M-Pesa statement before taking any money.
      </p>
      <label htmlFor="mpesa-receipt">M-Pesa receipt (if it was received)</label>
      <input
        id="mpesa-receipt"
        maxLength={40}
        value={receipt}
        onChange={(event) => {
          setReceipt(event.target.value.trim());
        }}
      />
      <label htmlFor="mpesa-reason">What did you check? (at least 10 characters)</label>
      <textarea
        id="mpesa-reason"
        rows={2}
        maxLength={500}
        value={reason}
        onChange={(event) => {
          setReason(event.target.value);
        }}
      />
      {resolve.error ? <p role="alert">{errorMessage(resolve.error)}</p> : null}
      <div className="actions">
        <button
          type="button"
          disabled={resolve.isPending || !enoughReason || receipt.length < 6}
          onClick={() => {
            resolve.mutate({ paid: true, receipt, reason: typed });
          }}
        >
          Received in full
        </button>
        <button
          type="button"
          className="secondary"
          disabled={resolve.isPending || !enoughReason}
          onClick={() => {
            resolve.mutate({ paid: false, reason: typed });
          }}
        >
          Not received
        </button>
      </div>
    </form>
  );
}

interface MpesaPaymentProps {
  visitId: string;
  progress: PaymentProgress | undefined;
}

/** Sends the prompt and follows it. Only the server's confirmation marks the bill paid. */
export function MpesaPayment({ visitId, progress }: MpesaPaymentProps) {
  const queryClient = useQueryClient();
  const send = useSendMpesaPrompt(visitId);
  const [phone, setPhone] = useState('');
  const [key, setKey] = useState(() => crypto.randomUUID());
  const status = progress?.paymentStatus ?? null;
  const settled = progress !== undefined && progress.invoiceStatus !== 'UNPAID';

  // The background check confirmed payment, so show the bill as the server now has it.
  useEffect(() => {
    if (settled) {
      void queryClient.invalidateQueries({ queryKey: invoiceKey(visitId), exact: true });
      void queryClient.invalidateQueries({ queryKey: ['visits', 'queue'] });
    }
  }, [settled, queryClient, visitId]);

  if (settled) return null;
  if (status === 'UNCONFIRMED') return <ResolveUnconfirmed visitId={visitId} />;
  if (status === 'PENDING') {
    return <p role="status">Prompt sent. Waiting for the customer to enter their M-Pesa PIN...</p>;
  }

  const valid = PHONE.test(phone);
  return (
    <form
      aria-label="Pay with M-Pesa"
      onSubmit={(event) => {
        event.preventDefault();
        // A failed prompt is finished, so another try is a new payment.
        const idempotencyKey = status === 'FAILED' ? crypto.randomUUID() : key;
        setKey(idempotencyKey);
        send.mutate(
          { phone, idempotencyKey },
          {
            onSuccess: () => {
              setPhone('');
            },
          },
        );
      }}
    >
      {status === 'FAILED' ? (
        <p role="alert">The M-Pesa payment did not go through. No money was taken.</p>
      ) : null}
      {send.error ? <p role="alert">{errorMessage(send.error)}</p> : null}
      <label htmlFor="mpesa-phone">M-Pesa phone number</label>
      <input
        id="mpesa-phone"
        inputMode="tel"
        autoComplete="off"
        maxLength={12}
        value={phone}
        onChange={(event) => {
          setPhone(event.target.value.trim());
        }}
      />
      <button type="submit" disabled={send.isPending || !valid}>
        Send M-Pesa prompt
      </button>
    </form>
  );
}
