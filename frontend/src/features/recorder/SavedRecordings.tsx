import { useState } from 'react';
import { apiPost, errorMessage } from '../../api/client';
import { TranscriptPanel } from '../transcript/TranscriptPanel';
import { useConsultationRecordings, useRefreshRecordings } from './recordingsApi';

interface SavedRecordingsProps {
  visitId: string;
  /** The recording already shown by the panel above, left out of this list. */
  currentSessionId: string | null;
}

/**
 * This visit's saved consultation recordings, so they survive a page reload. A transcript is
 * only fetched when the doctor opens it, because each opening is written to the access log.
 */
export function SavedRecordings({ visitId, currentSessionId }: SavedRecordingsProps) {
  const recordings = useConsultationRecordings(visitId);
  const refresh = useRefreshRecordings(visitId);
  const [openId, setOpenId] = useState<string | null>(null);
  const [confirmEraseId, setConfirmEraseId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const saved = recordings.data?.filter((recording) => recording.id !== currentSessionId) ?? [];

  const erase = async (id: string) => {
    setError(null);
    try {
      await apiPost(`/api/captures/${id}/erase`);
      setConfirmEraseId(null);
      setOpenId((current) => (current === id ? null : current));
      await refresh();
    } catch (cause) {
      setError(errorMessage(cause));
    }
  };

  if (recordings.error) {
    return <p role="alert">{errorMessage(recordings.error)}</p>;
  }
  if (saved.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby="saved-recordings-heading" className="panel">
      <h2 id="saved-recordings-heading">Saved recordings for this visit</h2>
      {error ? <p role="alert">{error}</p> : null}
      <ul className="saved-recordings">
        {saved.map((recording) => {
          const time = new Date(recording.startedAt).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
          });
          return (
            <li key={recording.id}>
              <div className="actions">
                <span>
                  Recorded at {time}
                  {recording.status === 'COMPLETE' ? '' : ' (still transcribing)'}
                </span>
                <button
                  type="button"
                  className="secondary"
                  aria-expanded={openId === recording.id}
                  onClick={() => {
                    setOpenId(openId === recording.id ? null : recording.id);
                  }}
                >
                  {openId === recording.id ? 'Hide transcript' : 'Open transcript'}
                </button>
                {confirmEraseId === recording.id ? (
                  <>
                    <span>Erase this recording and its transcript? This cannot be undone.</span>
                    <button
                      type="button"
                      onClick={() => {
                        void erase(recording.id);
                      }}
                    >
                      Yes, erase
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        setConfirmEraseId(null);
                      }}
                    >
                      Keep it
                    </button>
                  </>
                ) : (
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => {
                      setConfirmEraseId(recording.id);
                    }}
                  >
                    Erase recording
                  </button>
                )}
              </div>
              {openId === recording.id ? (
                <TranscriptPanel sessionId={recording.id} live={recording.status !== 'COMPLETE'} />
              ) : null}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
