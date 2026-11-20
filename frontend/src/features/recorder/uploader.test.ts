import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../api/client';
import { jsonResponse, mockApi } from '../../test/render';
import { deleteAll, deleteOlderThan, pendingPieces, savePiece } from './pieceStore';
import type { StoredPiece } from './pieceStore';
import { Uploader, backoffMs, sendPiece } from './uploader';
import type { UploadStatus } from './uploader';

function piece(sessionId: string, sequenceNo: number, createdAt = Date.now()): StoredPiece {
  return {
    sessionId,
    sequenceNo,
    audio: new Blob([`audio ${String(sequenceNo)}`], { type: 'audio/webm' }),
    mimeType: 'audio/webm',
    durationMs: 30_000,
    offsetMs: sequenceNo * 30_000,
    createdAt: createdAt + sequenceNo,
  };
}

function refused(status: number, code: string) {
  return new ApiRequestError(status, { code, message: code, fieldErrors: [], requestId: null });
}

describe('Uploader', () => {
  beforeEach(async () => {
    await deleteAll();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('sends pieces in the order they were recorded and deletes each once sent', async () => {
    const recordedAt = Date.now();
    await savePiece(piece('s1', 1, recordedAt));
    await savePiece(piece('s1', 0, recordedAt - 30_000));
    const sent: number[] = [];

    await new Uploader(undefined, (p) => {
      sent.push(p.sequenceNo);
      return Promise.resolve();
    }).drain();

    expect(sent).toEqual([0, 1]);
    expect(await pendingPieces()).toEqual([]);
  });

  it('keeps pieces through a lost connection and retries with backoff', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    await savePiece(piece('s1', 0));
    let online = false;
    const statuses: UploadStatus[] = [];
    const uploader = new Uploader(
      (status) => statuses.push(status),
      () => (online ? Promise.resolve() : Promise.reject(new TypeError('Failed to fetch'))),
    );

    await uploader.drain();
    expect(await pendingPieces()).toHaveLength(1);
    expect(statuses.at(-1)?.offline).toBe(true);

    online = true;
    await vi.advanceTimersByTimeAsync(2_000);
    await vi.waitFor(async () => {
      expect(await pendingPieces()).toEqual([]);
    });
    expect(statuses.at(-1)?.offline).toBe(false);
  });

  it('picks up pieces left by a closed tab', async () => {
    await savePiece(piece('s1', 0));
    await savePiece(piece('s1', 1));
    const sent: number[] = [];

    // A new page load: a fresh uploader reading the same store.
    await new Uploader(undefined, (p) => {
      sent.push(p.sequenceNo);
      return Promise.resolve();
    }).drain();

    expect(sent).toEqual([0, 1]);
  });

  it('drops every piece of a recording the server says was erased', async () => {
    await savePiece(piece('erased', 0));
    await savePiece(piece('erased', 1));
    await savePiece(piece('kept', 0, Date.now() + 1_000));
    const sent: string[] = [];

    await new Uploader(undefined, (p) => {
      if (p.sessionId === 'erased') {
        return Promise.reject(refused(410, 'GONE'));
      }
      sent.push(p.sessionId);
      return Promise.resolve();
    }).drain();

    expect(sent).toEqual(['kept']);
    expect(await pendingPieces()).toEqual([]);
  });

  it('drops a refused piece and carries on with the rest', async () => {
    await savePiece(piece('s1', 0));
    await savePiece(piece('s1', 1));
    const statuses: UploadStatus[] = [];

    await new Uploader(
      (status) => statuses.push(status),
      (p) =>
        p.sequenceNo === 0
          ? Promise.reject(refused(422, 'BUSINESS_RULE_VIOLATED'))
          : Promise.resolve(),
    ).drain();

    expect(await pendingPieces()).toEqual([]);
    expect(statuses.at(-1)?.lastError).toBe('BUSINESS_RULE_VIOLATED');
  });

  it('waits longer after each failure, up to 30 seconds', () => {
    expect([1, 2, 3, 4, 5, 6].map(backoffMs)).toEqual([
      2_000, 4_000, 8_000, 16_000, 30_000, 30_000,
    ]);
  });
});

describe('piece store', () => {
  beforeEach(async () => {
    await deleteAll();
  });

  it('deletes pieces older than a day', async () => {
    const now = Date.now();
    await savePiece(piece('old', 0, now - 25 * 60 * 60 * 1000));
    await savePiece(piece('new', 0, now));

    expect(await deleteOlderThan(24 * 60 * 60 * 1000, now)).toBe(1);
    expect((await pendingPieces()).map((p) => p.sessionId)).toEqual(['new']);
  });
});

describe('sendPiece', () => {
  it('sends the audio with its checksum, length and position', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    const fetchMock = mockApi({
      'PUT /api/captures/s1/pieces/2': () =>
        jsonResponse(201, { id: 'p', sequenceNo: 2, status: 'STORED' }),
    });
    const audio = new Blob(['hello'], { type: 'audio/webm' });

    await sendPiece({ ...piece('s1', 2), audio });

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.get('Content-Type')).toBe('audio/webm');
    expect(headers.get('X-Piece-Sha256')).toBe(
      '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824',
    );
    expect(headers.get('X-Piece-Duration-Ms')).toBe('30000');
    expect(headers.get('X-Piece-Offset-Ms')).toBe('60000');
  });
});
