import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { CreatedStaff, CreateStaffRequest, StaffSummary } from './types';

const STAFF_KEY = ['admin', 'staff'];

export function useStaffList() {
  return useQuery({
    queryKey: STAFF_KEY,
    queryFn: () => apiFetch<StaffSummary[]>('/api/admin/staff'),
  });
}

/** Creates an account. gcTime 0 so the one-time password is not kept in the mutation cache. */
export function useCreateStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    gcTime: 0,
    mutationFn: (request: CreateStaffRequest) => apiPost<CreatedStaff>('/api/admin/staff', request),
    // Not awaited: the one-time password must show even if refreshing the list is slow.
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEY });
    },
  });
}

/** Disables an account. The server also ends all of that person's sessions. */
export function useDisableStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiPost<StaffSummary>(`/api/admin/staff/${id}/disable`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: STAFF_KEY }),
  });
}
