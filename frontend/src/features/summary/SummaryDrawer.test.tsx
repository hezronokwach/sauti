import { act, fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { NoteEditor } from '../notes/NoteEditor';
import type { NoteResponse } from '../notes/types';
import { SummaryDrawer } from './SummaryDrawer';
import type { ClaimResponse, SummaryResponse } from './types';

vi.mock('../transcript/playAudio', () => ({
  playAt: vi.fn(() => Promise.resolve(null)),
}));

function claim(overrides: Partial<ClaimResponse>): ClaimResponse {
  return {
    section: 'history',
    text: 'Fever for three days',
    quote: 'nina homa siku tatu',
    supported: true,
    quotePresent: true,
    negationConsistent: true,
    numbersMatch: true,
    checkAudio: false,
    startMs: 0,
    sessionId: 'session-1',
    ...overrides,
  };
}

/** Like the fake model: two good claims (one from an unsure line) and the planted one. */
const SUMMARY: SummaryResponse = {
  requestId: 'request-1',
  available: true,
  message: null,
  claims: [
    claim({}),
    claim({ text: 'No cough', quote: 'sina kikohozi', checkAudio: true, startMs: 4000 }),
    claim({
      section: 'assessment',
      text: 'Has had a high fever for ten days',
      quote: 'nina homa kali siku kumi',
      supported: false,
      quotePresent: false,
      startMs: null,
      sessionId: null,
    }),
  ],
};

describe('SummaryDrawer', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  type OnAccept = (requestId: string, claims: number[]) => Promise<string | null>;

  async function openSummary(onAccept = vi.fn<OnAccept>(() => Promise.resolve(null))) {
    mockApi({ 'POST /api/visits/visit-1/summary': () => jsonResponse(200, SUMMARY) });
    renderWithQuery(<SummaryDrawer visitId="visit-1" onAccept={onAccept} />);
    fireEvent.click(screen.getByRole('button', { name: 'Summarise transcript' }));
    await screen.findByText('Has had a high fever for ten days');
    return onAccept;
  }

  it('shows the planted claim as not supported, with no way to accept it', async () => {
    await openSummary();

    expect(
      screen.getByText('Not supported: the quote is not in the transcript.'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Accept: Has had a high fever for ten days' }),
    ).not.toBeInTheDocument();
  });

  it('accepts one claim and marks it added', async () => {
    const onAccept = await openSummary();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Accept: Fever for three days' }));
      await Promise.resolve();
    });

    expect(onAccept).toHaveBeenCalledWith('request-1', [0]);
    expect(screen.getByText('Added to the note')).toBeInTheDocument();
  });

  it('asks to listen before accepting a claim from an unsure line', async () => {
    const onAccept = await openSummary();
    const acceptCough = screen.getByRole('button', { name: 'Accept: No cough' });
    expect(acceptCough).toBeDisabled();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Play the words for: No cough' }));
      await Promise.resolve();
    });

    expect(acceptCough).toBeEnabled();
    await act(async () => {
      fireEvent.click(acceptCough);
      await Promise.resolve();
    });
    expect(onAccept).toHaveBeenCalledWith('request-1', [1]);
  });

  it('accepts all only takes claims that passed and were listened to where needed', async () => {
    const onAccept = await openSummary();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Accept all that passed (1)' }));
      await Promise.resolve();
    });

    expect(onAccept).toHaveBeenCalledWith('request-1', [0]);
  });

  it('shows why the server refused', async () => {
    await openSummary(
      vi.fn(() => Promise.resolve('A claim that failed the checks cannot be accepted')),
    );

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Accept: Fever for three days' }));
      await Promise.resolve();
    });

    expect(screen.getByRole('alert')).toHaveTextContent('cannot be accepted');
    expect(screen.queryByText('Added to the note')).not.toBeInTheDocument();
  });

  it('says when the summary is unavailable', async () => {
    mockApi({
      'POST /api/visits/visit-1/summary': () =>
        jsonResponse(200, {
          requestId: 'r',
          available: false,
          message: 'Summary unavailable; write the note yourself or try again later',
          claims: [],
        }),
    });
    renderWithQuery(<SummaryDrawer visitId="visit-1" onAccept={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Summarise transcript' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Summary unavailable');
  });
});

describe('Accepting from the note editor', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('saves the typing first, then shows the note with the accepted text', async () => {
    const calls: string[] = [];
    const accepts: unknown[] = [];
    const saved: NoteResponse = {
      id: 'n',
      visitId: 'visit-1',
      patientId: 'p',
      authorId: 'd',
      status: 'DRAFT',
      version: 2,
      presentingComplaint: null,
      history: null,
      examination: null,
      assessment: 'Typed',
      plan: null,
      aiAssisted: false,
      updatedAt: '2026-01-01T08:00:00Z',
      signedAt: null,
      signedBy: null,
      addenda: [],
    };
    mockApi({
      'POST /api/visits/visit-1/summary': () => jsonResponse(200, SUMMARY),
      'PUT /api/visits/visit-1/note': () => {
        calls.push('save');
        return jsonResponse(200, saved);
      },
      'POST /api/visits/visit-1/summary/request-1/accept': (init) => {
        calls.push('accept');
        accepts.push(bodyOf(init));
        return jsonResponse(200, {
          ...saved,
          version: 3,
          history: 'Fever for three days',
          aiAssisted: true,
        });
      },
    });
    renderWithQuery(<NoteEditor visitId="visit-1" initial={null} onSigned={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Assessment'), { target: { value: 'Typed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Summarise transcript' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Accept: Fever for three days' }));

    expect(await screen.findByText('Added to the note')).toBeInTheDocument();
    expect(calls).toEqual(['save', 'accept']);
    expect(accepts).toEqual([{ claims: [0], noteVersion: 2 }]);
    expect(screen.getByLabelText('History')).toHaveValue('Fever for three days');
    expect(screen.getByLabelText('Assessment')).toHaveValue('Typed');
  });
});
