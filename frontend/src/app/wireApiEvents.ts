import type { QueryClient } from '@tanstack/react-query';
import { setApiEventHandlers } from '../api/client';
import { SESSION_QUERY_KEY } from '../features/auth/session';

interface Navigator {
  navigate: (to: string) => Promise<void> | void;
}

/**
 * Tells the API client what to do when a call shows the session has changed: a 401
 * empties "who is logged in", so the route guard shows the login page; a required
 * password change opens the password page.
 */
export function wireApiEvents(queryClient: QueryClient, router: Navigator): void {
  setApiEventHandlers({
    onUnauthorized: () => {
      queryClient.setQueryData(SESSION_QUERY_KEY, null);
    },
    onPasswordChangeRequired: () => {
      void router.navigate('/change-password');
    },
  });
}
