import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { RegisterPatientForm } from '../reception/RegisterPatientForm';
import { EligibilityBadge } from './EligibilityBadge';

function lookUp(id: string) {
  fireEvent.change(screen.getByLabelText(/^National ID/), { target: { value: id } });
  fireEvent.click(screen.getByRole('button', { name: 'Look up in the registry' }));
}

describe('Registry lookup on the registration form', () => {
  it('fills name, date of birth and sex from a registry hit', async () => {
    mockApi({
      'GET /api/registry/lookup': () =>
        jsonResponse(200, {
          found: true,
          fullName: 'Wanjiru Njeri Kamau',
          dateOfBirth: '1978-11-23',
          sex: 'FEMALE',
        }),
    });
    renderWithQuery(<RegisterPatientForm onPatient={vi.fn()} />);

    lookUp('30000003');

    expect(
      await screen.findByText('Found in the registry. Check the details with the patient.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/^Full name/)).toHaveValue('Wanjiru Njeri Kamau');
    expect(screen.getByLabelText(/^Date of birth/)).toHaveValue('1978-11-23');
    expect(screen.getByLabelText(/^Sex/)).toHaveValue('FEMALE');
    expect(screen.getAllByText('from the registry')).toHaveLength(3);
  });

  it('falls back to typing when the registry has no such person', async () => {
    mockApi({
      'GET /api/registry/lookup': () =>
        jsonResponse(200, { found: false, fullName: null, dateOfBirth: null, sex: null }),
    });
    renderWithQuery(<RegisterPatientForm onPatient={vi.fn()} />);

    lookUp('31234567');

    expect(await screen.findByText('Not in the registry: type the details.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Full name/)).toHaveValue('');
  });

  it('says so when the registry is not answering', async () => {
    mockApi({
      'GET /api/registry/lookup': () =>
        jsonResponse(503, {
          code: 'SERVICE_UNAVAILABLE',
          message: 'The registry is not answering; enter the details by hand',
          fieldErrors: [],
          requestId: null,
        }),
    });
    renderWithQuery(<RegisterPatientForm onPatient={vi.fn()} />);

    lookUp('39999999');

    expect(
      await screen.findByText('The registry is not answering; enter the details by hand'),
    ).toBeInTheDocument();
  });

  it('asks for the ID before looking up', () => {
    const fetchMock = mockApi({});
    renderWithQuery(<RegisterPatientForm onPatient={vi.fn()} />);

    lookUp('12');

    expect(screen.getByText('Type the national ID (6 to 9 digits) first.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('EligibilityBadge', () => {
  it.each([
    ['ELIGIBLE', 'SHA: eligible'],
    ['INELIGIBLE', 'SHA: not eligible'],
    ['NOT_FOUND', 'SHA: not found'],
    ['PENDING', 'SHA: checking...'],
    ['UNAVAILABLE', 'SHA: service not answering, trying again'],
    ['NONE', 'SHA: not checked (no national ID)'],
  ])('shows %s', async (status, text) => {
    mockApi({
      'GET /api/patients/p-1/eligibility': () => jsonResponse(200, { status, checkedAt: null }),
    });
    renderWithQuery(<EligibilityBadge patientId="p-1" />);

    expect(await screen.findByText(text)).toBeInTheDocument();
  });
});
