import { act, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { apiFetch } from '../api/client';
import type { CurrentStaffResponse, StaffRole } from '../features/auth/types';
import { apiError, jsonResponse, mockApi, renderRoute } from '../test/render';
import { healthUp, meReturns, staffMember } from '../test/staff';

function loggedInAs(staff: CurrentStaffResponse | null) {
  return mockApi({ 'GET /api/auth/me': meReturns(() => staff), 'GET /actuator/health': healthUp });
}

describe('route guards', () => {
  it('sends a logged-out visitor at / to the login page', async () => {
    loggedInAs(null);

    renderRoute('/');

    expect(await screen.findByRole('heading', { name: 'Log in to Sauti HMS' })).toBeInTheDocument();
  });

  it('remembers the page a logged-out visitor asked for', async () => {
    loggedInAs(null);

    const { router } = renderRoute('/doctor');

    await screen.findByRole('heading', { name: 'Log in to Sauti HMS' });
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?next=%2Fdoctor');
  });

  it.each<[StaffRole, string, string]>([
    ['RECEPTIONIST', '/reception', 'Reception'],
    ['NURSE', '/triage', 'Triage'],
    ['DOCTOR', '/doctor', 'Doctor'],
    ['CASHIER', '/billing', 'Billing'],
    ['ADMIN', '/admin', 'Administration'],
  ])('sends a %s from / to their home', async (role, path, heading) => {
    loggedInAs(staffMember(role));

    const { router } = renderRoute('/');

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(path);
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });

  it("shows no access when a role opens another role's page", async () => {
    loggedInAs(staffMember('NURSE'));

    renderRoute('/doctor');

    expect(
      await screen.findByRole('heading', { name: "You don't have access to this" }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to your home page' })).toHaveAttribute(
      'href',
      '/triage',
    );
  });

  it('holds someone with a one-time password at the password change', async () => {
    loggedInAs(staffMember('DOCTOR', { mustChangePassword: true }));

    const { router } = renderRoute('/doctor');

    expect(
      await screen.findByRole('heading', { name: 'Change your password' }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/change-password');
  });

  it('goes to the login page when a later call finds the session gone', async () => {
    mockApi({
      'GET /api/auth/me': meReturns(() => staffMember('ADMIN')),
      'GET /api/patients': () =>
        jsonResponse(401, apiError('UNAUTHENTICATED', 'You need to log in')),
      'GET /actuator/health': healthUp,
    });
    renderRoute('/admin');
    await screen.findByRole('heading', { name: 'Administration' });

    await act(async () => {
      await apiFetch('/api/patients').catch(() => undefined);
    });

    expect(await screen.findByRole('heading', { name: 'Log in to Sauti HMS' })).toBeInTheDocument();
  });

  it('shows the not-found page for an unknown path', () => {
    loggedInAs(null);

    renderRoute('/no/such/page');

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute('href', '/');
  });
});
