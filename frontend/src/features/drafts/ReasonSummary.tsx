import { errorMessage } from '../../api/client';
import { useReason } from './api';

/** The reason for visit as the doctor sees it, and whether triage checked it with the patient. */
export function ReasonSummary({ visitId }: { visitId: string }) {
  const reason = useReason(visitId);

  if (reason.error) {
    return <p role="alert">{errorMessage(reason.error)}</p>;
  }
  if (!reason.data) {
    return null;
  }
  return (
    <section aria-labelledby="reason-summary-heading">
      <h2 id="reason-summary-heading">Reason for visit</h2>
      <p>{reason.data.reasonForVisit ?? 'No reason was recorded.'}</p>
      {reason.data.reasonForVisit && !reason.data.confirmed ? (
        <p className="notice">Not confirmed at triage. Check it with the patient.</p>
      ) : null}
    </section>
  );
}
