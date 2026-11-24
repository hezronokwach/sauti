import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { PreRegistrationLookup } from '../reception/PreRegistrationLookup';
import type { PreFill } from '../reception/PreRegistrationLookup';
import { RegisterPatientForm } from '../reception/RegisterPatientForm';
import { PreRegisterPage } from './PreRegisterPage';

describe('PreRegisterPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  function fill() {
    fireEvent.change(screen.getByLabelText('Full name / Jina kamili'), {
      target: { value: 'Achieng Otieno' },
    });
    fireEvent.change(screen.getByLabelText('Date of birth / Tarehe ya kuzaliwa'), {
      target: { value: '1990-02-03' },
    });
    fireEvent.change(screen.getByLabelText('Sex / Jinsia'), { target: { value: 'FEMALE' } });
    fireEvent.change(screen.getByLabelText('Mobile number / Nambari ya simu'), {
      target: { value: '0712 345 678' },
    });
  }

  it('says what is kept and for how long', () => {
    mockApi({});
    renderWithQuery(<PreRegisterPage />);

    expect(screen.getByText(/deleted at the end of the day/)).toBeInTheDocument();
  });

  it('sends the form and shows only the ticket code it gets back', async () => {
    const sent: unknown[] = [];
    mockApi({
      'POST /api/public/pre-registrations': (init) => {
        sent.push(bodyOf(init));
        return jsonResponse(201, { ticketCode: 'P-004' });
      },
    });
    renderWithQuery(<PreRegisterPage />);

    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Get my ticket / Pata tiketi' }));

    expect(await screen.findByText('P-004')).toBeInTheDocument();
    expect(sent).toEqual([
      {
        fullName: 'Achieng Otieno',
        dateOfBirth: '1990-02-03',
        sex: 'FEMALE',
        phone: '0712 345 678',
        preferredLanguage: 'SWAHILI',
      },
    ]);
  });

  it('checks the phone number before sending', async () => {
    const fetchMock = mockApi({});
    renderWithQuery(<PreRegisterPage />);

    fill();
    fireEvent.change(screen.getByLabelText('Mobile number / Nambari ya simu'), {
      target: { value: '12345' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Get my ticket / Pata tiketi' }));

    expect(
      await screen.findByText('Use a Kenyan mobile number, for example 0712 345 678'),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows a rate limit plainly', async () => {
    mockApi({
      'POST /api/public/pre-registrations': () =>
        jsonResponse(429, {
          code: 'RATE_LIMITED',
          message: 'Too many pre-registrations; please register at the desk',
          fieldErrors: [],
          requestId: null,
        }),
    });
    renderWithQuery(<PreRegisterPage />);

    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Get my ticket / Pata tiketi' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('please register at the desk');
  });
});

describe('Opening a pre-registration at the desk', () => {
  it('loads the entry by the called ticket code', async () => {
    mockApi({
      'GET /api/pre-registrations/P-004': () =>
        jsonResponse(200, {
          id: 'pre-1',
          ticketId: 't-1',
          fullName: 'Achieng Otieno',
          dateOfBirth: '1990-02-03',
          sex: 'FEMALE',
          phone: '254712345678',
          reasonForVisit: 'Homa',
          preferredLanguage: 'SWAHILI',
        }),
    });
    const onLoaded = vi.fn();
    renderWithQuery(<PreRegistrationLookup ticketCode="P-004" onLoaded={onLoaded} />);

    expect(screen.getByLabelText('Pre-registration ticket')).toHaveValue('P-004');
    fireEvent.click(screen.getByRole('button', { name: 'Open' }));

    await vi.waitFor(() => {
      expect(onLoaded).toHaveBeenCalled();
    });
    const loaded = onLoaded.mock.calls[0]?.[0] as PreFill;
    expect(loaded.id).toBe('pre-1');
    expect(loaded.reasonForVisit).toBe('Homa');
    expect(loaded.values).toMatchObject({ fullName: 'Achieng Otieno', sex: 'FEMALE' });
  });

  it('marks the fields the patient typed on the registration form', () => {
    mockApi({});
    renderWithQuery(
      <RegisterPatientForm
        onPatient={vi.fn()}
        initial={{ fullName: 'Achieng Otieno', sex: 'FEMALE' }}
        patientEntered={['fullName', 'sex']}
      />,
    );

    expect(screen.getByLabelText(/Full name/)).toHaveValue('Achieng Otieno');
    expect(screen.getByLabelText(/^Sex/)).toHaveValue('FEMALE');
    expect(screen.getAllByText('patient-entered, check with the patient')).toHaveLength(2);
  });
});
