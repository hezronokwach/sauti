import { useId, useState } from 'react';
import { playAt } from './playAudio';
import { useTranscript } from './useTranscript';
import type { TranscriptSegmentResponse } from './useTranscript';

interface TranscriptPanelProps {
  sessionId: string;
  /** True while recording, and until every piece is transcribed. */
  live: boolean;
}

function timestamp(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  return `${String(Math.floor(seconds / 60))}:${String(seconds % 60).padStart(2, '0')}`;
}

/**
 * The transcript as it arrives. Lines the transcriber was unsure of are marked: LOW lines
 * are lighter with a dotted underline, REVIEW lines carry a "check" marker (numbers,
 * medicines and negations always do). Clicking a line plays that moment of the recording.
 */
export function TranscriptPanel({ sessionId, live }: TranscriptPanelProps) {
  const { segments, erased, error } = useTranscript(sessionId, live);
  const [playError, setPlayError] = useState<string | null>(null);
  const headingId = useId();

  const play = async (segment: TranscriptSegmentResponse) => {
    setPlayError(null);
    setPlayError(await playAt(sessionId, segment.startMs));
  };

  if (erased) {
    return (
      <section aria-labelledby={headingId} className="panel">
        <h2 id={headingId}>Transcript</h2>
        <p role="status">This recording was erased.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby={headingId} className="panel">
      <h2 id={headingId}>Transcript</h2>
      <div aria-live="polite">
        {error ? <p role="alert">{error}</p> : null}
        {playError ? <p role="alert">{playError}</p> : null}
      </div>
      {segments.length === 0 ? (
        <p className="quiet">
          {live
            ? 'Listening. Lines appear a few seconds behind speech.'
            : 'No speech was transcribed.'}
        </p>
      ) : (
        <ol className="transcript">
          {segments.map((segment) => (
            <li key={segment.orderNo} className={`seg seg-${segment.confidence.toLowerCase()}`}>
              <button
                type="button"
                className="seg-line"
                onClick={() => {
                  void play(segment);
                }}
                aria-label={`Play from ${timestamp(segment.startMs)}: ${segment.text}`}
              >
                <span className="seg-time">{timestamp(segment.startMs)}</span>{' '}
                <span className="seg-text">{segment.text}</span>
                {segment.confidence === 'REVIEW' ? (
                  <span className="seg-marker"> check</span>
                ) : null}
                {segment.confidence === 'LOW' ? <span className="seg-marker"> unsure</span> : null}
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
