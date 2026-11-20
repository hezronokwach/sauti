import { PieceCutter, TICK_MS, quietLevel } from './cutting';
import { savePiece } from './pieceStore';
import type { DraftField } from './pieceStore';
import type { Uploader } from './uploader';

/** What the recorder reports to the screen. */
export interface RecorderStatus {
  state: 'idle' | 'calibrating' | 'recording' | 'stopping';
  /** Time recorded so far. */
  elapsedMs: number;
  /** Current loudness, 0 to 1, for a level meter. */
  level: number;
}

const CALIBRATION_TICKS = 10;

/** webm/opus where supported; mp4 for Safari, which records nothing else. */
export function pickMimeType(): string {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? '';
}

/** Root-mean-square loudness of the latest audio frame. */
function loudness(analyser: AnalyserNode, buffer: Float32Array<ArrayBuffer>): number {
  analyser.getFloatTimeDomainData(buffer);
  let sum = 0;
  for (const sample of buffer) {
    sum += sample * sample;
  }
  return Math.sqrt(sum / buffer.length);
}

/**
 * Records one capture session into pieces. Each piece is a complete file: the recorder is
 * restarted on the same stream to cut (slices of a running recorder are not playable files
 * and transcription rejects them). Pieces with speech go to IndexedDB and the uploader is
 * woken; silent ones are dropped. Sequence numbers count kept pieces only, so the number
 * returned by {@link stop} is exactly what the server should expect.
 */
export class Recorder {
  private stream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private buffer: Float32Array<ArrayBuffer> | null = null;
  private current: MediaRecorder | null = null;
  private cutter: PieceCutter | null = null;
  private interval: ReturnType<typeof setInterval> | null = null;
  private wakeLock: WakeLockSentinel | null = null;
  private mimeType = '';
  private nextSequence = 0;
  private elapsedMs = 0;
  private pieceStartMs = 0;
  /** The field reception is on; the current piece is tagged with it. */
  private field: DraftField | null = null;
  private saving: Promise<void>[] = [];
  private status: RecorderStatus = { state: 'idle', elapsedMs: 0, level: 0 };

  private readonly sessionId: string;
  private readonly uploader: Uploader;
  private readonly onStatus: (status: RecorderStatus) => void;

  constructor(
    sessionId: string,
    uploader: Uploader,
    onStatus: (status: RecorderStatus) => void = () => undefined,
  ) {
    this.sessionId = sessionId;
    this.uploader = uploader;
    this.onStatus = onStatus;
  }

  async start(): Promise<void> {
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    });
    this.mimeType = pickMimeType();
    this.audioContext = new AudioContext();
    this.analyser = this.audioContext.createAnalyser();
    this.analyser.fftSize = 2048;
    this.audioContext.createMediaStreamSource(this.stream).connect(this.analyser);
    this.buffer = new Float32Array(this.analyser.fftSize);
    this.report({ state: 'calibrating' });

    const samples = await this.calibrate();
    this.cutter = new PieceCutter(quietLevel(samples));
    await this.requestWakeLock();
    this.current = this.newRecorder();
    this.report({ state: 'recording' });
    this.interval = setInterval(() => {
      this.tick();
    }, TICK_MS);
  }

  /**
   * Reception moved to another field: the current piece ends here, tagged with the field it
   * was recorded for, and the next one is tagged with the new field.
   */
  cut(field: DraftField | null): void {
    if (field === this.field) {
      return;
    }
    if (!this.cutter || !this.current) {
      this.field = field;
      return;
    }
    const last = this.cutter.finish();
    const finished = this.current;
    const finishedField = this.field;
    this.current = this.newRecorder();
    this.field = field;
    this.finishPiece(finished, last.keep, last.durationMs, finishedField);
  }

  /** Stops recording, saves the last piece if it held speech, and returns how many pieces were kept. */
  async stop(): Promise<number> {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
    this.report({ state: 'stopping' });
    if (this.current && this.cutter) {
      const last = this.cutter.finish();
      this.finishPiece(this.current, last.keep, last.durationMs, this.field);
      this.current = null;
    }
    await Promise.all(this.saving);
    this.release();
    this.report({ state: 'idle', level: 0 });
    return this.nextSequence;
  }

  private calibrate(): Promise<number[]> {
    return new Promise((resolve) => {
      const samples: number[] = [];
      const timer = setInterval(() => {
        samples.push(this.readLevel());
        if (samples.length >= CALIBRATION_TICKS) {
          clearInterval(timer);
          resolve(samples);
        }
      }, TICK_MS);
    });
  }

  private tick() {
    if (!this.cutter || !this.current) {
      return;
    }
    const level = this.readLevel();
    this.elapsedMs += TICK_MS;
    const durationMs = this.cutter.currentMs + TICK_MS;
    const decision = this.cutter.tick(level);
    this.report({ elapsedMs: this.elapsedMs, level });
    if (decision.cut) {
      // Start the next piece before stopping this one, so no audio falls between them.
      const finished = this.current;
      this.current = this.newRecorder();
      this.finishPiece(finished, decision.keep, durationMs, this.field);
    }
  }

  private newRecorder(): MediaRecorder {
    if (!this.stream) {
      throw new Error('The microphone is not open');
    }
    const recorder = new MediaRecorder(
      this.stream,
      this.mimeType ? { mimeType: this.mimeType } : undefined,
    );
    recorder.start();
    return recorder;
  }

  /** Stops a piece's recorder and, if it held speech, saves the complete file it produces. */
  private finishPiece(
    recorder: MediaRecorder,
    keep: boolean,
    durationMs: number,
    field: DraftField | null,
  ) {
    const offsetMs = this.pieceStartMs;
    this.pieceStartMs += durationMs;
    const sequenceNo = keep ? this.nextSequence++ : -1;
    const saved = new Promise<void>((resolve) => {
      const chunks: Blob[] = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunks.push(event.data);
        }
      };
      recorder.onstop = () => {
        if (!keep) {
          resolve();
          return;
        }
        const type = recorder.mimeType || this.mimeType || 'audio/webm';
        void savePiece({
          sessionId: this.sessionId,
          sequenceNo,
          audio: new Blob(chunks, { type }),
          mimeType: type,
          durationMs,
          offsetMs,
          ...(field ? { field } : {}),
          createdAt: Date.now(),
        }).then(() => {
          this.uploader.wake();
          resolve();
        });
      };
    });
    this.saving.push(saved);
    recorder.stop();
  }

  private readLevel(): number {
    return this.analyser && this.buffer ? loudness(this.analyser, this.buffer) : 0;
  }

  private async requestWakeLock() {
    try {
      this.wakeLock = 'wakeLock' in navigator ? await navigator.wakeLock.request('screen') : null;
    } catch {
      // Not supported or refused (low battery): recording works without it.
      this.wakeLock = null;
    }
  }

  private release() {
    this.stream?.getTracks().forEach((track) => {
      track.stop();
    });
    this.stream = null;
    void this.audioContext?.close();
    this.audioContext = null;
    void this.wakeLock?.release();
    this.wakeLock = null;
  }

  private report(change: Partial<RecorderStatus>) {
    this.status = { ...this.status, ...change };
    this.onStatus(this.status);
  }
}
