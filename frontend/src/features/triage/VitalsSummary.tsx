import { errorMessage } from '../../api/client';
import { useVisitVitals } from './api';

/** The latest vitals from triage, for the doctor. */
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
        <dl>
          <dt>Temperature</dt>
          <dd>{latest.temperatureC} °C</dd>
          <dt>Pulse</dt>
          <dd>{latest.pulseBpm} a minute</dd>
          <dt>Breathing rate</dt>
          <dd>{latest.respiratoryRate} a minute</dd>
          <dt>Blood pressure</dt>
          <dd>
            {latest.systolicMmhg}/{latest.diastolicMmhg} mmHg
          </dd>
          <dt>Oxygen saturation</dt>
          <dd>{latest.spo2Percent}%</dd>
          {latest.weightKg !== null ? (
            <>
              <dt>Weight</dt>
              <dd>{latest.weightKg} kg</dd>
            </>
          ) : null}
          {latest.heightCm !== null ? (
            <>
              <dt>Height</dt>
              <dd>{latest.heightCm} cm</dd>
            </>
          ) : null}
        </dl>
      ) : (
        <p>No vitals were taken at triage.</p>
      )}
    </section>
  );
}
