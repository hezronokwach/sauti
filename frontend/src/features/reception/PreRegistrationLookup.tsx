import { useState } from 'react';
import { apiFetch, errorMessage } from '../../api/client';
import type { PreferredLanguage, Sex } from '../patients/types';
import type { RegisterInitialValues } from './RegisterPatientForm';

/** A pre-registration as reception receives it (backend: PreRegistrationResponse). */
interface PreRegistrationResponse {
  id: string;
  ticketId: string;
  fullName: string;
  dateOfBirth: string;
  sex: Sex;
  phone: string;
  reasonForVisit: string | null;
  preferredLanguage: PreferredLanguage;
}

/** What reception gets to pre-fill the forms with. */
export interface PreFill {
  id: string;
  values: RegisterInitialValues;
  /** The form fields the patient typed. */
  fields: (keyof RegisterInitialValues)[];
  reasonForVisit: string | null;
}

interface PreRegistrationLookupProps {
  /** The called ticket's code when it is a pre-registered one; the parent remounts on change. */
  ticketCode: string | null;
  onLoaded: (preFill: PreFill) => void;
}

/**
 * Reception opens a pre-registration by its ticket code (the called P ticket fills it in) to
 * pre-fill the registration form. Everything in it was typed by the patient and is marked
 * so reception checks it with them.
 */
export function PreRegistrationLookup({ ticketCode, onLoaded }: PreRegistrationLookupProps) {
  const [code, setCode] = useState(ticketCode ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const open = async () => {
    setError(null);
    setBusy(true);
    try {
      const entry = await apiFetch<PreRegistrationResponse>(
        `/api/pre-registrations/${encodeURIComponent(code.trim())}`,
      );
      onLoaded({
        id: entry.id,
        values: {
          fullName: entry.fullName,
          dateOfBirth: entry.dateOfBirth,
          sex: entry.sex,
          phone: entry.phone,
          preferredLanguage: entry.preferredLanguage,
        },
        fields: ['fullName', 'dateOfBirth', 'sex', 'phone', 'preferredLanguage'],
        reasonForVisit: entry.reasonForVisit,
      });
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form
      className="actions"
      aria-label="Open a pre-registration"
      onSubmit={(event) => {
        event.preventDefault();
        void open();
      }}
    >
      <label htmlFor="pre-registration-code">Pre-registration ticket</label>
      <input
        id="pre-registration-code"
        placeholder="P-001"
        maxLength={10}
        value={code}
        onChange={(event) => {
          setCode(event.target.value);
        }}
      />
      <button type="submit" disabled={busy || code.trim() === ''}>
        Open
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
  );
}
