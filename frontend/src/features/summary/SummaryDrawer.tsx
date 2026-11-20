import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { playAt } from '../transcript/playAudio';
import { requestSummary } from './api';
import type { ClaimResponse, SummaryResponse } from './types';
import { SECTION_LABELS, failures } from './types';

interface SummaryDrawerProps {
  visitId: string;
  /** Adds these claims to the note; resolves with an error message, or null when added. */
  onAccept: (requestId: string, claims: number[]) => Promise<string | null>;
}

/** A claim can be accepted when it passed the checks and, if it came from an unsure line, was listened to. */
function acceptable(claim: ClaimResponse, played: boolean) {
  return claim.supported && (!claim.checkAudio || played);
}

/**
 * The AI summary: each claim with the transcript words it rests on and the server's checks.
 * The doctor accepts claims one by one, or all that passed; a claim from an unsure line must
 * be listened to first. Claims that failed the checks cannot be accepted (the server refuses
 * them too); the doctor may still write them in the note by hand.
 */
export function SummaryDrawer({ visitId, onAccept }: SummaryDrawerProps) {
  const [summary, setSummary] = useState<SummaryResponse | null>(null);
  const [accepted, setAccepted] = useState<Set<number>>(new Set());
  const [played, setPlayed] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const summarise = async () => {
    setError(null);
    setBusy(true);
    try {
      setSummary(await requestSummary(visitId));
      setAccepted(new Set());
      setPlayed(new Set());
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  };

  const accept = async (positions: number[]) => {
    if (!summary || positions.length === 0) {
      return;
    }
    setError(null);
    setBusy(true);
    const failure = await onAccept(summary.requestId, positions);
    setBusy(false);
    if (failure) {
      setError(failure);
      return;
    }
    setAccepted((before) => new Set([...before, ...positions]));
  };

  const play = async (position: number, claim: ClaimResponse) => {
    if (claim.sessionId === null || claim.startMs === null) {
      return;
    }
    setError(null);
    const failure = await playAt(claim.sessionId, claim.startMs);
    if (failure) {
      setError(failure);
      return;
    }
    setPlayed((before) => new Set([...before, position]));
  };

  const ready = summary
    ? summary.claims
        .map((claim, position) => ({ claim, position }))
        .filter(
          ({ claim, position }) =>
            !accepted.has(position) && acceptable(claim, played.has(position)),
        )
        .map(({ position }) => position)
    : [];

  return (
    <section aria-labelledby="summary-heading" className="panel">
      <h2 id="summary-heading">AI summary</h2>
      <p className="quiet">
        Check each claim against its quote before accepting. The checks catch invented quotes, a
        flipped &quot;no&quot; and wrong numbers, not a wrong meaning.
      </p>
      <button
        type="button"
        disabled={busy}
        onClick={() => {
          void summarise();
        }}
      >
        {busy && !summary ? 'Summarising...' : summary ? 'Summarise again' : 'Summarise transcript'}
      </button>
      {error ? <p role="alert">{error}</p> : null}
      {summary && !summary.available ? <p role="status">{summary.message}</p> : null}
      {summary?.available && summary.claims.length === 0 ? (
        <p role="status">Nothing clinical was found in the transcript.</p>
      ) : null}
      {summary?.available && summary.claims.length > 0 ? (
        <>
          <ul className="claims">
            {summary.claims.map((claim, position) => {
              const done = accepted.has(position);
              const listened = played.has(position);
              return (
                <li
                  key={`${summary.requestId}-${String(position)}`}
                  className={claim.supported ? 'claim' : 'claim claim-failed'}
                >
                  <p className="quiet">{SECTION_LABELS[claim.section]}</p>
                  <p>
                    <strong>{claim.text}</strong>
                  </p>
                  <p className="claim-quote">&ldquo;{claim.quote}&rdquo;</p>
                  {claim.supported ? (
                    <p className="claim-ok">Matches the transcript</p>
                  ) : (
                    <p className="field-error">Not supported: {failures(claim).join('; ')}.</p>
                  )}
                  {claim.supported && claim.checkAudio && !listened ? (
                    <p className="notice">From an unsure line: listen before accepting.</p>
                  ) : null}
                  <div className="actions">
                    {claim.sessionId !== null && claim.startMs !== null ? (
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Play the words for: ${claim.text}`}
                        onClick={() => {
                          void play(position, claim);
                        }}
                      >
                        Play
                      </button>
                    ) : null}
                    {done ? (
                      <span role="status">Added to the note</span>
                    ) : claim.supported ? (
                      <button
                        type="button"
                        disabled={busy || !acceptable(claim, listened)}
                        aria-label={`Accept: ${claim.text}`}
                        onClick={() => {
                          void accept([position]);
                        }}
                      >
                        Accept
                      </button>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
          <button
            type="button"
            disabled={busy || ready.length === 0}
            onClick={() => {
              void accept(ready);
            }}
          >
            Accept all that passed ({ready.length})
          </button>
        </>
      ) : null}
    </section>
  );
}
