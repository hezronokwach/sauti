import { act, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { NoteEditor } from './NoteEditor';
import { PatientHistory } from './PatientHistory';
import { SignedNote } from './SignedNote';
import type { NoteResponse } from './types';
import { AUTOSAVE_MS } from './useNoteDraft';

function note(overrides: Partial<NoteResponse> = {}): NoteResponse {
  return {
    id: 'note-1',
    visitId: 'visit-1',
    patientId: 'patient-1',
    authorId: 'doctor-1',
    status: 'DRAFT',
    version: 0,
    presentingComplaint: null,
    history: null,
    examination: null,
    assessment: null,
    plan: null,
    aiAssisted: false,
    updatedAt: '2026-01-01T08:00:00Z',
    signedAt: null,
    signedBy: null,
    addenda: [],
    ...overrides,
  };
}

function conflict(code: string, message: string) {
  return jsonResponse(409, { code, message, fieldErrors: [], requestId: null });
}

const type = (label: string, value: string) => {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
};

const waitForAutosave = () =>
  act(async () => {
    await vi.advanceTimersByTimeAsync(AUTOSAVE_MS);
  });

describe('NoteEditor', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('saves 2 seconds after typing stops, sending the version it holds', async () => {
    const saves: Record<string, unknown>[] = [];
    let version = 0;
    mockApi({
      'PUT /api/visits/visit-1/note': (init) => {
        saves.push(bodyOf(init) as Record<string, unknown>);
        version += 1;
        return jsonResponse(200, note({ version }));
      },
    });
    renderWithQuery(<NoteEditor visitId="visit-1" initial={null} onSigned={vi.fn()} />);

    type('Assessment', 'Malaria?');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(AUTOSAVE_MS - 100);
    });
    expect(saves).toHaveLength(0);
    expect(screen.getByText('Unsaved changes')).toBeInTheDocument();

    await waitForAutosave();
    expect(saves).toHaveLength(1);
    expect(saves[0]).toMatchObject({ version: null, assessment: 'Malaria?' });
    expect(screen.getByText('All changes saved')).toBeInTheDocument();

    type('Assessment', 'Malaria, uncomplicated');
    await waitForAutosave();
    expect(saves[1]).toMatchObject({ version: 1, assessment: 'Malaria, uncomplicated' });
  });

  it('stops saving on a conflict and reloads the saved note on request', async () => {
    const saves: unknown[] = [];
    mockApi({
      'PUT /api/visits/visit-1/note': (init) => {
        saves.push(bodyOf(init));
        return conflict(
          'STALE_VERSION',
          'This record was changed by someone else; reload and try again',
        );
      },
      'GET /api/visits/visit-1/note': () =>
        jsonResponse(200, note({ version: 4, assessment: 'Saved in the other tab' })),
    });
    renderWithQuery(<NoteEditor visitId="visit-1" initial={note()} onSigned={vi.fn()} />);

    type('Assessment', 'My typing');
    await waitForAutosave();
    expect(screen.getByRole('alert')).toHaveTextContent('changed by someone else');

    type('Assessment', 'More typing');
    await waitForAutosave();
    expect(saves).toHaveLength(1);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Reload the note' }));
      await vi.runOnlyPendingTimersAsync();
    });
    expect(screen.getByLabelText('Assessment')).toHaveValue('Saved in the other tab');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('saves what is on screen, then signs that version with the code', async () => {
    const signs: unknown[] = [];
    mockApi({
      'PUT /api/visits/visit-1/note': () =>
        jsonResponse(200, note({ version: 3, assessment: 'Malaria', plan: 'AL' })),
      'POST /api/visits/visit-1/note/sign': (init) => {
        signs.push(bodyOf(init));
        return jsonResponse(200, note({ status: 'SIGNED', version: 4 }));
      },
    });
    const onSigned = vi.fn();
    renderWithQuery(<NoteEditor visitId="visit-1" initial={note()} onSigned={onSigned} />);

    type('Assessment', 'Malaria');
    type('Plan', 'AL');
    fireEvent.click(screen.getByRole('button', { name: 'Sign...' }));
    type('Authenticator code', '123456');
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Sign note' }));
      await vi.runOnlyPendingTimersAsync();
    });

    expect(signs).toEqual([{ version: 3, code: '123456' }]);
    expect(onSigned).toHaveBeenCalledWith(expect.objectContaining({ status: 'SIGNED' }));
  });

  it("shows the backend's reason when the code is wrong", async () => {
    mockApi({
      'POST /api/visits/visit-1/note/sign': () =>
        jsonResponse(403, {
          code: 'FORBIDDEN',
          message: 'The authenticator code is not valid',
          fieldErrors: [],
          requestId: null,
        }),
    });
    renderWithQuery(
      <NoteEditor
        visitId="visit-1"
        initial={note({ assessment: 'Malaria', plan: 'AL' })}
        onSigned={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Sign...' }));
    type('Authenticator code', '000000');
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Sign note' }));
      await vi.runOnlyPendingTimersAsync();
    });

    expect(screen.getByText('The authenticator code is not valid')).toBeInTheDocument();
    expect(screen.getByLabelText('Authenticator code')).toHaveValue('');
  });

  it('asks for the assessment and plan before signing, without calling the server', async () => {
    const fetchMock = mockApi({});
    renderWithQuery(<NoteEditor visitId="visit-1" initial={note()} onSigned={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Sign...' }));
    type('Authenticator code', '123456');
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Sign note' }));
      await vi.runOnlyPendingTimersAsync();
    });

    expect(
      screen.getByText('Write the assessment and the plan before signing'),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('SignedNote', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('shows the signed text read-only and adds an addendum', async () => {
    const signed = note({
      status: 'SIGNED',
      assessment: 'Malaria',
      signedAt: '2026-01-01T09:00:00Z',
    });
    mockApi({
      'POST /api/visits/visit-1/note/addenda': () =>
        jsonResponse(200, {
          ...signed,
          addenda: [
            {
              id: 'a1',
              authorId: 'doctor-1',
              text: 'Test positive',
              createdAt: '2026-01-01T10:00:00Z',
            },
          ],
        }),
    });
    const onChanged = vi.fn();
    renderWithQuery(<SignedNote visitId="visit-1" note={signed} onChanged={onChanged} />);

    expect(screen.getByText('Malaria')).toBeInTheDocument();
    expect(screen.queryByLabelText('Assessment')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Add an addendum'), {
      target: { value: 'Test positive' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Add addendum' }));

    await vi.waitFor(() => {
      expect(onChanged).toHaveBeenCalled();
    });
  });
});

describe('PatientHistory', () => {
  it("lists earlier signed notes and leaves out this visit's", async () => {
    mockApi({
      'GET /api/patients/patient-1/history': () =>
        jsonResponse(200, [
          note({ id: 'n2', visitId: 'visit-1', status: 'SIGNED', assessment: 'Today' }),
          note({
            id: 'n1',
            visitId: 'visit-0',
            status: 'SIGNED',
            assessment: 'Last month',
            signedAt: '2025-12-01T09:00:00Z',
          }),
        ]),
    });
    renderWithQuery(<PatientHistory patientId="patient-1" visitId="visit-1" />);

    expect(await screen.findByText('Last month')).toBeInTheDocument();
    expect(screen.queryByText('Today')).not.toBeInTheDocument();
  });

  it('shows a refusal', async () => {
    mockApi({
      'GET /api/patients/patient-1/history': () =>
        jsonResponse(403, {
          code: 'FORBIDDEN',
          message: 'You are not looking after this patient',
          fieldErrors: [],
          requestId: null,
        }),
    });
    renderWithQuery(<PatientHistory patientId="patient-1" visitId="visit-1" />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
