import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { apiFetch, errorMessage } from '../../api/client';

interface Stage {
  median: number | null;
  p90: number | null;
}

/** One lane and hour of arrival (backend: WaitingTimeRow). */
interface WaitingTimeRow {
  lane: string;
  hour: number;
  visits: number;
  deskWait: Stage;
  registration: Stage;
  triageWait: Stage;
  doctorWait: Stage;
  doorToDoctor: Stage;
}

const LANES: Record<string, string> = {
  PRE_REGISTERED: 'Pre-registered',
  RETURNING: 'Returning',
  NEW: 'New',
  EMERGENCY: 'Emergency',
  NO_TICKET: 'No ticket',
};

const STAGES: {
  key: keyof Pick<
    WaitingTimeRow,
    'deskWait' | 'registration' | 'triageWait' | 'doctorWait' | 'doorToDoctor'
  >;
  label: string;
}[] = [
  { key: 'deskWait', label: 'Wait for desk' },
  { key: 'registration', label: 'Registration' },
  { key: 'triageWait', label: 'Wait for triage' },
  { key: 'doctorWait', label: 'Wait for doctor' },
  { key: 'doorToDoctor', label: 'Door to doctor' },
];

function today(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

const show = (minutes: number | null) => (minutes === null ? '-' : minutes.toFixed(1));

/**
 * The administrator's waiting-time report: for each lane and hour of arrival, the median and
 * 90th-percentile minutes of every stage, so improvements can be shown and not just claimed.
 */
export function WaitingTimes() {
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const report = useQuery({
    queryKey: ['admin', 'waiting-times', from, to],
    queryFn: () =>
      apiFetch<WaitingTimeRow[]>(
        `/api/admin/reports/waiting-times?${new URLSearchParams({ from, to }).toString()}`,
      ),
  });

  return (
    <section aria-labelledby="waiting-heading">
      <h2 id="waiting-heading">Waiting times</h2>
      <div className="actions">
        <label htmlFor="report-from">From</label>
        <input
          id="report-from"
          type="date"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
          }}
        />
        <label htmlFor="report-to">To</label>
        <input
          id="report-to"
          type="date"
          value={to}
          onChange={(event) => {
            setTo(event.target.value);
          }}
        />
      </div>
      <p className="quiet">
        Minutes: median / 90th percentile. A dash means no visit reached that stage.
      </p>
      {report.error ? <p role="alert">{errorMessage(report.error)}</p> : null}
      {report.data && report.data.length === 0 ? <p>No visits in these dates.</p> : null}
      {report.data && report.data.length > 0 ? (
        <table>
          <thead>
            <tr>
              <th>Lane</th>
              <th>Hour</th>
              <th>Visits</th>
              {STAGES.map((stage) => (
                <th key={stage.key}>{stage.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {report.data.map((row) => (
              <tr key={`${row.lane}-${String(row.hour)}`}>
                <td>{LANES[row.lane] ?? row.lane}</td>
                <td>{`${String(row.hour).padStart(2, '0')}:00`}</td>
                <td>{row.visits}</td>
                {STAGES.map((stage) => (
                  <td key={stage.key}>
                    {show(row[stage.key].median)} / {show(row[stage.key].p90)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}
