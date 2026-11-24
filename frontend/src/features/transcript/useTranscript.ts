import { useEffect, useRef, useState } from 'react';
import { ApiRequestError, apiFetch, errorMessage } from '../../api/client';

export type Confidence = 'HIGH' | 'REVIEW' | 'LOW';

/** One transcript line (backend: TranscriptSegmentResponse). */
export interface TranscriptSegmentResponse {
  orderNo: number;
  startMs: number;
  endMs: number;
  text: string;
  confidence: Confidence;
}

/** New lines are fetched this often while the transcript is live. */
export const TRANSCRIPT_POLL_MS = 2_000;

export interface TranscriptState {
  segments: TranscriptSegmentResponse[];
  erased: boolean;
  error: string | null;
}

/**
 * A recording's transcript, kept up to date: the first fetch reads everything (and is the
 * one the server records as opening the transcript); while {@code live}, each poll asks only
 * for lines after the last one received.
 */
export function useTranscript(sessionId: string | null, live: boolean): TranscriptState {
  const [state, setState] = useState<TranscriptState>({ segments: [], erased: false, error: null });
  const last = useRef<number | null>(null);

  useEffect(() => {
    last.current = null;
    setState({ segments: [], erased: false, error: null });
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId) {
      return undefined;
    }
    let cancelled = false;
    const fetchNew = async () => {
      const after = last.current === null ? '' : `?after=${String(last.current)}`;
      try {
        const lines = await apiFetch<TranscriptSegmentResponse[]>(
          `/api/captures/${sessionId}/segments${after}`,
        );
        if (cancelled) {
          return;
        }
        const newest = lines.at(-1);
        if (newest) {
          last.current = newest.orderNo;
        } else if (last.current === null) {
          last.current = -1;
        }
        setState((current) => ({
          segments: [...current.segments, ...lines],
          erased: false,
          error: null,
        }));
      } catch (error) {
        if (cancelled) {
          return;
        }
        if (error instanceof ApiRequestError && error.status === 410) {
          setState({ segments: [], erased: true, error: null });
        } else {
          setState((current) => ({ ...current, error: errorMessage(error) }));
        }
      }
    };
    void fetchNew();
    if (!live) {
      return () => {
        cancelled = true;
      };
    }
    const timer = setInterval(() => {
      void fetchNew();
    }, TRANSCRIPT_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [sessionId, live]);

  return state;
}
