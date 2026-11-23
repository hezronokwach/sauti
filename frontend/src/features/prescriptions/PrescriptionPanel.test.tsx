import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiError, bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { PrescriptionPanel } from './PrescriptionPanel';
import type { Medicine, Prescription } from './types';

const amoxicillin: Medicine = {
  id: 'm1',
  name: 'Amoxicillin',
  form: 'Capsule',
  strength: '500 mg',
  routes: ['ORAL'],
  priceKes: 15,
};

const prescription = (revision: number, quantity: number | null): Prescription => ({
  id: 'p1',
  visitId: 'v1',
  status: 'DRAFT',
  rulesVersion: 'medicines-v1',
  revision,
  signedAt: null,
  alerts: [],
  items: [
    {
      id: 'i1',
      medicine: amoxicillin,
      doseValue: 500,
      doseUnit: 'MG',
      frequency: 'TDS',
      durationDays: 5,
      route: 'ORAL',
      quantity,
      instructions: null,
    },
  ],
});

const noPrescription = () => jsonResponse(404, apiError('NOT_FOUND', 'No prescription yet'));

describe('PrescriptionPanel', () => {
  it('finds a medicine and adds it with its details', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/visits/v1/prescription': noPrescription,
      'GET /api/medicines': () => jsonResponse(200, [amoxicillin]),
      'POST /api/visits/v1/prescription/items': (init) => {
        sent = bodyOf(init);
        return jsonResponse(200, prescription(1, 15));
      },
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.type(await screen.findByLabelText('Find a medicine'), 'amox');
    await user.click(await screen.findByRole('button', { name: 'Amoxicillin 500 mg (Capsule)' }));
    await user.type(screen.getByLabelText('Dose'), '500');
    await user.selectOptions(screen.getByLabelText('Unit'), 'MG');
    await user.selectOptions(screen.getByLabelText('Frequency'), 'TDS');
    await user.type(screen.getByLabelText('Days'), '5');
    await user.type(screen.getByLabelText('Quantity to dispense'), '15');
    await user.click(screen.getByRole('button', { name: 'Add to prescription' }));

    const list = await screen.findByRole('list', { name: 'Medicines on this prescription' });
    expect(within(list).getByText(/Quantity: 15/)).toBeInTheDocument();
    expect(sent).toEqual({
      medicineId: 'm1',
      doseValue: 500,
      doseUnit: 'MG',
      frequency: 'TDS',
      durationDays: 5,
      route: 'ORAL',
      quantity: 15,
    });
  });

  it('shows a missing detail instead of hiding it', async () => {
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription(1, null)),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    expect(await screen.findByText(/Quantity: missing/)).toBeInTheDocument();
  });

  it('refuses a dose that is not a positive number before sending', async () => {
    const user = userEvent.setup();
    const api = mockApi({
      'GET /api/visits/v1/prescription': noPrescription,
      'GET /api/medicines': () => jsonResponse(200, [amoxicillin]),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.type(await screen.findByLabelText('Find a medicine'), 'amox');
    await user.click(await screen.findByRole('button', { name: /Amoxicillin/ }));
    await user.type(screen.getByLabelText('Dose'), '0');
    await user.click(screen.getByRole('button', { name: 'Add to prescription' }));

    expect(await screen.findByText(/Enter a dose above 0/)).toBeInTheDocument();
    expect(api.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it('sends the revision it read when changing a line', async () => {
    const user = userEvent.setup();
    let sent: unknown;
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription(3, 15)),
      'PUT /api/visits/v1/prescription/items/i1': (init) => {
        sent = bodyOf(init);
        return jsonResponse(200, prescription(4, 21));
      },
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.click(await screen.findByRole('button', { name: 'Change Amoxicillin' }));
    const quantity = screen.getByLabelText('Quantity to dispense');
    await user.clear(quantity);
    await user.type(quantity, '21');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText(/Quantity: 21/)).toBeInTheDocument();
    expect(sent).toMatchObject({ revision: 3, quantity: 21 });
  });

  it('shows why a save was refused', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/visits/v1/prescription': () => jsonResponse(200, prescription(3, 15)),
      'PUT /api/visits/v1/prescription/items/i1': () =>
        jsonResponse(409, apiError('CONFLICT', 'The prescription was changed elsewhere')),
    });
    renderWithQuery(<PrescriptionPanel visitId="v1" />);

    await user.click(await screen.findByRole('button', { name: 'Change Amoxicillin' }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('changed elsewhere');
  });
});
