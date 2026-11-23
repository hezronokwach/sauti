import { useState } from 'react';
import { Link } from 'react-router';
import { ApiRequestError, errorMessage } from '../../api/client';
import { useChangeVisitStatus, useFinishConsultation, useQueue, useTakeVisit } from './api';
import { minutesWaiting } from './time';
import type { QueueEntryResponse, VisitStatus } from './types';

/** What the button on each row does: take the patient, or move the visit on. */
export type QueueAction =
  | { kind: 'take'; label: string }
  | { kind: 'move'; label: string; to: VisitStatus }
  | { kind: 'finish'; label: string }
  | { kind: 'select'; label: string; onSelect: (visitId: string) => void };

interface QueueListProps {
  title: string;
  status: VisitStatus;
  action?: QueueAction;
  emptyText?: string;
  /** Show an Open link to each visit's page (for patients the viewer is looking after). */
  openable?: boolean;
}

const TAKEN = 'Someone else has already taken this patient.';

/**
 * One queue, refreshed every 5 seconds: queue number, name and waiting time only, so it
 * can be on screen in a shared space. Taking a patient someone else took first shows a
 * plain message and refreshes the list.
 */
export function QueueList({
  title,
  status,
  action,
  emptyText = 'Nobody waiting.',
  openable = false,
}: QueueListProps) {
  const queue = useQueue(status);
  const take = useTakeVisit();
  const move = useChangeVisitStatus();
  const finish = useFinishConsultation();
  const [message, setMessage] = useState<string | null>(null);

  const act = (entry: QueueEntryResponse) => {
    setMessage(null);
    const onError = (error: Error) => {
      const taken =
        action?.kind === 'take' && error instanceof ApiRequestError && error.status === 409;
      setMessage(taken ? TAKEN : errorMessage(error));
    };
    if (action?.kind === 'take') {
      take.mutate(entry.visitId, { onError });
    } else if (action?.kind === 'move') {
      move.mutate({ visitId: entry.visitId, to: action.to }, { onError });
    } else if (action?.kind === 'finish') {
      finish.mutate(entry.visitId, { onError });
    } else if (action?.kind === 'select') {
      action.onSelect(entry.visitId);
    }
  };
  const busy = take.isPending || move.isPending || finish.isPending;
  const headingId = `queue-${status}`;

  return (
    <section aria-labelledby={headingId} className="queue">
      <h2 id={headingId}>
        {title} <span className="count">({queue.data?.length ?? 0})</span>
      </h2>
      <div aria-live="polite">{message ? <p role="alert">{message}</p> : null}</div>
      {queue.isPending ? <p role="status">Loading...</p> : null}
      {queue.error ? <p role="alert">{errorMessage(queue.error)}</p> : null}
      {queue.data?.length === 0 ? <p className="quiet">{emptyText}</p> : null}
      {queue.data && queue.data.length > 0 ? (
        <table>
          <thead>
            <tr>
              <th scope="col">No.</th>
              <th scope="col">Patient</th>
              <th scope="col">Waiting</th>
              {action || openable ? <th scope="col">Action</th> : null}
            </tr>
          </thead>
          <tbody>
            {queue.data.map((entry) => (
              <tr key={entry.visitId}>
                <td>{entry.queueNumber}</td>
                <td>
                  {entry.patientName} <span className="quiet">{entry.patientNumber}</span>
                </td>
                <td>{minutesWaiting(entry.waitingSince)} min</td>
                {action || openable ? (
                  <td>
                    {openable ? (
                      <Link
                        to={`/visits/${entry.visitId}`}
                        aria-label={`Open ${entry.patientName}`}
                      >
                        Open
                      </Link>
                    ) : null}{' '}
                    {action ? (
                      <button
                        type="button"
                        disabled={busy}
                        aria-label={`${action.label} ${entry.patientName}`}
                        onClick={() => {
                          act(entry);
                        }}
                      >
                        {action.label}
                      </button>
                    ) : null}
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}
