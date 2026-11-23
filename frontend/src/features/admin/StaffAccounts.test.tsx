import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi } from '../../test/render';
import { StaffAccounts } from './StaffAccounts';
import type { StaffSummary } from './types';

const person = (overrides: Partial<StaffSummary>): StaffSummary => ({
  id: 'b1',
  fullName: 'Asha Mwangi',
  email: 'asha@example.test',
  phone: null,
  role: 'RECEPTIONIST',
  licenceNumber: null,
  status: 'ACTIVE',
  authenticatorSetUp: true,
  mustChangePassword: false,
  lockedUntil: null,
  ...overrides,
});
const admin = person({ id: 'a1', fullName: 'Admin One', role: 'ADMIN' });
const me = { id: 'a1', fullName: 'Admin One', email: 'a@example.test', role: 'ADMIN' };

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <StaffAccounts />
    </QueryClientProvider>,
  );
}

describe('StaffAccounts', () => {
  it('shows each account state and offers no way to disable yourself', async () => {
    mockApi({
      'GET /api/auth/me': () => jsonResponse(200, { ...me, mustChangePassword: false }),
      'GET /api/admin/staff': () =>
        jsonResponse(200, [
          admin,
          person({ mustChangePassword: true }),
          person({ id: 'b2', fullName: 'Gone', status: 'DISABLED' }),
        ]),
    });
    show();

    expect(await screen.findByText('Waiting for first login')).toBeInTheDocument();
    expect(screen.getByText('Disabled')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Disable Asha Mwangi' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disable Admin One' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disable Gone' })).not.toBeInTheDocument();
  });

  it('disables an account only after confirming', async () => {
    const user = userEvent.setup();
    const api = mockApi({
      'GET /api/auth/me': () => jsonResponse(200, { ...me, mustChangePassword: false }),
      'GET /api/admin/staff': () => jsonResponse(200, [admin, person({})]),
      'POST /api/admin/staff/b1/disable': () => jsonResponse(200, person({ status: 'DISABLED' })),
    });
    show();

    await user.click(await screen.findByRole('button', { name: 'Disable Asha Mwangi' }));
    const disableCalls = () =>
      api.mock.calls.filter(([url]) => typeof url === 'string' && url.endsWith('/b1/disable'));
    expect(disableCalls()).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Yes, disable Asha Mwangi' }));
    await screen.findByRole('button', { name: 'Disable Asha Mwangi' });
    expect(disableCalls()).toHaveLength(1);
  });

  it('needs a licence for a doctor, then shows the one-time password until dismissed', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/auth/me': () => jsonResponse(200, { ...me, mustChangePassword: false }),
      'GET /api/admin/staff': () => jsonResponse(200, [admin]),
      'POST /api/admin/staff': (init) => {
        sent = bodyOf(init);
        return jsonResponse(201, {
          staff: person({ fullName: 'Dr Otieno', role: 'DOCTOR' }),
          oneTimePassword: 'test-one-time-value',
        });
      },
    });
    show();

    await user.type(await screen.findByLabelText('Full name'), 'Dr Otieno');
    await user.type(screen.getByLabelText('Email'), 'Otieno@Example.test');
    await user.selectOptions(screen.getByLabelText('Role'), 'DOCTOR');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Doctors and nurses need a licence number')).toBeInTheDocument();
    expect(sent).toBeUndefined();

    await user.type(screen.getByLabelText('Licence number'), 'KMPDC-1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('test-one-time-value')).toBeInTheDocument();
    expect(sent).toEqual({
      fullName: 'Dr Otieno',
      email: 'otieno@example.test',
      role: 'DOCTOR',
      licenceNumber: 'KMPDC-1',
    });

    await user.click(screen.getByRole('button', { name: 'I have passed it on' }));
    expect(screen.queryByText('test-one-time-value')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create account' })).toBeInTheDocument();
  });

  it('shows the server error when the email is already used', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/auth/me': () => jsonResponse(200, { ...me, mustChangePassword: false }),
      'GET /api/admin/staff': () => jsonResponse(200, [admin]),
      'POST /api/admin/staff': () =>
        jsonResponse(409, apiError('CONFLICT', 'A staff member with this email already exists')),
    });
    show();

    await user.type(await screen.findByLabelText('Full name'), 'Asha');
    await user.type(screen.getByLabelText('Email'), 'asha@example.test');
    await user.selectOptions(screen.getByLabelText('Role'), 'CASHIER');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('already exists');
  });
});
