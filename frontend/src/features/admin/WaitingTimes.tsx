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
  /** What the stage measures, in plain words, for the explanation under the report. */
  means: string;
}[] = [
  { key: 'deskWait', label: 'Wait for desk', means: 'from taking a ticket to being called' },
  {
    key: 'registration',
    label: 'Registration',
    means: 'from being called to the visit being opened',
  },
  {
    key: 'triageWait',
    label: 'Wait for triage',
    means: 'from the visit being opened to a nurse taking the patient',
  },
  {
    key: 'doctorWait',
    label: 'Wait for doctor',
    means: 'from leaving triage to a doctor taking the patient',
  },
  {
    key: 'doorToDoctor',
    label: 'Door to doctor',
    means: 'the whole time from taking a ticket to a doctor taking the patient',
  },
];

const hour = (h: number) => `${String(h).padStart(2, '0')}:00`;

/**
 * Headline numbers that can be read exactly from the rows: medians of different rows cannot
 * be averaged honestly, so the headline names the hour instead of inventing one overall figure.
 */
function headline(rows: WaitingTimeRow[]) {
  const visits = rows.reduce((sum, row) => sum + row.visits, 0);
  const busiest = rows.reduce<WaitingTimeRow | null>(
    (top, row) => (top === null || row.visits > top.visits ? row : top),
    null,
  );
  const slowest = rows
    .filter((row) => row.doorToDoctor.p90 !== null)
    .reduce<WaitingTimeRow | null>(
      (top, row) =>
        top === null || (row.doorToDoctor.p90 ?? 0) > (top.doorToDoctor.p90 ?? 0) ? row : top,
      null,
    );
  return { visits, busiest, slowest };
}

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
      {report.data && report.data.length > 0 ? <Headline rows={report.data} /> : null}
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
                <td>{hour(row.hour)}</td>
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
      <details className="report-help">
        <summary>What each column measures</summary>
        <dl>
          {STAGES.map((stage) => (
            <div key={stage.key}>
              <dt>{stage.label}</dt>
              <dd>{stage.means}.</dd>
            </div>
          ))}
        </dl>
        <p className="quiet">
          The median is the typical patient: half waited less, half more. The 90th percentile is
          what 9 in 10 patients waited at most. Hours are when the patient arrived.
        </p>
      </details>
    </section>
  );
}

function Headline({ rows }: { rows: WaitingTimeRow[] }) {
  const { visits, busiest, slowest } = headline(rows);
  return (
    <div className="report-headline">
      <p>
        <strong>{visits}</strong> visits
      </p>
      {busiest ? (
        <p>
          Busiest hour <strong>{hour(busiest.hour)}</strong> ({busiest.visits} visits,{' '}
          {LANES[busiest.lane] ?? busiest.lane})
        </p>
      ) : null}
      {slowest ? (
        <p>
          Slowest door to doctor: 9 in 10 seen within{' '}
          <strong>{show(slowest.doorToDoctor.p90)} min</strong> at {hour(slowest.hour)} (
          {LANES[slowest.lane] ?? slowest.lane})
        </p>
      ) : (
        <p>No patient has reached a doctor in these dates.</p>
      )}
    </div>
  );
}
