import { ApiRequestError, apiFetch } from '../../api/client';
import { deletePiece, deleteSession, pendingPieces } from './pieceStore';
import type { StoredPiece } from './pieceStore';

/** Waits after a network failure: 2 s, 4 s, 8 s ... up to 30 s. */
export function backoffMs(failures: number): number {
  return Math.min(2_000 * 2 ** Math.max(failures - 1, 0), 30_000);
}

/** What the uploader reports to the screen. */
export interface UploadStatus {
  pending: number;
  /** True while the last attempt failed for a network reason and a retry is waiting. */
  offline: boolean;
  /** A piece the server refused for good (not a network problem), if any. */
  lastError: string | null;
}

async function sha256Hex(blob: Blob): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer());
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Sends one piece. Throws on failure; a repeat of a piece already received is a success. */
export async function sendPiece(piece: StoredPiece): Promise<void> {
  await apiFetch(`/api/captures/${piece.sessionId}/pieces/${String(piece.sequenceNo)}`, {
    method: 'PUT',
    headers: {
      'Content-Type': piece.mimeType,
      'X-Piece-Sha256': await sha256Hex(piece.audio),
      'X-Piece-Duration-Ms': String(piece.durationMs),
      'X-Piece-Offset-Ms': String(piece.offsetMs),
    },
    body: piece.audio,
  });
}

/** Whether a failed send should be retried later (network or server trouble). */
function isRetryable(error: unknown): boolean {
  return !(error instanceof ApiRequestError) || error.status >= 500 || error.status === 429;
}

/**
 * Sends waiting pieces oldest first, deleting each once the server has it. Network and
 * server errors wait and retry with backoff; a recording the server says was erased (410)
 * has its pieces dropped; a piece refused for another reason is dropped and reported, so
 * one bad piece cannot block the rest.
 */
export class Uploader {
  private failures = 0;
  private running = false;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private status: UploadStatus = { pending: 0, offline: false, lastError: null };

  private readonly onStatus: (status: UploadStatus) => void;
  private readonly send: (piece: StoredPiece) => Promise<void>;

  constructor(
    onStatus: (status: UploadStatus) => void = () => undefined,
    send: (piece: StoredPiece) => Promise<void> = sendPiece,
  ) {
    this.onStatus = onStatus;
    this.send = send;
  }

  /** Starts sending now (call after saving a piece, on page load, or when back online). */
  wake(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    void this.drain();
  }

  stop(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  /** Sends everything waiting; resolves when nothing is left or a retry has been scheduled. */
  async drain(): Promise<void> {
    if (this.running) {
      return;
    }
    this.running = true;
    try {
      for (;;) {
        const [next, ...rest] = await pendingPieces();
        this.report({ pending: rest.length + (next ? 1 : 0) });
        if (!next) {
          this.failures = 0;
          this.report({ offline: false });
          return;
        }
        try {
          await this.send(next);
          await deletePiece(next.sessionId, next.sequenceNo);
          this.failures = 0;
          this.report({ offline: false });
        } catch (error) {
          if (error instanceof ApiRequestError && error.status === 410) {
            await deleteSession(next.sessionId);
          } else if (isRetryable(error)) {
            this.failures += 1;
            this.report({ offline: true });
            this.timer = setTimeout(() => {
              this.timer = null;
              void this.drain();
            }, backoffMs(this.failures));
            return;
          } else {
            await deletePiece(next.sessionId, next.sequenceNo);
            this.report({
              lastError: error instanceof Error ? error.message : 'A piece was refused',
            });
          }
        }
      }
    } finally {
      this.running = false;
    }
  }

  private report(change: Partial<UploadStatus>) {
    this.status = { ...this.status, ...change };
    this.onStatus(this.status);
  }
}
