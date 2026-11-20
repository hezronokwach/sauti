import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderRoute } from '../../test/render';
import type { MockRoute } from '../../test/render';
import { healthUp, meReturns, staffMember } from '../../test/staff';
import type { PatientSummaryResponse } from '../patients/types';

const ACHIENG: PatientSummaryResponse = {
  id: 'patient-1',
  patientNumber: 'SH-000042',
  fullName: 'Achieng Odhiambo',
  sex: 'FEMALE',
  dateOfBirth: '1990-05-17',
  ageYears: 35,
};

function duplicate(confirmable: boolean) {
  return jsonResponse(409, {
    code: 'DUPLICATE_PATIENT',
    message: 'A patient with this phone number or national ID is already registered',
    fieldErrors: [],
    requestId: null,
    matches: [ACHIENG],
    confirmable,
  });
}

function backend(extra: Record<string, MockRoute> = {}) {
  return mockApi({
    'GET /api/auth/me': meReturns(() => staffMember('RECEPTIONIST')),
    'GET /actuator/health': healthUp,
    'GET /api/visits': () => jsonResponse(200, []),
    'GET /api/patients': () => jsonResponse(200, [ACHIENG]),
    'POST /api/visits': () => jsonResponse(201, { id: 'visit-9', queueNumber: 14 }),
    'POST /api/visits/visit-9/status': () => jsonResponse(200, { id: 'visit-9' }),
    ...extra,
  });
}

async function fillRegistration(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Register a new patient' }));
  await user.type(screen.getByLabelText('Full name'), 'Baby Achieng');
  await user.type(screen.getByLabelText('Date of birth'), '2024-02-10');
  await user.selectOptions(screen.getByLabelText('Sex'), 'FEMALE');
  await user.type(screen.getByLabelText('Phone (optional)'), '0712 345 678');
}

describe('ReceptionPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('finds a patient, opens a visit and sends it to triage', async () => {
    const user = userEvent.setup();
    const fetchMock = backend();
    renderRoute('/reception');

    await user.type(await screen.findByLabelText(/Name, phone, national ID/), 'achi');
    await user.click(await screen.findByRole('button', { name: 'Select Achieng Odhiambo' }));
    await user.type(screen.getByLabelText('Reason for the visit (optional)'), 'Homa');
    await user.click(screen.getByRole('button', { name: 'Open visit and send to triage' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Queue number 14');
    const open = fetchMock.mock.calls.find(
      ([path, init]) => path === '/api/visits' && init?.method === 'POST',
    );
    expect(bodyOf(open?.[1])).toEqual({ patientId: 'patient-1', reasonForVisit: 'Homa' });
    const move = fetchMock.mock.calls.find(([path]) => path === '/api/visits/visit-9/status');
    expect(bodyOf(move?.[1])).toEqual({ to: 'WAITING_TRIAGE' });
  });

  it('registers a new patient and continues to the visit', async () => {
    const user = userEvent.setup();
    const fetchMock = backend({
      'POST /api/patients': () => jsonResponse(201, { ...ACHIENG, fullName: 'Baby Achieng' }),
    });
    renderRoute('/reception');

    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByRole('heading', { name: 'Open a visit' })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([path]) => path === '/api/patients');
    expect(bodyOf(call?.[1])).toMatchObject({
      fullName: 'Baby Achieng',
      dateOfBirth: '2024-02-10',
      sex: 'FEMALE',
      phone: '0712 345 678',
      payerType: 'CASH',
      confirmNewPatient: false,
    });
  });

  it('shows the matches for a shared phone and can register a different person', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    const fetchMock = backend({
      'POST /api/patients': () => {
        attempts += 1;
        return attempts === 1
          ? duplicate(true)
          : jsonResponse(201, { ...ACHIENG, id: 'patient-2', fullName: 'Baby Achieng' });
      },
    });
    renderRoute('/reception');

    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText('This patient may already be registered.')).toBeInTheDocument();
    expect(screen.getByText(/Achieng Odhiambo \(SH-000042/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Register as a different person' }));

    expect(await screen.findByRole('heading', { name: 'Open a visit' })).toBeInTheDocument();
    const last = fetchMock.mock.calls.filter(([path]) => path === '/api/patients').at(-1);
    expect(bodyOf(last?.[1])).toMatchObject({ confirmNewPatient: true });
  });

  it('only offers the existing patient when the national ID matches', async () => {
    const user = userEvent.setup();
    backend({ 'POST /api/patients': () => duplicate(false) });
    renderRoute('/reception');

    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText(/same national ID/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Register as a different person' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Use this patient' }));
    expect(await screen.findByText('Achieng Odhiambo')).toBeInTheDocument();
  });

  it('asks for the SHA number when SHA pays', async () => {
    const user = userEvent.setup();
    backend();
    renderRoute('/reception');

    await fillRegistration(user);
    await user.selectOptions(screen.getByLabelText('Who pays'), 'SHA');
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText('Enter the SHA number when SHA pays')).toBeInTheDocument();
  });
});
