/**
 * When to cut a recording into pieces, and which pieces are silence. Pure logic, driven by
 * a loudness reading every 100 ms, so it can be tested without a microphone.
 */

/** A piece is cut once it is at least this long and the speaker pauses... */
export const MIN_PIECE_MS = 20_000;
/** ...for at least this long, */
export const PAUSE_MS = 600;
/** or at this length regardless. */
export const MAX_PIECE_MS = 45_000;
/** Speech is louder than the room's quiet level by this factor. */
export const SPEECH_FACTOR = 2.5;
/** A piece with less speech than this is silence and is thrown away. */
export const MIN_SPEECH_MS = 300;
/** How often loudness is measured. */
export const TICK_MS = 100;

/** The room's quiet level: the median loudness over the 1-second calibration. */
export function quietLevel(samples: number[]): number {
  if (samples.length === 0) {
    return 0;
  }
  const sorted = [...samples].sort((a, b) => a - b);
  // Guard against a perfectly silent calibration making every sound "speech".
  return Math.max(sorted[Math.floor(sorted.length / 2)] ?? 0, 0.002);
}

/** What the cutter decides on each tick. */
export interface CutDecision {
  cut: boolean;
  /** Whether the piece being cut had enough speech to keep. */
  keep: boolean;
}

/**
 * Tracks one piece: how long it is, how long the speaker has been quiet, and how much
 * speech it holds.
 */
export class PieceCutter {
  private pieceMs = 0;
  private quietMs = 0;
  private speechMs = 0;
  private readonly quiet: number;

  constructor(quiet: number) {
    this.quiet = quiet;
  }

  /** Feeds one loudness reading; returns whether to cut now. */
  tick(level: number): CutDecision {
    this.pieceMs += TICK_MS;
    if (level > this.quiet * SPEECH_FACTOR) {
      this.speechMs += TICK_MS;
      this.quietMs = 0;
    } else {
      this.quietMs += TICK_MS;
    }
    const cut =
      (this.pieceMs >= MIN_PIECE_MS && this.quietMs >= PAUSE_MS) || this.pieceMs >= MAX_PIECE_MS;
    const decision = { cut, keep: this.speechMs >= MIN_SPEECH_MS };
    if (cut) {
      this.reset();
    }
    return decision;
  }

  /** The piece in progress at stop: keep it only if it held speech. */
  finish(): { keep: boolean; durationMs: number } {
    const result = { keep: this.speechMs >= MIN_SPEECH_MS, durationMs: this.pieceMs };
    this.reset();
    return result;
  }

  get currentMs(): number {
    return this.pieceMs;
  }

  private reset() {
    this.pieceMs = 0;
    this.quietMs = 0;
    this.speechMs = 0;
  }
}
