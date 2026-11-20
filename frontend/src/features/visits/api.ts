import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { QueueEntryResponse, VisitResponse, VisitStatus } from './types';

/** Queues refresh every 5 seconds, so a taken patient disappears for everyone quickly. */
export const QUEUE_REFRESH_MS = 5_000;

const queueKey = (status: VisitStatus) => ['visits', 'queue', status] as const;

export function useQueue(status: VisitStatus) {
  return useQuery({
    queryKey: queueKey(status),
    queryFn: () => apiFetch<QueueEntryResponse[]>(`/api/visits?status=${status}`),
    refetchInterval: QUEUE_REFRESH_MS,
  });
}

/** After any visit change every queue is refreshed at once, not on the next poll. */
function useRefreshQueues() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['visits', 'queue'] });
}

export function useOpenVisit() {
  const refresh = useRefreshQueues();
  return useMutation({
    mutationFn: (request: { patientId: string; reasonForVisit?: string; ticketId?: string }) =>
      apiPost<VisitResponse>('/api/visits', request),
    onSettled: refresh,
  });
}

export function useTakeVisit() {
  const refresh = useRefreshQueues();
  return useMutation({
    mutationFn: (visitId: string) => apiPost<VisitResponse>(`/api/visits/${visitId}/take`),
    onSettled: refresh,
  });
}

export function useChangeVisitStatus() {
  const refresh = useRefreshQueues();
  return useMutation({
    mutationFn: ({ visitId, to, reason }: { visitId: string; to: VisitStatus; reason?: string }) =>
      apiPost<VisitResponse>(`/api/visits/${visitId}/status`, { to, reason }),
    onSettled: refresh,
  });
}
