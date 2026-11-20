import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import type { PatientDetailsResponse } from '../patients/types';
import { focusField } from '../recorder/activeRecorder';
import { ReasonConfirm } from './ReasonConfirm';
import { VoiceFields } from './VoiceFields';
import type { FieldDraftResponse } from './api';

vi.mock('../recorder/activeRecorder', () => ({ focusField: vi.fn() }));
vi.mock('../transcript/playAudio', () => ({ playAt: vi.fn(() => Promise.resolve(null)) }));

const PATIENT: PatientDetailsResponse = {
  id: 'p-1',
  patientNumber: 'SH-000001',
  fullName: 'Voice Patient',
  sex: 'FEMALE',
  dateOfBirth: '1990-01-01',
  ageYears: 36,
  phone: null,
  nationalId: null,
  shaNumber: null,
  residence: null,
  nextOfKinName: null,
  nextOfKinPhone: null,
  preferredLanguage: 'SWAHILI',
  payerType: 'CASH',
};

function draft(overrides: Partial<FieldDraftResponse>): FieldDraftResponse {
  return {
    id: 'd-1',
    field: 'RESIDENCE',
    value: 'Kibera, Nairobi',
    quote: 'naishi Kibera hapa Nairobi',
    sessionId: 's-1',
    startMs: 2500,
    status: 'PENDING',
    ...overrides,
  };
}

describe('VoiceFields', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('tells the recorder which field reception is on', async () => {
    mockApi({
      'GET /api/visits/v-1/reason': () =>
        jsonResponse(200, { reasonForVisit: null, confirmed: false }),
      'GET /api/visits/v-1/field-drafts': () => jsonResponse(200, []),
    });
    renderWithQuery(<VoiceFields visitId="v-1" patient={PATIENT} />);

    fireEvent.focus(await screen.findByLabelText('Residence'));
    fireEvent.focus(screen.getByLabelText('Next of kin name'));

    expect(focusField).toHaveBeenCalledWith('RESIDENCE');
    expect(focusField).toHaveBeenCalledWith('NEXT_OF_KIN_NAME');
  });

  it('shows a draft greyed with its quote, and saving it goes through the normal form', async () => {
    const calls: string[] = [];
    const contact: unknown[] = [];
    mockApi({
      'GET /api/visits/v-1/reason': () =>
        jsonResponse(200, { reasonForVisit: null, confirmed: false }),
      'GET /api/visits/v-1/field-drafts': () => jsonResponse(200, [draft({})]),
      'PUT /api/patients/p-1/contact': (init) => {
        calls.push('save');
        contact.push(bodyOf(init));
        return jsonResponse(200, { ...PATIENT, residence: 'Kibera, Nairobi' });
      },
      'POST /api/visits/v-1/field-drafts/d-1/confirm': () => {
        calls.push('confirm');
        return jsonResponse(200, draft({ status: 'CONFIRMED' }));
      },
    });
    renderWithQuery(<VoiceFields visitId="v-1" patient={PATIENT} />);

    expect(await screen.findByText('Kibera, Nairobi')).toBeInTheDocument();
    expect(screen.getByText(/naishi Kibera hapa Nairobi/)).toBeInTheDocument();
    expect(screen.getByLabelText('Residence')).toHaveValue('');

    fireEvent.click(screen.getByRole('button', { name: 'Use this' }));
    expect(screen.getByLabelText('Residence')).toHaveValue('Kibera, Nairobi');
    fireEvent.click(screen.getAllByRole('button', { name: 'Save' })[1] as HTMLElement);

    await vi.waitFor(() => {
      expect(calls).toEqual(['save', 'confirm']);
    });
    expect(contact).toEqual([{ residence: 'Kibera, Nairobi', nextOfKinName: '' }]);
  });

  it('dismisses a wrong draft', async () => {
    const dismissed: string[] = [];
    mockApi({
      'GET /api/visits/v-1/reason': () =>
        jsonResponse(200, { reasonForVisit: null, confirmed: false }),
      'GET /api/visits/v-1/field-drafts': () =>
        jsonResponse(200, [draft({ field: 'NEXT_OF_KIN_NAME', value: 'Otieno' })]),
      'POST /api/visits/v-1/field-drafts/d-1/dismiss': () => {
        dismissed.push('d-1');
        return jsonResponse(200, draft({ status: 'DISMISSED' }));
      },
    });
    renderWithQuery(<VoiceFields visitId="v-1" patient={PATIENT} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Dismiss' }));

    await vi.waitFor(() => {
      expect(dismissed).toEqual(['d-1']);
    });
  });

  it('shows why a save was refused', async () => {
    mockApi({
      'GET /api/visits/v-1/reason': () =>
        jsonResponse(200, { reasonForVisit: null, confirmed: false }),
      'GET /api/visits/v-1/field-drafts': () => jsonResponse(200, []),
    });
    renderWithQuery(<VoiceFields visitId="v-1" patient={PATIENT} />);

    fireEvent.click((await screen.findAllByRole('button', { name: 'Save' }))[0] as HTMLElement);

    expect(await screen.findByText('Write the reason for visit')).toBeInTheDocument();
  });
});

describe('ReasonConfirm', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('lets the triage nurse confirm the reason with the patient', async () => {
    let confirmed = false;
    mockApi({
      'GET /api/visits/v-1/reason': () =>
        jsonResponse(200, { reasonForVisit: 'Homa siku tatu', confirmed }),
      'POST /api/visits/v-1/reason/confirm': () => {
        confirmed = true;
        return jsonResponse(200, { reasonForVisit: 'Homa siku tatu', confirmed: true });
      },
    });
    renderWithQuery(<ReasonConfirm visitId="v-1" />);

    expect(await screen.findByText('Homa siku tatu')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm with the patient' }));

    expect(await screen.findByText('Confirmed with the patient')).toBeInTheDocument();
  });

  it('lets the nurse correct the reason', async () => {
    const saved: unknown[] = [];
    mockApi({
      'GET /api/visits/v-1/reason': () =>
        jsonResponse(200, { reasonForVisit: 'Homa', confirmed: false }),
      'PUT /api/visits/v-1/reason': (init) => {
        saved.push(bodyOf(init));
        return jsonResponse(200, { reasonForVisit: 'Homa na kikohozi', confirmed: false });
      },
    });
    renderWithQuery(<ReasonConfirm visitId="v-1" />);

    fireEvent.click(await screen.findByRole('button', { name: 'Correct it' }));
    fireEvent.change(screen.getByLabelText('Reason, as the patient says it'), {
      target: { value: 'Homa na kikohozi' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await vi.waitFor(() => {
      expect(saved).toEqual([{ reasonForVisit: 'Homa na kikohozi' }]);
    });
  });
});
