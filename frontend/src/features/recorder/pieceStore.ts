/**
 * Recorded pieces waiting to be sent, kept in IndexedDB so a dropped network or a closed
 * tab loses nothing: the uploader picks them up again when the page opens.
 */

/** Reception fields that can be drafted from speech; identity fields never are. */
export type DraftField = 'REASON_FOR_VISIT' | 'RESIDENCE' | 'NEXT_OF_KIN_NAME';

export interface StoredPiece {
  sessionId: string;
  sequenceNo: number;
  audio: Blob;
  mimeType: string;
  durationMs: number;
  offsetMs: number;
  /** The reception field the piece was recorded for, if any (voice drafts). */
  field?: DraftField;
  /** When it was recorded (epoch ms); pieces older than a day are deleted. */
  createdAt: number;
}

const DB_NAME = 'sauti-recorder';
const STORE = 'pieces';
const VERSION = 1;

/** Pieces older than this are deleted unsent: they would only fill the device. */
export const MAX_AGE_MS = 24 * 60 * 60 * 1000;

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, VERSION);
    request.onupgradeneeded = () => {
      const store = request.result.createObjectStore(STORE, {
        keyPath: ['sessionId', 'sequenceNo'],
      });
      store.createIndex('createdAt', 'createdAt');
    };
    request.onsuccess = () => {
      resolve(request.result);
    };
    request.onerror = () => {
      reject(request.error ?? new Error('Could not open the piece store'));
    };
  });
}

/** Runs one transaction and resolves when it completes. */
async function run<T>(
  mode: IDBTransactionMode,
  work: (store: IDBObjectStore) => IDBRequest<T> | undefined,
): Promise<T | undefined> {
  const db = await open();
  try {
    return await new Promise<T | undefined>((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const request = work(tx.objectStore(STORE));
      tx.oncomplete = () => {
        resolve(request?.result);
      };
      tx.onerror = () => {
        reject(tx.error ?? new Error('Piece store transaction failed'));
      };
      tx.onabort = () => {
        reject(tx.error ?? new Error('Piece store transaction aborted'));
      };
    });
  } finally {
    db.close();
  }
}

export async function savePiece(piece: StoredPiece): Promise<void> {
  await run('readwrite', (store) => store.put(piece));
}

export async function deletePiece(sessionId: string, sequenceNo: number): Promise<void> {
  await run('readwrite', (store) => store.delete([sessionId, sequenceNo]));
}

/** Every waiting piece, oldest first. */
export async function pendingPieces(): Promise<StoredPiece[]> {
  const all = (await run('readonly', (store) => store.index('createdAt').getAll())) as
    StoredPiece[] | undefined;
  return all ?? [];
}

export async function deleteSession(sessionId: string): Promise<void> {
  const pieces = await pendingPieces();
  for (const piece of pieces.filter((p) => p.sessionId === sessionId)) {
    await deletePiece(piece.sessionId, piece.sequenceNo);
  }
}

/** Deletes pieces recorded more than a day ago; returns how many. */
export async function deleteOlderThan(maxAgeMs: number, now: number = Date.now()): Promise<number> {
  const old = (await pendingPieces()).filter((piece) => now - piece.createdAt > maxAgeMs);
  for (const piece of old) {
    await deletePiece(piece.sessionId, piece.sequenceNo);
  }
  return old.length;
}

export async function deleteAll(): Promise<void> {
  await run('readwrite', (store) => store.clear());
}
