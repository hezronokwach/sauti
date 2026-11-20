import { ApiRequestError, errorMessage, toApiRequestError } from '../../api/client';

/** The one recording playing at a time, so starting another stops it. */
let current: HTMLAudioElement | null = null;

/**
 * Plays a recording from a moment: fetches the piece holding it (decrypted by the server,
 * never cached) and starts at the right offset. Resolves with an error message to show, or
 * null when it played.
 */
export async function playAt(sessionId: string, atMs: number): Promise<string | null> {
  try {
    const response = await fetch(`/api/captures/${sessionId}/audio?atMs=${String(atMs)}`, {
      credentials: 'include',
    });
    if (!response.ok) {
      throw await toApiRequestError(response);
    }
    const offsetMs = Number(response.headers.get('X-Piece-Offset-Ms') ?? '0');
    const url = URL.createObjectURL(await response.blob());
    current?.pause();
    const player = new Audio(url);
    current = player;
    player.currentTime = Math.max(0, (atMs - offsetMs) / 1000);
    player.onended = () => {
      URL.revokeObjectURL(url);
    };
    await player.play();
    return null;
  } catch (cause) {
    return cause instanceof ApiRequestError ? errorMessage(cause) : 'The audio could not be played';
  }
}
