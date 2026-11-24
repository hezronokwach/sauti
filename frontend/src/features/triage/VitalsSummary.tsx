import { errorMessage } from '../../api/client';
import { useVisitVitals } from './api';

/** The latest vitals from triage, for the doctor, as one compact strip. */
export function VitalsSummary({ visitId }: { visitId: string }) {
  const vitals = useVisitVitals(visitId);

  if (vitals.error) {
    return <p role="alert">{errorMessage(vitals.error)}</p>;
  }
  if (!vitals.data) {
    return <p role="status">Loading vitals...</p>;
  }
  const latest = vitals.data.at(-1);

  return (
    <section aria-labelledby="vitals-summary-heading">
      <h2 id="vitals-summary-heading">Vitals</h2>
      {latest ? (
        <dl className="vitals-strip">
          <div>
            <dt>Temperature</dt>
            <dd>{latest.temperatureC} °C</dd>
          </div>
          <div>
            <dt>Pulse</dt>
            <dd>{latest.pulseBpm} /min</dd>
          </div>
          <div>
            <dt>Breathing</dt>
            <dd>{latest.respiratoryRate} /min</dd>
          </div>
          <div>
            <dt>Blood pressure</dt>
            <dd>
              {latest.systolicMmhg}/{latest.diastolicMmhg}
            </dd>
          </div>
          <div>
            <dt>Oxygen</dt>
            <dd>{latest.spo2Percent}%</dd>
          </div>
          {latest.weightKg !== null ? (
            <div>
              <dt>Weight</dt>
              <dd>{latest.weightKg} kg</dd>
            </div>
          ) : null}
          {latest.heightCm !== null ? (
            <div>
              <dt>Height</dt>
              <dd>{latest.heightCm} cm</dd>
            </div>
          ) : null}
        </dl>
      ) : (
        <p>No vitals were taken at triage.</p>
      )}
    </section>
  );
}
