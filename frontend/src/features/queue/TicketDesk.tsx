import { errorMessage } from '../../api/client';
import { useCallNext, useCancelTicket, useIssueTicket, useTickets } from './api';
import type { TicketLane, TicketResponse } from './types';
import { LANE_LABELS } from './types';

const LANES: TicketLane[] = ['NEW', 'RETURNING', 'PRE_REGISTERED', 'EMERGENCY'];

interface TicketDeskProps {
  /** The ticket reception is now serving; the visit opened next is linked to it. */
  current: TicketResponse | null;
  onCurrent: (ticket: TicketResponse | null) => void;
}

/**
 * Reception's ticket desk: give arriving patients a ticket in their lane, then call the next
 * one in fair order (pre-registered and returning first, but no new patient waits more than
 * 15 minutes). Emergency tickets go straight to the nurses and doctors.
 */
export function TicketDesk({ current, onCurrent }: TicketDeskProps) {
  const tickets = useTickets();
  const issue = useIssueTicket();
  const callNext = useCallNext();
  const cancel = useCancelTicket();
  const error = issue.error ?? callNext.error ?? cancel.error ?? tickets.error;
  const waiting = (tickets.data ?? []).filter(
    (t) => t.status === 'WAITING' && t.lane !== 'EMERGENCY',
  );

  return (
    <section aria-labelledby="tickets-heading" className="panel">
      <h2 id="tickets-heading">Tickets</h2>
      <p className="quiet">Give a ticket:</p>
      <div className="actions">
        {LANES.map((lane) => (
          <button
            key={lane}
            type="button"
            className={lane === 'EMERGENCY' ? 'danger' : 'secondary'}
            disabled={issue.isPending}
            onClick={() => {
              issue.mutate(lane);
            }}
          >
            {LANE_LABELS[lane]}
          </button>
        ))}
      </div>
      {issue.data ? (
        <p role="status">
          Ticket <strong className="ticket-code">{issue.data.code}</strong> given
          {issue.data.lane === 'EMERGENCY' ? '; the nurses have been alerted.' : '.'}
        </p>
      ) : null}
      <div aria-live="polite">{error ? <p role="alert">{errorMessage(error)}</p> : null}</div>

      <p>
        Waiting: <strong>{waiting.length}</strong>
      </p>
      {current ? (
        <div className="notice">
          <p>
            Now serving <strong className="ticket-code">{current.code}</strong> (
            {LANE_LABELS[current.lane]}). Find or register the patient, then open the visit.
          </p>
          <button
            type="button"
            className="secondary"
            disabled={cancel.isPending}
            onClick={() => {
              cancel.mutate(current.id, {
                onSuccess: () => {
                  onCurrent(null);
                },
              });
            }}
          >
            Did not come: cancel {current.code}
          </button>
        </div>
      ) : (
        <button
          type="button"
          disabled={callNext.isPending}
          onClick={() => {
            callNext.mutate(undefined, {
              onSuccess: (called) => {
                onCurrent(called);
              },
            });
          }}
        >
          Call next
        </button>
      )}
      {callNext.isSuccess && callNext.data === null && !current ? (
        <p role="status">Nobody is waiting.</p>
      ) : null}
    </section>
  );
}
