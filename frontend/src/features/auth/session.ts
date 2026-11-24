import { useQuery } from '@tanstack/react-query';
import { ApiRequestError, apiFetch } from '../../api/client';
import type { CurrentStaffResponse } from './types';

/** The cache entry for "who is logged in"; null means nobody. */
export const SESSION_QUERY_KEY = ['auth', 'me'] as const;

/** Who is logged in, or null. A 401 is the normal "not logged in" answer, not an error. */
export async function fetchCurrentStaff(): Promise<CurrentStaffResponse | null> {
  try {
    return await apiFetch<CurrentStaffResponse>('/api/auth/me');
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

/**
 * The logged-in staff member. Called on every page load, so reloading keeps the person
 * logged in for as long as the server session lives.
 */
export function useCurrentStaff() {
  return useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: fetchCurrentStaff,
    staleTime: 60_000,
    retry: false,
  });
}
