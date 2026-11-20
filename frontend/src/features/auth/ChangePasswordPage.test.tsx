import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi, renderRoute } from '../../test/render';
import { healthUp, meReturns, staffMember } from '../../test/staff';
import type { CurrentStaffResponse } from './types';

function backend(password: () => Response) {
  let staff: CurrentStaffResponse = staffMember('NURSE', { mustChangePassword: true });
  return mockApi({
    'GET /api/auth/me': meReturns(() => staff),
    'GET /actuator/health': healthUp,
    'POST /api/auth/password': () => {
      const response = password();
      if (response.ok) {
        staff = { ...staff, mustChangePassword: false };
      }
      return response;
    },
  });
}

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  current: string,
  next: string,
  again: string,
) {
  await user.type(await screen.findByLabelText('Current password'), current);
  await user.type(screen.getByLabelText(/^New password \(at least/), next);
  await user.type(screen.getByLabelText('New password again'), again);
  await user.click(screen.getByRole('button', { name: 'Change password' }));
}

describe('ChangePasswordPage', () => {
  it('explains why a one-time password must be changed', async () => {
    backend(() => jsonResponse(200, {}));

    renderRoute('/change-password');

    expect(
      await screen.findByText(
        'You logged in with a one-time password. Choose your own before continuing.',
      ),
    ).toBeInTheDocument();
  });

  it('refuses a short password and a mismatch before calling the backend', async () => {
    const user = userEvent.setup();
    const fetchMock = backend(() => jsonResponse(200, {}));
    renderRoute('/change-password');

    await fill(user, 'one-time-pass', 'short', 'different');

    expect(await screen.findByText('Use at least 12 characters')).toBeInTheDocument();
    expect(screen.getByText('The two new passwords do not match')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/password')).toBe(false);
  });

  it("shows the backend's reason when it refuses", async () => {
    const user = userEvent.setup();
    backend(() =>
      jsonResponse(422, apiError('BUSINESS_RULE_VIOLATED', 'The current password is not correct')),
    );
    renderRoute('/change-password');

    await fill(user, 'wrong-password', 'A-much-better-one-2026', 'A-much-better-one-2026');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The current password is not correct',
    );
  });

  it('sends only the two passwords and then opens the role home', async () => {
    const user = userEvent.setup();
    const fetchMock = backend(() =>
      jsonResponse(200, staffMember('NURSE', { mustChangePassword: false })),
    );
    const { router } = renderRoute('/change-password');

    await fill(user, 'one-time-pass', 'A-much-better-one-2026', 'A-much-better-one-2026');

    expect(await screen.findByRole('heading', { name: 'Triage' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/triage');
    const call = fetchMock.mock.calls.find(([path]) => path === '/api/auth/password');
    expect(bodyOf(call?.[1])).toEqual({
      currentPassword: 'one-time-pass',
      newPassword: 'A-much-better-one-2026',
    });
  });
});
