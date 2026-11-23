import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { PrescriptionPanel } from './PrescriptionPanel';
import type { Prescription, SafetyAlert } from './types';

const allergyAlert: SafetyAlert = {
  id: 'a1',
  itemId: 'i1',
  rule: 'R3',
  level: 'BLOCKING',
  message: 'Allergy on file: Penicillin. Amoxicillin is in the same group.',
  status: 'OPEN',
  overrideReason: null,
  canOverride: true,
};

const prescription = (
  alerts: SafetyAlert[],
  status: Prescription['status'] = 'DRAFT',
): Prescription => ({
  id: 'p1',
  visitId: 'v1',
  status,
  rulesVersion: 'medicines-v2',
  revision: 2,
  signedAt: status === 'SIGNED' ? '2026-11-23T10:00:00Z' : null,
  alerts,
  items: [
    {
      id: 'i1',
      medicine: {
        id: 'm1',
        name: 'Amoxicillin',
        form: 'Capsule',
        strength: '500 mg',
        routes: ['ORAL'],
        priceKes: 15,
      },
      doseValue: 500,
      doseUnit: 'MG',
      frequency: 'TDS',
      durationDays: 5,
      route: 'ORAL',
      quantity: 15,
      instructions: null,
    },
  ],
});

describe('SafetyAlerts in the prescription panel', () => {
  it('will not offer signing while an alert is open', async () => {
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription([allergyAlert])),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    expect(await screen.findByText(/Allergy on file: Penicillin/)).toBeInTheDocument();
    expect(screen.getByText('Deal with every safety alert before signing.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign prescription' })).not.toBeInTheDocument();
  });

  it('overrides only with a long enough reason and a code', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription([allergyAlert])),
      'POST /api/visits/v1/prescription/alerts/a1/override': (init) => {
        sent = bodyOf(init);
        return jsonResponse(
          200,
          prescription([
            { ...allergyAlert, status: 'OVERRIDDEN', overrideReason: 'Tolerated it before' },
          ]),
        );
      },
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.click(await screen.findByRole('button', { name: 'Override' }));
    const confirm = screen.getByRole('button', { name: 'Confirm override' });
    await user.type(screen.getByLabelText(/Why is it safe/), 'short');
    await user.type(screen.getByLabelText('Authenticator code'), '123456');
    expect(confirm).toBeDisabled();

    await user.clear(screen.getByLabelText(/Why is it safe/));
    await user.type(screen.getByLabelText(/Why is it safe/), 'Tolerated it before');
    await user.click(confirm);

    expect(await screen.findByText('Overridden: Tolerated it before')).toBeInTheDocument();
    expect(sent).toEqual({ reason: 'Tolerated it before', code: '123456' });
  });

  it('offers no override for missing details', async () => {
    mockApi({
      'GET /api/visits/v1/prescription': () =>
        jsonResponse(
          200,
          prescription([
            {
              ...allergyAlert,
              rule: 'R1',
              message: 'Amoxicillin is missing: route.',
              canOverride: false,
            },
          ]),
        ),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    expect(
      await screen.findByText('Fill in the missing details to clear this.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Override' })).not.toBeInTheDocument();
  });

  it('acknowledges a review alert', async () => {
    const user = userEvent.setup();
    const review: SafetyAlert = {
      ...allergyAlert,
      id: 'a2',
      rule: 'R7',
      level: 'REVIEW',
      message: 'Allergies have not been recorded for this patient. Ask before signing.',
      canOverride: false,
    };
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription([review])),
      'POST /api/visits/v1/prescription/alerts/a2/acknowledge': () =>
        jsonResponse(200, prescription([{ ...review, status: 'ACKNOWLEDGED' }])),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.click(await screen.findByRole('button', { name: 'Acknowledge' }));

    expect(await screen.findByText('Acknowledged')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign prescription' })).toBeInTheDocument();
  });

  it('signs with a code, then allows no edits', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription([])),
      'POST /api/visits/v1/prescription/sign': (init) => {
        sent = bodyOf(init);
        return jsonResponse(200, prescription([], 'SIGNED'));
      },
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.type(await screen.findByLabelText('Authenticator code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Sign prescription' }));

    expect(await screen.findByText('Signed. This prescription is final.')).toBeInTheDocument();
    expect(sent).toEqual({ revision: 2, code: '123456' });
    expect(screen.queryByRole('button', { name: 'Change Amoxicillin' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel prescription' })).toBeInTheDocument();
  });

  it('shows why signing was refused and clears the code', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription([])),
      'POST /api/visits/v1/prescription/sign': () =>
        jsonResponse(403, apiError('FORBIDDEN', 'The authenticator code is not valid')),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    const code = await screen.findByLabelText('Authenticator code');
    await user.type(code, '000000');
    await user.click(screen.getByRole('button', { name: 'Sign prescription' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('code is not valid');
    expect(screen.getByLabelText('Authenticator code')).toHaveValue('');
  });
});
