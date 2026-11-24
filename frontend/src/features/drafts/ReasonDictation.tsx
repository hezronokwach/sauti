import { useEffect, useRef, useState } from 'react';
import { apiPost, errorMessage } from '../../api/client';
import type { ConsentPurpose } from '../consent/api';
import { isAgreed, useConsentHistory } from '../consent/api';
import { ConsentPanel } from '../consent/ConsentPanel';
import type { PreferredLanguage } from '../patients/types';
import { uploader } from '../recorder/background';
import { Recorder } from '../recorder/recorder';
import type { RecorderStatus } from '../recorder/recorder';

const PURPOSE: ConsentPurpose = 'RECORD_RECEPTION';

/** A reason for visit is a sentence or two, so dictation stops by itself after a minute. */
export const MAX_DICTATION_MS = 60_000;

const IDLE: RecorderStatus = { state: 'idle', elapsedMs: 0, level: 0 };

interface ReasonDictationProps {
  visitId: string;
  language: PreferredLanguage;
}

/**
 * The only voice input at reception. Staff open it, get consent, and record one short answer
 * to "why are you here today?". The draft then appears under the field to check and save.
 */
export function ReasonDictation({ visitId, language }: ReasonDictationProps) {
  const history = useConsentHistory(visitId);
  const agreed = isAgreed(history.data, PURPOSE);
  const recorder = useRef<Recorder | null>(null);
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<RecorderStatus>(IDLE);
  const sessionId = useRef<string | null>(null);
  const [outcome, setOutcome] = useState<'heard' | 'silent' | 'erased' | null>(null);
  const [confirmErase, setConfirmErase] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recording = status.state !== 'idle';

  const start = async () => {
    setError(null);
    setOutcome(null);
    setConfirmErase(false);
    try {
      const session = await apiPost<{ id: string }>(`/api/visits/${visitId}/captures`, {
        purpose: PURPOSE,
        languageHint: language === 'SWAHILI' ? 'sw' : 'en',
      });
      sessionId.current = session.id;
      const next = new Recorder(session.id, uploader, (update) => {
        setStatus(update);
        if (update.state === 'recording' && update.elapsedMs >= MAX_DICTATION_MS) {
          void stop();
        }
      });
      next.cut('REASON_FOR_VISIT');
      recorder.current = next;
      await next.start();
    } catch (cause) {
      recorder.current = null;
      setStatus(IDLE);
      setError(
        cause instanceof DOMException
          ? 'The microphone is not available. Allow microphone access or type the reason.'
          : errorMessage(cause),
      );
    }
  };

  const stop = async () => {
    const active = recorder.current;
    recorder.current = null;
    const id = sessionId.current;
    if (!active || !id) {
      return;
    }
    const expectedPieces = await active.stop();
    try {
      await apiPost(`/api/captures/${id}/stop`, { expectedPieces });
      setOutcome(expectedPieces > 0 ? 'heard' : 'silent');
    } catch (cause) {
      setError(errorMessage(cause));
    }
  };

  const erase = async () => {
    const id = sessionId.current;
    if (!id) {
      return;
    }
    try {
      await apiPost(`/api/captures/${id}/erase`);
      setOutcome('erased');
      setConfirmErase(false);
    } catch (cause) {
      setError(errorMessage(cause));
    }
  };

  // A withdrawal while recording stops the recorder (the server stops the session too).
  useEffect(() => {
    if (!agreed && recorder.current) {
      void stop();
    }
  });

  if (!open) {
    return (
      <button
        type="button"
        className="secondary"
        onClick={() => {
          setOpen(true);
        }}
      >
        Use voice
      </button>
    );
  }

  return (
    <div className="reason-dictation">
      <ConsentPanel visitId={visitId} purpose={PURPOSE} language={language} />
      <div aria-live="polite">
        {status.state === 'calibrating' ? (
          <p role="status">Measuring the room. Please stay quiet for a second.</p>
        ) : null}
        {status.state === 'recording' ? (
          <p role="status">Listening. Ask the patient why they came today.</p>
        ) : null}
        {outcome === 'heard' ? (
          <p role="status">A draft will appear below in a few seconds.</p>
        ) : null}
        {outcome === 'silent' ? (
          <p role="status">No speech was heard. Try again or type the reason.</p>
        ) : null}
        {outcome === 'erased' ? <p role="status">The recording was erased.</p> : null}
        {error ? <p role="alert">{error}</p> : null}
      </div>
      <div className="actions">
        {recording ? (
          <button
            type="button"
            disabled={status.state !== 'recording'}
            onClick={() => {
              void stop();
            }}
          >
            Stop
          </button>
        ) : (
          <button
            type="button"
            disabled={!agreed}
            onClick={() => {
              void start();
            }}
          >
            Record reason
          </button>
        )}
        {outcome === 'heard' && !confirmErase ? (
          <button
            type="button"
            className="secondary"
            onClick={() => {
              setConfirmErase(true);
            }}
          >
            Erase recording
          </button>
        ) : null}
        {confirmErase ? (
          <>
            <span>Erase this recording? This cannot be undone.</span>
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
              className="secondary"
              onClick={() => {
                setConfirmErase(false);
              }}
            >
              Keep it
            </button>
          </>
        ) : null}
      </div>
    </div>
  );
}
