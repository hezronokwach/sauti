import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { Invoice, SettleMethod } from './types';

const invoiceKey = (visitId: string) => ['visits', visitId, 'invoice'] as const;

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
