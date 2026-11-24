import { useState } from 'react';
import { ApiRequestError, errorMessage } from '../../api/client';
import { allergyState, usePatientAllergies, useRecordAllergy } from './api';

interface AllergyListProps {
  patientId: string;
  /** Nurses and doctors can record what the patient tells them. */
  editable?: boolean;
}

/**
 * The patient's active allergies. Entries marked as entered in error are left out. The nurse
 * asks every patient: either they name what they react to, or "no known allergies" is
 * recorded, so an empty list never means "none".
 */
export function AllergyList({ patientId, editable = false }: AllergyListProps) {
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
        <ul className="allergy-list">
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
      {editable ? <AllergyForm patientId={patientId} asked={active.length > 0} /> : null}
    </section>
  );
}

function AllergyForm({ patientId, asked }: { patientId: string; asked: boolean }) {
  const record = useRecordAllergy(patientId);
  const [substance, setSubstance] = useState('');
  const [reaction, setReaction] = useState('');
  const [error, setError] = useState<string | null>(null);

  const save = async (request: Parameters<typeof record.mutateAsync>[0]) => {
    setError(null);
    try {
      await record.mutateAsync(request);
      setSubstance('');
      setReaction('');
    } catch (failure) {
      setError(failure instanceof ApiRequestError ? failure.message : errorMessage(failure));
    }
  };

  return (
    <form
      className="allergy-form"
      aria-label="Record an allergy"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        if (!substance.trim()) {
          setError('Write what the patient reacts to');
          return;
        }
        void save({ substance: substance.trim(), reaction: reaction.trim() || undefined });
      }}
    >
      <div className="field-row">
        <div>
          <label htmlFor="allergy-substance">Reacts to</label>
          <input
            id="allergy-substance"
            placeholder="For example penicillin"
            maxLength={100}
            value={substance}
            onChange={(event) => {
              setSubstance(event.target.value);
            }}
          />
        </div>
        <div>
          <label htmlFor="allergy-reaction">Reaction (optional)</label>
          <input
            id="allergy-reaction"
            placeholder="For example rash"
            maxLength={200}
            value={reaction}
            onChange={(event) => {
              setReaction(event.target.value);
            }}
          />
        </div>
      </div>
      {error ? <p className="field-error">{error}</p> : null}
      <div className="actions">
        <button type="submit" className="secondary" disabled={record.isPending}>
          Add allergy
        </button>
        {!asked ? (
          <button
            type="button"
            disabled={record.isPending}
            onClick={() => {
              void save({ noKnownAllergies: true });
            }}
          >
            No known allergies
          </button>
        ) : null}
      </div>
    </form>
  );
}

/** A reminder beside the vitals form until the allergy question has been answered. */
export function AllergyReminder({ patientId }: { patientId: string }) {
  const allergies = usePatientAllergies(patientId);
  return allergyState(allergies.data) === 'not-asked' ? (
    <p className="notice">Ask about allergies before sending the patient to the doctor.</p>
  ) : null;
}
