import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { AllergyList } from './AllergyList';
import { allergyState } from './api';
import type { AllergyResponse } from './types';
import { VitalsForm } from './VitalsForm';

async function fillNormal(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Temperature (°C)'), '37.8');
  await user.type(screen.getByLabelText('Pulse (beats a minute)'), '92');
  await user.type(screen.getByLabelText('Breathing rate (breaths a minute)'), '18');
  await user.type(screen.getByLabelText('Blood pressure, systolic (mmHg)'), '124');
  await user.type(screen.getByLabelText('Blood pressure, diastolic (mmHg)'), '82');
  await user.type(screen.getByLabelText('Oxygen saturation (%)'), '97');
}

function allergy(overrides: Partial<AllergyResponse>): AllergyResponse {
  return {
    id: crypto.randomUUID(),
    kind: 'ALLERGY',
    substance: 'Penicillin',
    substanceGroup: null,
    reaction: 'Rash',
    status: 'ACTIVE',
    recordedAt: '2026-01-01T08:00:00Z',
    enteredInErrorReason: null,
    ...overrides,
  };
}

describe('VitalsForm', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('reports the save even when the page replaced the form first', async () => {
    let answer: (response: Response) => void = () => undefined;
    mockApi({
      'POST /api/visits/visit-1/vitals': () =>
        new Promise<Response>((resolve) => {
          answer = resolve;
        }),
    });
    const onRecorded = vi.fn();
    const user = userEvent.setup({ delay: null });
    const view = renderWithQuery(<VitalsForm visitId="visit-1" onRecorded={onRecorded} />);

    await fillNormal(user);
    await user.click(screen.getByRole('button', { name: 'Save and send to doctor' }));
    // The visit page swaps the form for the summary as soon as the visit moves on.
    view.unmount();
    answer(jsonResponse(201, {}));

    await vi.waitFor(() => {
      expect(onRecorded).toHaveBeenCalled();
    });
  });

  it('sends plausible readings and reports that the patient went to the doctor', async () => {
    const sent: unknown[] = [];
    mockApi({
      'POST /api/visits/visit-1/vitals': (init) => {
        sent.push(bodyOf(init));
        return jsonResponse(201, {});
      },
    });
    const onRecorded = vi.fn();
    const user = userEvent.setup({ delay: null });
    renderWithQuery(<VitalsForm visitId="visit-1" onRecorded={onRecorded} />);

    await fillNormal(user);
    await user.type(screen.getByLabelText('Weight (kg, optional)'), '64.5');
    await user.click(screen.getByRole('button', { name: 'Save and send to doctor' }));

    await vi.waitFor(() => {
      expect(onRecorded).toHaveBeenCalled();
    });
    expect(sent).toEqual([
      {
        temperatureC: 37.8,
        pulseBpm: 92,
        respiratoryRate: 18,
        systolicMmhg: 124,
        diastolicMmhg: 82,
        spo2Percent: 97,
        weightKg: 64.5,
      },
    ]);
  });

  it('cannot send the patient on until allergies are answered, and says why', async () => {
    const fetchMock = mockApi({});
    const user = userEvent.setup({ delay: null });
    renderWithQuery(
      <VitalsForm visitId="visit-1" onRecorded={vi.fn()} blockedBy="Ask about allergies first." />,
    );

    await fillNormal(user);
    const send = screen.getByRole('button', { name: 'Save and send to doctor' });

    expect(send).toBeDisabled();
    expect(send).toHaveAccessibleDescription('Ask about allergies first.');
    await user.click(send);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses impossible values field by field without sending anything', async () => {
    const fetchMock = mockApi({});
    const user = userEvent.setup({ delay: null });
    renderWithQuery(<VitalsForm visitId="visit-1" onRecorded={vi.fn()} />);

    await fillNormal(user);
    await user.clear(screen.getByLabelText('Temperature (°C)'));
    await user.type(screen.getByLabelText('Temperature (°C)'), '47');
    await user.clear(screen.getByLabelText('Pulse (beats a minute)'));
    await user.type(screen.getByLabelText('Pulse (beats a minute)'), 'abc');
    await user.clear(screen.getByLabelText('Blood pressure, diastolic (mmHg)'));
    await user.type(screen.getByLabelText('Blood pressure, diastolic (mmHg)'), '130');
    await user.click(screen.getByRole('button', { name: 'Save and send to doctor' }));

    expect(await screen.findByText('Temperature must be between 30 and 45 °C')).toBeInTheDocument();
    expect(screen.getByText('Pulse must be a number')).toBeInTheDocument();
    expect(screen.getByText('Diastolic must be lower than systolic')).toBeInTheDocument();
    expect(screen.getByLabelText('Temperature (°C)')).toHaveAttribute('aria-invalid', 'true');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('asks for every required reading', async () => {
    mockApi({});
    const user = userEvent.setup({ delay: null });
    renderWithQuery(<VitalsForm visitId="visit-1" onRecorded={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Save and send to doctor' }));

    expect(await screen.findByText('Enter the temperature')).toBeInTheDocument();
    expect(screen.getByText('Enter the oxygen saturation')).toBeInTheDocument();
  });

  it("shows the backend's refusal", async () => {
    mockApi({
      'POST /api/visits/visit-1/vitals': () =>
        jsonResponse(409, {
          code: 'CONFLICT',
          message: 'Vitals are taken while the patient is in triage',
          fieldErrors: [],
          requestId: null,
        }),
    });
    const user = userEvent.setup({ delay: null });
    renderWithQuery(<VitalsForm visitId="visit-1" onRecorded={vi.fn()} />);

    await fillNormal(user);
    await user.click(screen.getByRole('button', { name: 'Save and send to doctor' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Vitals are taken while the patient is in triage',
    );
  });
});

describe('AllergyList', () => {
  it('lists active allergies and leaves out entries made in error', async () => {
    mockApi({
      'GET /api/patients/p-1/allergies': () =>
        jsonResponse(200, [
          allergy({}),
          allergy({ substance: 'Aspirin', status: 'ENTERED_IN_ERROR' }),
        ]),
    });
    renderWithQuery(<AllergyList patientId="p-1" />);

    expect(await screen.findByText('Penicillin')).toBeInTheDocument();
    expect(screen.queryByText('Aspirin')).not.toBeInTheDocument();
  });

  it('says when nobody has asked about allergies yet', async () => {
    mockApi({ 'GET /api/patients/p-1/allergies': () => jsonResponse(200, []) });
    renderWithQuery(<AllergyList patientId="p-1" />);

    expect(
      await screen.findByText('Allergies not yet asked. Ask the patient.'),
    ).toBeInTheDocument();
  });

  it('shows no known allergies', async () => {
    mockApi({
      'GET /api/patients/p-1/allergies': () =>
        jsonResponse(200, [
          allergy({ kind: 'NO_KNOWN_ALLERGIES', substance: null, reaction: null }),
        ]),
    });
    renderWithQuery(<AllergyList patientId="p-1" />);

    expect(await screen.findByText('No known allergies.')).toBeInTheDocument();
  });

  it('records that the patient has no known allergies', async () => {
    const entries: AllergyResponse[] = [];
    const sent: unknown[] = [];
    mockApi({
      'GET /api/patients/p-1/allergies': () => jsonResponse(200, entries),
      'POST /api/patients/p-1/allergies': (init) => {
        sent.push(bodyOf(init));
        entries.push(allergy({ kind: 'NO_KNOWN_ALLERGIES', substance: null, reaction: null }));
        return jsonResponse(200, entries[0]);
      },
    });
    const user = userEvent.setup({ delay: null });
    renderWithQuery(<AllergyList patientId="p-1" editable />);

    await user.click(await screen.findByRole('button', { name: 'No known allergies' }));

    expect(await screen.findByText('No known allergies.')).toBeInTheDocument();
    expect(sent).toEqual([{ noKnownAllergies: true }]);
    expect(screen.queryByRole('button', { name: 'No known allergies' })).not.toBeInTheDocument();
  });

  it('adds an allergy with its reaction, and asks for the substance first', async () => {
    const entries: AllergyResponse[] = [];
    const sent: unknown[] = [];
    mockApi({
      'GET /api/patients/p-1/allergies': () => jsonResponse(200, entries),
      'POST /api/patients/p-1/allergies': (init) => {
        sent.push(bodyOf(init));
        entries.push(allergy({ substance: 'Sulfa', reaction: 'Swelling' }));
        return jsonResponse(200, entries[0]);
      },
    });
    const user = userEvent.setup({ delay: null });
    renderWithQuery(<AllergyList patientId="p-1" editable />);

    await user.click(await screen.findByRole('button', { name: 'Add allergy' }));
    expect(screen.getByText('Write what the patient reacts to')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Reacts to'), 'Sulfa');
    await user.type(screen.getByLabelText('Reaction (optional)'), 'Swelling');
    await user.click(screen.getByRole('button', { name: 'Add allergy' }));

    expect(await screen.findByText('Sulfa')).toBeInTheDocument();
    expect(sent).toEqual([{ substance: 'Sulfa', reaction: 'Swelling' }]);
  });

  it('works out the banner flag from the entries', () => {
    expect(allergyState(undefined)).toBe('loading');
    expect(allergyState([])).toBe('not-asked');
    expect(allergyState([allergy({ status: 'ENTERED_IN_ERROR' })])).toBe('not-asked');
    expect(allergyState([allergy({ kind: 'NO_KNOWN_ALLERGIES', substance: null })])).toBe('none');
    expect(allergyState([allergy({})])).toBe('allergic');
  });
});
