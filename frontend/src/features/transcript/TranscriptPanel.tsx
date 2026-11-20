import { useRef, useState } from 'react';
import { ApiRequestError, errorMessage, toApiRequestError } from '../../api/client';
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
  const audio = useRef<HTMLAudioElement | null>(null);

  const play = async (segment: TranscriptSegmentResponse) => {
    setPlayError(null);
    try {
      const response = await fetch(
        `/api/captures/${sessionId}/audio?atMs=${String(segment.startMs)}`,
        { credentials: 'include' },
      );
      if (!response.ok) {
        throw await toApiRequestError(response);
      }
      const offsetMs = Number(response.headers.get('X-Piece-Offset-Ms') ?? '0');
      const url = URL.createObjectURL(await response.blob());
      audio.current?.pause();
      const player = new Audio(url);
      audio.current = player;
      player.currentTime = Math.max(0, (segment.startMs - offsetMs) / 1000);
      player.onended = () => {
        URL.revokeObjectURL(url);
      };
      await player.play();
    } catch (cause) {
      setPlayError(
        cause instanceof ApiRequestError ? errorMessage(cause) : 'The audio could not be played',
      );
    }
  };

  if (erased) {
    return (
      <section aria-labelledby="transcript-heading" className="panel">
        <h2 id="transcript-heading">Transcript</h2>
        <p role="status">This recording was erased.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="transcript-heading" className="panel">
      <h2 id="transcript-heading">Transcript</h2>
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
