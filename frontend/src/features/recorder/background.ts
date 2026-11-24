import { MAX_AGE_MS, deleteAll, deleteOlderThan } from './pieceStore';
import { Uploader } from './uploader';
import type { UploadStatus } from './uploader';

type Listener = (status: UploadStatus) => void;

const listeners = new Set<Listener>();
let latest: UploadStatus = { pending: 0, offline: false, lastError: null };

/** The one uploader for the page: every recording saves into the same store it drains. */
export const uploader = new Uploader((status) => {
  latest = status;
  listeners.forEach((listener) => {
    listener(status);
  });
});

export function onUploadStatus(listener: Listener): () => void {
  listeners.add(listener);
  listener(latest);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * On page load: drop pieces older than a day, then send whatever is still waiting (from a
 * closed tab or a lost connection). Coming back online wakes the uploader at once.
 */
export function startUploadsInBackground(): void {
  if (typeof indexedDB === 'undefined') {
    return;
  }
  void deleteOlderThan(MAX_AGE_MS)
    .catch(() => 0)
    .then(() => {
      uploader.wake();
    });
  window.addEventListener('online', () => {
    uploader.wake();
  });
}

/**
 * Before logout: try to send what is waiting (for a few seconds at most), then clear the
 * device so no recording stays behind for the next person.
 */
export async function flushAndClearPieces(timeoutMs = 5_000): Promise<void> {
  if (typeof indexedDB === 'undefined') {
    return;
  }
  uploader.stop();
  await Promise.race([uploader.drain(), new Promise((resolve) => setTimeout(resolve, timeoutMs))]);
  uploader.stop();
  await deleteAll().catch(() => undefined);
}
