import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { RecordingPanel } from './RecordingPanel';

const agreed = {
  id: 'consent-1',
  visitId: 'visit-1',
  purpose: 'RECORD_CONSULTATION',
  decision: 'AGREED',
  givenBy: 'PATIENT',
  wordingVersion: 'v1-sw',
  recordedBy: 'staff-1',
  recordedAt: '2026-01-01T08:00:00Z',
};

describe('RecordingPanel', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'mediaDevices');
  });

  it('closes the recording it opened when the microphone is refused', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn().mockRejectedValue(new DOMException('Denied', 'NotAllowedError')),
      },
    });
    const fetchMock = mockApi({
      'GET /api/consent/wording': () =>
        jsonResponse(200, {
          version: 'v1-sw',
          language: 'SWAHILI',
          text: 'Je?',
          demonstration: true,
        }),
      'GET /api/visits/visit-1/consents': () => jsonResponse(200, [agreed]),
      'GET /api/visits/visit-1/captures': () => jsonResponse(200, []),
      'POST /api/visits/visit-1/captures': () =>
        jsonResponse(201, { id: 'session-1', status: 'RECORDING' }),
      'POST /api/captures/session-1/erase': () =>
        jsonResponse(200, { id: 'session-1', status: 'ERASED' }),
    });
    const user = userEvent.setup();
    renderWithQuery(
      <RecordingPanel visitId="visit-1" purpose="RECORD_CONSULTATION" language="SWAHILI" />,
    );

    const record = await screen.findByRole('button', { name: 'Record' });
    await vi.waitFor(() => {
      expect(record).toBeEnabled();
    });
    await user.click(record);

    expect(
      await screen.findByText(
        'The microphone is not available. Allow microphone access and try again.',
      ),
    ).toBeInTheDocument();
    await vi.waitFor(() => {
      expect(fetchMock.mock.calls.some(([path]) => path === '/api/captures/session-1/erase')).toBe(
        true,
      );
    });
    expect(screen.getByRole('button', { name: 'Record' })).toBeInTheDocument();
  });
});
