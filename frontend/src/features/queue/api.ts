import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { QueueBoardResponse, TicketLane, TicketResponse } from './types';

/** Ticket lists and the waiting-area display refresh this often. */
export const TICKETS_REFRESH_MS = 5_000;

const ticketsKey = ['queue', 'tickets'] as const;

export function useTickets() {
  return useQuery({
    queryKey: ticketsKey,
    queryFn: () => apiFetch<TicketResponse[]>('/api/queue/tickets'),
    refetchInterval: TICKETS_REFRESH_MS,
  });
}

function useRefreshTickets() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ticketsKey });
}

export function useIssueTicket() {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: (lane: TicketLane) => apiPost<TicketResponse>('/api/queue/tickets', { lane }),
    onSettled: refresh,
  });
}

/** Resolves with the called ticket, or null when nobody is waiting (204). */
export function useCallNext() {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: async () =>
      (await apiPost<TicketResponse | undefined>('/api/queue/tickets/call-next')) ?? null,
    onSettled: refresh,
  });
}

export function useCancelTicket() {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: (ticketId: string) =>
      apiPost<TicketResponse>(`/api/queue/tickets/${ticketId}/cancel`),
    onSettled: refresh,
  });
}

export function useAttendEmergency() {
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: (ticketId: string) =>
      apiPost<TicketResponse>(`/api/queue/tickets/${ticketId}/attend`),
    onSettled: refresh,
  });
}

/** The public display: no login needed. */
export function useQueueBoard() {
  return useQuery({
    queryKey: ['queue', 'board'],
    queryFn: () => apiFetch<QueueBoardResponse>('/api/public/queue-board'),
    refetchInterval: TICKETS_REFRESH_MS,
  });
}
