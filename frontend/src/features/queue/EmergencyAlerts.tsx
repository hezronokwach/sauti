import { errorMessage } from '../../api/client';
import { minutesWaiting } from '../visits/time';
import { useAttendEmergency, useTickets } from './api';

/**
 * Emergency arrivals waiting for clinical staff, shown at the top of the triage and doctor
 * screens. One person attends; the alert then goes for everyone.
 */
export function EmergencyAlerts() {
  const tickets = useTickets();
  const attend = useAttendEmergency();
  const waiting = (tickets.data ?? []).filter(
    (t) => t.lane === 'EMERGENCY' && t.status === 'WAITING',
  );

  if (waiting.length === 0 && !attend.error) {
    return null;
  }
  return (
    <section aria-labelledby="emergency-heading" className="emergency" role="alert">
      <h2 id="emergency-heading">Emergency arrivals</h2>
      {attend.error ? <p>{errorMessage(attend.error)}</p> : null}
      <ul>
        {waiting.map((ticket) => (
          <li key={ticket.id}>
            <strong className="ticket-code">{ticket.code}</strong> waiting{' '}
            {minutesWaiting(ticket.arrivedAt)} min{' '}
            <button
              type="button"
              disabled={attend.isPending}
              onClick={() => {
                attend.mutate(ticket.id);
              }}
            >
              Attend {ticket.code}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
