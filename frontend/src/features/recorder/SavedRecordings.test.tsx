import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { SavedRecordings } from './SavedRecordings';

const RECORDING = {
  id: 's-1',
  status: 'COMPLETE',
  startedAt: '2026-11-24T08:00:00Z',
  stoppedAt: '2026-11-24T08:05:00Z',
};

describe('SavedRecordings', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
  });

  it('reads a transcript only when the doctor opens it', async () => {
    const reads: string[] = [];
    mockApi({
      'GET /api/visits/v-1/captures': () => jsonResponse(200, [RECORDING]),
      'GET /api/captures/s-1/segments': () => {
        reads.push('segments');
        return jsonResponse(200, [
          { orderNo: 0, startMs: 0, endMs: 1000, text: 'Nina homa', confidence: 'HIGH' },
        ]);
      },
    });
    renderWithQuery(<SavedRecordings visitId="v-1" currentSessionId={null} />);

    const open = await screen.findByRole('button', { name: 'Open transcript' });
    expect(reads).toEqual([]);

    fireEvent.click(open);

    expect(await screen.findByText('Nina homa')).toBeInTheDocument();
    expect(reads).toEqual(['segments']);
  });

  it('leaves out the recording already on screen', async () => {
    const answered = vi.fn(() => jsonResponse(200, [RECORDING]));
    mockApi({ 'GET /api/visits/v-1/captures': answered });
    renderWithQuery(<SavedRecordings visitId="v-1" currentSessionId="s-1" />);

    await vi.waitFor(() => {
      expect(answered).toHaveBeenCalled();
    });
    expect(screen.queryByRole('button', { name: 'Open transcript' })).not.toBeInTheDocument();
  });

  it('erases only after confirming', async () => {
    const erased: string[] = [];
    mockApi({
      'GET /api/visits/v-1/captures': () => jsonResponse(200, erased.length ? [] : [RECORDING]),
      'POST /api/captures/s-1/erase': () => {
        erased.push('s-1');
        return jsonResponse(200, { ...RECORDING, status: 'ERASED' });
      },
    });
    renderWithQuery(<SavedRecordings visitId="v-1" currentSessionId={null} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Erase recording' }));
    expect(erased).toEqual([]);
    fireEvent.click(screen.getByRole('button', { name: 'Yes, erase' }));

    await vi.waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Open transcript' })).not.toBeInTheDocument();
    });
    expect(erased).toEqual(['s-1']);
  });
});
