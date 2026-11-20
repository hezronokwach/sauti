import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { apiFetch } from '../../api/client';
import { SESSION_QUERY_KEY, fetchCurrentStaff } from './session';

/** Why the person was logged out, shown on the login page. */
export type LogoutReason = 'idle';

/**
 * Logs out: ends the server session, forgets every cached response (so no patient data
 * stays in memory), asks the backend again who is logged in (which also brings a fresh
 * CSRF cookie), and goes to the login page.
 */
export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useCallback(
    async (reason?: LogoutReason) => {
      try {
        await apiFetch<undefined>('/api/auth/logout', { method: 'POST' });
      } catch {
        // Already logged out (expired or disabled): carry on to the login page.
      }
      queryClient.clear();
      queryClient.setQueryData(SESSION_QUERY_KEY, await fetchCurrentStaff().catch(() => null));
      await navigate(reason ? `/login?reason=${reason}` : '/login', { replace: true });
    },
    [navigate, queryClient],
  );
}
