import { useEffect, useRef, useState } from 'react';
import { apiFetch, apiPost, errorMessage } from '../../api/client';
import type { ConsentPurpose } from '../consent/api';
import { isAgreed, useConsentHistory } from '../consent/api';
import { ConsentPanel } from '../consent/ConsentPanel';
import type { PreferredLanguage } from '../patients/types';
import { TranscriptPanel } from '../transcript/TranscriptPanel';
import { onUploadStatus, uploader } from './background';
import { Recorder } from './recorder';
import { useRefreshRecordings } from './recordingsApi';
import { SavedRecordings } from './SavedRecordings';
import type { RecorderStatus } from './recorder';
import type { UploadStatus } from './uploader';

interface RecordingPanelProps {
  visitId: string;
  purpose: ConsentPurpose;
  language: PreferredLanguage;
  /** Moves to the AI summary; left out when the note is already signed. */
  onGoToSummary?: () => void;
}

interface CaptureSessionResponse {
  id: string;
  status: string;
}

function clock(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  return `${String(Math.floor(seconds / 60))}:${String(seconds % 60).padStart(2, '0')}`;
}

/**
 * Consent, then Record and Stop. Recording stops by itself if consent is withdrawn.
 * Pieces wait on the device until the server has them, so a dropped connection or a
 * closed tab loses nothing.
 */
export function RecordingPanel({ visitId, purpose, language, onGoToSummary }: RecordingPanelProps) {
  const refreshRecordings = useRefreshRecordings(visitId);
  const history = useConsentHistory(visitId);
  const agreed = isAgreed(history.data, purpose);
  const recorder = useRef<Recorder | null>(null);
  const sessionId = useRef<string | null>(null);
  const [session, setSession] = useState<{ id: string; status: string } | null>(null);
  const [confirmErase, setConfirmErase] = useState(false);
  const [status, setStatus] = useState<RecorderStatus>({ state: 'idle', elapsedMs: 0, level: 0 });
  const [uploads, setUploads] = useState<UploadStatus>({
    pending: 0,
    offline: false,
    lastError: null,
  });
  const [error, setError] = useState<string | null>(null);
  const recording = status.state !== 'idle';

  useEffect(() => onUploadStatus(setUploads), []);

  const start = async () => {
    setError(null);
    // Only the session this attempt opens; an earlier recording is never touched here.
    let opened: string | null = null;
    try {
      const session = await apiPost<CaptureSessionResponse>(`/api/visits/${visitId}/captures`, {
        purpose,
        languageHint: language === 'SWAHILI' ? 'sw' : 'en',
      });
      opened = session.id;
      sessionId.current = session.id;
      setSession({ id: session.id, status: 'RECORDING' });
      setConfirmErase(false);
      recorder.current = new Recorder(session.id, uploader, setStatus);
      await recorder.current.start();
    } catch (cause) {
      setError(
        cause instanceof DOMException
          ? 'The microphone is not available. Allow microphone access and try again.'
          : errorMessage(cause),
      );
      recorder.current = null;
      setStatus({ state: 'idle', elapsedMs: 0, level: 0 });
      // The session opened before the microphone failed holds no audio. Left open, the
      // server would refuse every later Record for this visit, so it is closed here.
      if (opened) {
        sessionId.current = null;
        setSession(null);
        await apiPost(`/api/captures/${opened}/erase`).catch(() => undefined);
        await refreshRecordings();
      }
    }
  };

  const stop = async () => {
    const active = recorder.current;
    const id = sessionId.current;
    recorder.current = null;
    if (!active || !id) {
      return;
    }
    const expectedPieces = await active.stop();
    try {
      const stopped = await apiPost<CaptureSessionResponse>(`/api/captures/${id}/stop`, {
        expectedPieces,
      });
      setSession({ id, status: stopped.status });
      await refreshRecordings();
    } catch (cause) {
      setError(errorMessage(cause));
    }
  };

  const erase = async () => {
    const id = sessionId.current;
    if (!id) {
      return;
    }
    if (recorder.current) {
      await stop();
    }
    try {
      await apiPost(`/api/captures/${id}/erase`);
      setSession({ id, status: 'ERASED' });
      setConfirmErase(false);
      await refreshRecordings();
    } catch (cause) {
      setError(errorMessage(cause));
    }
  };

  // After stopping, check the session until every piece is transcribed, so the
  // transcript keeps filling in until it is complete.
  useEffect(() => {
    if (session?.status !== 'STOPPED') {
      return undefined;
    }
    const timer = setInterval(() => {
      void apiFetch<CaptureSessionResponse>(`/api/captures/${session.id}`)
        .then((current) => {
          if (current.status !== 'STOPPED') {
            setSession({ id: session.id, status: current.status });
          }
        })
        .catch(() => undefined);
    }, 2_000);
    return () => {
      clearInterval(timer);
    };
  }, [session]);

  // A withdrawal while recording stops the recorder (the server stops the session too).
  useEffect(() => {
    if (!agreed && recorder.current) {
      void stop();
    }
  });

  return (
    <div className="recording">
      <ConsentPanel visitId={visitId} purpose={purpose} language={language} />
      <section aria-labelledby="record-heading" className="panel">
        <h2 id="record-heading">Recording</h2>
        <div aria-live="polite">
          {error ? <p role="alert">{error}</p> : null}
          {status.state === 'calibrating' ? (
            <p role="status">Measuring the room. Please stay quiet for a second.</p>
          ) : null}
          {status.state === 'recording' ? (
            <p role="status">
              Recording <strong>{clock(status.elapsedMs)}</strong>
            </p>
          ) : null}
          {uploads.offline ? (
            <p className="notice">
              No connection. The recording is safe on this device and will be sent.
            </p>
          ) : null}
          {uploads.pending > 0 ? (
            <p className="quiet">{uploads.pending} piece(s) waiting to send.</p>
          ) : null}
          {uploads.lastError ? <p role="alert">A piece was refused: {uploads.lastError}</p> : null}
        </div>
        {recording ? (
          <>
            <meter min={0} max={0.3} value={status.level} aria-label="Microphone level" />
            <button
              type="button"
              disabled={status.state !== 'recording'}
              onClick={() => {
                void stop();
              }}
            >
              Stop recording
            </button>
          </>
        ) : (
          <button
            type="button"
            disabled={!agreed}
            onClick={() => {
              void start();
            }}
          >
            Record
          </button>
        )}
        {session && session.status !== 'ERASED' && !recording ? (
          confirmErase ? (
            <div className="actions">
              <span>Erase this recording and its transcript? This cannot be undone.</span>
              <button
                type="button"
                onClick={() => {
                  void erase();
                }}
              >
                Yes, erase
              </button>
              <button
                type="button"
                onClick={() => {
                  setConfirmErase(false);
                }}
              >
                Keep it
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => {
                setConfirmErase(true);
              }}
            >
              Erase recording
            </button>
          )
        ) : null}
        {!agreed && !recording ? (
          <p className="quiet">Recording needs the patient&apos;s consent.</p>
        ) : null}
      </section>
      {session ? (
        <TranscriptPanel
          sessionId={session.id}
          live={recording || session.status === 'RECORDING' || session.status === 'STOPPED'}
        />
      ) : null}
      {!recording && session?.status === 'STOPPED' ? (
        <p role="status" className="notice">
          Finishing the transcript. The next step appears here when it is ready.
        </p>
      ) : null}
      {!recording && session?.status === 'COMPLETE' && onGoToSummary ? (
        <div className="notice">
          <p>
            <strong>Next:</strong> check the transcript, then summarise it into your note.
          </p>
          <button type="button" onClick={onGoToSummary}>
            Go to AI summary
          </button>
        </div>
      ) : null}
      <SavedRecordings visitId={visitId} currentSessionId={session?.id ?? null} />
    </div>
  );
}
