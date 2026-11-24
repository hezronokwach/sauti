import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { ReasonDictation } from './ReasonDictation';

const consent = vi.hoisted(() => ({ agreed: true }));
const recorderCalls = vi.hoisted(() => [] as string[]);

vi.mock('../consent/ConsentPanel', () => ({ ConsentPanel: () => <p>Consent wording</p> }));
vi.mock('../consent/api', () => ({
  isAgreed: () => consent.agreed,
  useConsentHistory: () => ({ data: [] }),
}));
vi.mock('../recorder/background', () => ({ uploader: {} }));
vi.mock('../recorder/recorder', () => ({
  Recorder: class {
    private readonly onStatus: (status: object) => void;
    constructor(_id: string, _uploader: unknown, onStatus: (status: object) => void) {
      this.onStatus = onStatus;
    }
    cut(field: string) {
      recorderCalls.push(`cut:${field}`);
    }
    start() {
      recorderCalls.push('start');
      this.onStatus({ state: 'recording', elapsedMs: 1000, level: 0 });
      return Promise.resolve();
    }
    stop() {
      recorderCalls.push('stop');
      this.onStatus({ state: 'idle', elapsedMs: 0, level: 0 });
      return Promise.resolve(1);
    }
  },
}));

describe('ReasonDictation', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    consent.agreed = true;
    recorderCalls.length = 0;
  });

  it('asks for nothing until voice is chosen, and needs consent to record', () => {
    consent.agreed = false;
    renderWithQuery(<ReasonDictation visitId="v-1" language="SWAHILI" />);
    expect(screen.queryByText('Consent wording')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Use voice' }));

    expect(screen.getByText('Consent wording')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Record reason' })).toBeDisabled();
  });

  it('records for the reason field only and says a draft is coming', async () => {
    const started: unknown[] = [];
    mockApi({
      'POST /api/visits/v-1/captures': (init) => {
        started.push(bodyOf(init));
        return jsonResponse(201, { id: 's-1', status: 'RECORDING' });
      },
      'POST /api/captures/s-1/stop': () => jsonResponse(200, { id: 's-1', status: 'STOPPED' }),
    });
    renderWithQuery(<ReasonDictation visitId="v-1" language="SWAHILI" />);
    fireEvent.click(screen.getByRole('button', { name: 'Use voice' }));

    fireEvent.click(screen.getByRole('button', { name: 'Record reason' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Stop' }));

    expect(await screen.findByText(/A draft will appear/)).toBeInTheDocument();
    expect(started).toEqual([{ purpose: 'RECORD_RECEPTION', languageHint: 'sw' }]);
    expect(recorderCalls).toEqual(['cut:REASON_FOR_VISIT', 'start', 'stop']);
  });
});
