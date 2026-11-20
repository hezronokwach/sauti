import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi, renderRoute } from '../../test/render';
import { healthUp, meReturns, staffMember } from '../../test/staff';
import type { CurrentStaffResponse } from './types';

const SETUP_URI =
  'otpauth://totp/Sauti%3Aamina%40example.test?secret=JBSWY3DPEHPK3PXP&issuer=Sauti';

/** A fake backend for one login: the person is logged in once the code step succeeds. */
function backend(options: {
  login?: () => Response;
  code?: () => Response;
  staff?: CurrentStaffResponse;
}) {
  let loggedIn: CurrentStaffResponse | null = null;
  const staff = options.staff ?? staffMember('DOCTOR');
  const fetchMock = mockApi({
    'GET /api/auth/me': meReturns(() => loggedIn),
    'GET /actuator/health': healthUp,
    'POST /api/auth/login':
      options.login ??
      (() => jsonResponse(200, { codeRequired: true, setupRequired: false, otpauthUri: null })),
    'POST /api/auth/code': () => {
      const response = options.code?.() ?? jsonResponse(200, staff);
      if (response.ok) {
        loggedIn = staff;
      }
      return response;
    },
  });
  return fetchMock;
}

async function enterPassword(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Email'), 'amina@example.test');
  await user.type(screen.getByLabelText('Password'), 'Habari-yako-2026');
  await user.click(screen.getByRole('button', { name: 'Continue' }));
}

describe('LoginPage', () => {
  it('logs in with password then code and lands on the role home', async () => {
    const user = userEvent.setup();
    const fetchMock = backend({});
    const { router } = renderRoute('/login');

    await enterPassword(user);
    await user.type(await screen.findByLabelText('Authenticator code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('heading', { name: 'Doctor' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/doctor');
    const login = fetchMock.mock.calls.find(([path]) => path === '/api/auth/login');
    expect(bodyOf(login?.[1])).toEqual({
      email: 'amina@example.test',
      password: 'Habari-yako-2026',
    });
  });

  it('shows the QR code and the key on the first login', async () => {
    const user = userEvent.setup();
    backend({
      login: () =>
        jsonResponse(200, { codeRequired: true, setupRequired: true, otpauthUri: SETUP_URI }),
    });
    renderRoute('/login');

    await enterPassword(user);

    expect(
      await screen.findByRole('heading', { name: 'Set up your authenticator app' }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('img', { name: 'QR code to scan with your authenticator app' }),
    ).toHaveAttribute('src', expect.stringMatching(/^data:image\/svg\+xml/) as string);
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP')).toBeInTheDocument();
  });

  it('shows the generic message when the password step fails', async () => {
    const user = userEvent.setup();
    backend({
      login: () => jsonResponse(401, apiError('LOGIN_FAILED', 'Login failed. Check your details')),
    });
    renderRoute('/login');

    await enterPassword(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('Login failed. Check your details');
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
  });

  it('goes back to the password step when the login has expired', async () => {
    const user = userEvent.setup();
    backend({
      code: () =>
        jsonResponse(401, apiError('LOGIN_EXPIRED', 'The login has expired; start again')),
    });
    renderRoute('/login');

    await enterPassword(user);
    await user.type(await screen.findByLabelText('Authenticator code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('The login has expired; start again')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('checks the fields before calling the backend', async () => {
    const user = userEvent.setup();
    const fetchMock = backend({});
    renderRoute('/login');

    await user.click(await screen.findByRole('button', { name: 'Continue' }));

    expect(await screen.findByText('Enter your email')).toBeInTheDocument();
    expect(screen.getByText('Enter your password')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([path]) => path === '/api/auth/login')).toBe(false);
  });

  it('accepts only a 6-digit code', async () => {
    const user = userEvent.setup();
    backend({});
    renderRoute('/login');

    await enterPassword(user);
    await user.type(await screen.findByLabelText('Authenticator code'), '12a4');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(
      await screen.findByText('Enter the 6-digit code from your authenticator app'),
    ).toBeInTheDocument();
  });

  it('returns to the page asked for before logging in', async () => {
    const user = userEvent.setup();
    backend({ staff: staffMember('ADMIN') });
    const { router } = renderRoute('/login?next=%2Fadmin%3Ftab%3Dstaff');

    await enterPassword(user);
    await user.type(await screen.findByLabelText('Authenticator code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    await screen.findByRole('heading', { name: 'Administration' });
    expect(router.state.location.pathname + router.state.location.search).toBe('/admin?tab=staff');
  });

  it('ignores a return address on another site', async () => {
    const user = userEvent.setup();
    backend({ staff: staffMember('CASHIER') });
    const { router } = renderRoute('/login?next=%2F%2Fevil.example%2F');

    await enterPassword(user);
    await user.type(await screen.findByLabelText('Authenticator code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    await screen.findByRole('heading', { name: 'Billing' });
    expect(router.state.location.pathname).toBe('/billing');
  });

  it('says why after an idle logout', async () => {
    backend({});

    renderRoute('/login?reason=idle');

    expect(
      await screen.findByText('You were logged out after 10 minutes without activity.'),
    ).toBeInTheDocument();
  });
});
