import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiError, jsonResponse, mockApi, renderWithQuery } from '../../test/render';
import { TranscriptPanel } from './TranscriptPanel';
import type { TranscriptSegmentResponse } from './useTranscript';

function line(
  orderNo: number,
  text: string,
  confidence: TranscriptSegmentResponse['confidence'] = 'HIGH',
): TranscriptSegmentResponse {
  return {
    orderNo,
    startMs: orderNo * 1_000 + 31_000,
    endMs: orderNo * 1_000 + 32_000,
    text,
    confidence,
  };
}

describe('TranscriptPanel', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('marks lines the transcriber was unsure of', async () => {
    mockApi({
      'GET /api/captures/s1/segments': () =>
        jsonResponse(200, [
          line(0, 'Habari daktari.'),
          line(1, 'Sina kikohozi.', 'REVIEW'),
          line(2, 'Kichwa kinauma.', 'LOW'),
        ]),
    });

    renderWithQuery(<TranscriptPanel sessionId="s1" live={false} />);

    expect(await screen.findByText('Habari daktari.')).toBeInTheDocument();
    expect(screen.getByText('Sina kikohozi.').closest('li')).toHaveClass('seg-review');
    expect(screen.getByText('Kichwa kinauma.').closest('li')).toHaveClass('seg-low');
    expect(screen.getByText('check')).toBeInTheDocument();
    expect(screen.getByText('unsure')).toBeInTheDocument();
  });

  it('asks every 2 seconds for lines after the last one while live', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let round = 0;
    const fetchMock = mockApi({
      'GET /api/captures/s1/segments': () => {
        round += 1;
        return jsonResponse(
          200,
          round === 1 ? [line(0, 'Habari.')] : round === 2 ? [line(1, 'Nina homa.')] : [],
        );
      },
    });
    renderWithQuery(<TranscriptPanel sessionId="s1" live />);
    await screen.findByText('Habari.');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000);
    });

    expect(await screen.findByText('Nina homa.')).toBeInTheDocument();
    const urls = fetchMock.mock.calls.map(([url]) => (typeof url === 'string' ? url : ''));
    expect(urls[0]).toBe('/api/captures/s1/segments');
    expect(urls[1]).toBe('/api/captures/s1/segments?after=0');
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });

  it('plays the moment a line was said', async () => {
    const user = userEvent.setup();
    const played: { src: string; currentTime: number }[] = [];
    class FakeAudio {
      currentTime = 0;
      onended: (() => void) | null = null;
      readonly src: string;
      constructor(src: string) {
        this.src = src;
      }
      pause() {}
      play() {
        played.push({ src: this.src, currentTime: this.currentTime });
        return Promise.resolve();
      }
    }
    vi.stubGlobal('Audio', FakeAudio);
    Object.assign(URL, {
      createObjectURL: () => 'blob:piece',
      revokeObjectURL: () => undefined,
    });
    mockApi({
      'GET /api/captures/s1/segments': () => jsonResponse(200, [line(0, 'Habari daktari.')]),
      'GET /api/captures/s1/audio': () =>
        new Response(new Blob(['ogg']), {
          status: 200,
          headers: { 'Content-Type': 'audio/ogg', 'X-Piece-Offset-Ms': '30000' },
        }),
    });
    renderWithQuery(<TranscriptPanel sessionId="s1" live={false} />);

    await user.click(
      await screen.findByRole('button', { name: /Play from 0:31: Habari daktari./ }),
    );

    await vi.waitFor(() => {
      expect(played).toEqual([{ src: 'blob:piece', currentTime: 1 }]);
    });
  });

  it('says so when the recording was erased', async () => {
    mockApi({
      'GET /api/captures/s1/segments': () =>
        jsonResponse(410, apiError('GONE', 'This recording was erased')),
    });

    renderWithQuery(<TranscriptPanel sessionId="s1" live />);

    expect(await screen.findByText('This recording was erased.')).toBeInTheDocument();
  });
});
