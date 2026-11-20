import { errorMessage } from '../../api/client';
import { usePatientAllergies } from './api';

/**
 * The patient's active allergies, beside the vitals form so the nurse sees them while the
 * patient is in front of them. Entries marked as entered in error are left out.
 */
export function AllergyList({ patientId }: { patientId: string }) {
  const allergies = usePatientAllergies(patientId);

  if (allergies.error) {
    return <p role="alert">{errorMessage(allergies.error)}</p>;
  }
  if (!allergies.data) {
    return <p role="status">Loading allergies...</p>;
  }
  const active = allergies.data.filter((entry) => entry.status === 'ACTIVE');
  const known = active.filter((entry) => entry.kind === 'ALLERGY');

  return (
    <section aria-labelledby="allergies-heading">
      <h2 id="allergies-heading">Allergies</h2>
      {known.length > 0 ? (
        <ul>
          {known.map((entry) => (
            <li key={entry.id}>
              <strong>{entry.substance ?? entry.substanceGroup}</strong>
              {entry.reaction ? `: ${entry.reaction}` : null}
            </li>
          ))}
        </ul>
      ) : active.length > 0 ? (
        <p>No known allergies.</p>
      ) : (
        <p className="field-error">Allergies not yet asked. Ask the patient.</p>
      )}
    </section>
  );
}
