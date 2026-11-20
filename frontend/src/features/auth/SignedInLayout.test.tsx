import { act, fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockApi, renderRoute } from '../../test/render';
import { healthUp, meReturns, staffMember } from '../../test/staff';
import type { CurrentStaffResponse } from './types';
import { IDLE_TIMEOUT_MS, reportActivity } from './useIdleTimeout';

const MINUTE = 60_000;

/** A fake backend where logging out really ends the session. */
function backend() {
  let staff: CurrentStaffResponse | null = staffMember('ADMIN');
  const fetchMock = mockApi({
    'GET /api/auth/me': meReturns(() => staff),
    'GET /actuator/health': healthUp,
    'POST /api/auth/logout': () => {
      staff = null;
      return new Response(null, { status: 204 });
    },
  });
  const loggedOut = () => fetchMock.mock.calls.some(([path]) => path === '/api/auth/logout');
  return { loggedOut };
}

describe('logout', () => {
  it('ends the session, forgets cached data and shows the login page', async () => {
    const user = userEvent.setup();
    const { loggedOut } = backend();
    const { queryClient, router } = renderRoute('/admin');
    await screen.findByRole('heading', { name: 'Administration' });
    queryClient.setQueryData(['patients', 'some-id'], { name: 'A patient' });

    await user.click(screen.getByRole('button', { name: 'Log out' }));

    expect(await screen.findByRole('heading', { name: 'Log in to Sauti HMS' })).toBeInTheDocument();
    expect(loggedOut()).toBe(true);
    expect(queryClient.getQueryData(['patients', 'some-id'])).toBeUndefined();
    expect(router.state.location.pathname).toBe('/login');
  });
});

describe('idle timeout', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function openAdminHome() {
    const session = backend();
    renderRoute('/admin');
    await screen.findByRole('heading', { name: 'Administration' });
    return session;
  }

  it('logs out after 10 minutes without input', async () => {
    const { loggedOut } = await openAdminHome();

    act(() => {
      vi.advanceTimersByTime(IDLE_TIMEOUT_MS);
    });

    expect(
      await screen.findByText('You were logged out after 10 minutes without activity.'),
    ).toBeInTheDocument();
    expect(loggedOut()).toBe(true);
  });

  it('starts counting again on every key press', async () => {
    const { loggedOut } = await openAdminHome();

    act(() => {
      vi.advanceTimersByTime(9 * MINUTE);
    });
    fireEvent.keyDown(window, { key: 'a' });
    act(() => {
      vi.advanceTimersByTime(9 * MINUTE);
    });

    expect(loggedOut()).toBe(false);
    expect(screen.getByRole('heading', { name: 'Administration' })).toBeInTheDocument();
  });

  it('counts reported work, such as recording, as activity', async () => {
    const { loggedOut } = await openAdminHome();

    act(() => {
      vi.advanceTimersByTime(9 * MINUTE);
      reportActivity();
      vi.advanceTimersByTime(9 * MINUTE);
    });

    expect(loggedOut()).toBe(false);
  });
});
