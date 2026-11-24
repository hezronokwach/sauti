import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { Invoice, PaymentProgress, SettleMethod } from './types';

/** How often the screen checks a sent M-Pesa prompt. */
export const PAYMENT_REFRESH_MS = 3_000;

export const invoiceKey = (visitId: string) => ['visits', visitId, 'invoice'] as const;
const paymentKey = (visitId: string) => [...invoiceKey(visitId), 'payment'] as const;

export function useInvoice(visitId: string) {
  return useQuery({
    queryKey: invoiceKey(visitId),
    queryFn: () => apiFetch<Invoice>(`/api/visits/${visitId}/invoice`),
  });
}

/** Settles the bill. The key stays the same on a retry, so it can only be recorded once. */
export function useSettleInvoice(visitId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ method, idempotencyKey }: { method: SettleMethod; idempotencyKey: string }) =>
      apiPost<Invoice>(`/api/visits/${visitId}/invoice/${method}`, { idempotencyKey }),
    onSuccess: (invoice) => {
      queryClient.setQueryData(invoiceKey(visitId), invoice);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['visits', 'queue'] }),
  });
}

/** Where the payment stands, refreshed only while a prompt is waiting on the customer. */
export function usePaymentProgress(visitId: string) {
  return useQuery({
    queryKey: paymentKey(visitId),
    queryFn: () => apiFetch<PaymentProgress>(`/api/visits/${visitId}/invoice/payment`),
    refetchInterval: (query) =>
      query.state.data?.paymentStatus === 'PENDING' ? PAYMENT_REFRESH_MS : false,
  });
}

function useApplyProgress(visitId: string) {
  const queryClient = useQueryClient();
  return (progress: PaymentProgress) => {
    queryClient.setQueryData(paymentKey(visitId), progress);
    if (progress.invoiceStatus !== 'UNPAID') {
      void queryClient.invalidateQueries({ queryKey: invoiceKey(visitId), exact: true });
      void queryClient.invalidateQueries({ queryKey: ['visits', 'queue'] });
    }
  };
}

export function useSendMpesaPrompt(visitId: string) {
  const apply = useApplyProgress(visitId);
  return useMutation({
    mutationFn: (request: { phone: string; idempotencyKey: string }) =>
      apiPost<PaymentProgress>(`/api/visits/${visitId}/invoice/mpesa`, request),
    onSuccess: apply,
  });
}

export function useResolvePayment(visitId: string) {
  const apply = useApplyProgress(visitId);
  return useMutation({
    mutationFn: (request: { paid: boolean; receipt?: string; reason: string }) =>
      apiPost<PaymentProgress>(`/api/visits/${visitId}/invoice/mpesa/resolve`, request),
    onSuccess: apply,
  });
}
