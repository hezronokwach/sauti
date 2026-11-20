import { describe, expect, it } from 'vitest';
import { MAX_PIECE_MS, MIN_PIECE_MS, PieceCutter, TICK_MS, quietLevel } from './cutting';

const QUIET = 0.01;
const SPEECH = 0.1;

/** Feeds the same level for a stretch of time; returns the decisions that cut. */
function feed(cutter: PieceCutter, level: number, ms: number) {
  const cuts = [];
  for (let t = 0; t < ms; t += TICK_MS) {
    const decision = cutter.tick(level);
    if (decision.cut) {
      cuts.push(decision);
    }
  }
  return cuts;
}

describe('quietLevel', () => {
  it("takes the median of the calibration, so a cough doesn't count", () => {
    expect(quietLevel([0.01, 0.012, 0.011, 0.5, 0.009])).toBe(0.011);
  });

  it('never goes to zero, so dead silence does not make every sound speech', () => {
    expect(quietLevel([0, 0, 0])).toBeGreaterThan(0);
    expect(quietLevel([])).toBe(0);
  });
});

describe('PieceCutter', () => {
  it('does not cut a long stretch of speech before 45 seconds', () => {
    const cutter = new PieceCutter(QUIET);

    expect(feed(cutter, SPEECH, MAX_PIECE_MS - TICK_MS)).toHaveLength(0);
  });

  it('cuts at 45 seconds regardless', () => {
    const cutter = new PieceCutter(QUIET);

    const cuts = feed(cutter, SPEECH, MAX_PIECE_MS);

    expect(cuts).toEqual([{ cut: true, keep: true }]);
    expect(cutter.currentMs).toBe(0);
  });

  it('cuts at the first 0.6-second pause after 20 seconds', () => {
    const cutter = new PieceCutter(QUIET);
    feed(cutter, SPEECH, MIN_PIECE_MS);

    expect(feed(cutter, QUIET, 500)).toHaveLength(0);
    expect(feed(cutter, QUIET, 100)).toEqual([{ cut: true, keep: true }]);
  });

  it('does not cut on a pause before 20 seconds', () => {
    const cutter = new PieceCutter(QUIET);
    feed(cutter, SPEECH, 10_000);

    expect(feed(cutter, QUIET, 2_000)).toHaveLength(0);
  });

  it('cuts silence at 20 seconds and marks it to be thrown away', () => {
    const cutter = new PieceCutter(QUIET);

    expect(feed(cutter, QUIET, MIN_PIECE_MS)).toEqual([{ cut: true, keep: false }]);
  });

  it('keeps the last piece at stop only if it held speech', () => {
    const talking = new PieceCutter(QUIET);
    feed(talking, SPEECH, 3_000);
    expect(talking.finish()).toEqual({ keep: true, durationMs: 3_000 });

    const silent = new PieceCutter(QUIET);
    feed(silent, QUIET, 3_000);
    expect(silent.finish().keep).toBe(false);
  });
});
