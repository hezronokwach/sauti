import type { ReactNode } from 'react';
import type { PatientDetailsResponse } from '../patients/types';
import { allergyState, usePatientAllergies } from '../triage/api';
import { initials } from '../ui/initials';
import type { VisitResponse } from './types';

interface PatientBannerProps {
  patient: PatientDetailsResponse;
  visit: VisitResponse;
  /** Only nurses and doctors may read allergies. */
  showAllergies: boolean;
  /** A button for this stage of the visit, such as Finish consultation. */
  action?: ReactNode;
}

/**
 * Who the patient is, always at the top of the visit: name, number, age, where the visit is,
 * and for clinicians the allergy flag, so nobody has to look for it before prescribing.
 */
export function PatientBanner({ patient, visit, showAllergies, action }: PatientBannerProps) {
  return (
    <div className="patient-hero">
      <span className="avatar" aria-hidden="true">
        {initials(patient.fullName)}
      </span>
      <div className="patient-hero-who">
        <h1>
          {patient.fullName}{' '}
          <span className="quiet">
            {patient.patientNumber}, {patient.ageYears} years
          </span>
        </h1>
        {showAllergies ? <AllergyFlag patientId={patient.id} /> : null}
      </div>
      <span className="status-pill">
        Queue number {visit.queueNumber}, {visit.status.replaceAll('_', ' ').toLowerCase()}
      </span>
      {action}
    </div>
  );
}

function AllergyFlag({ patientId }: { patientId: string }) {
  const allergies = usePatientAllergies(patientId);
  const state = allergyState(allergies.data);
  if (state === 'loading') {
    return null;
  }
  if (state === 'allergic') {
    const names = (allergies.data ?? [])
      .filter((entry) => entry.status === 'ACTIVE' && entry.kind === 'ALLERGY')
      .map((entry) => entry.substance ?? entry.substanceGroup)
      .join(', ');
    return <p className="allergy-flag allergic">Allergy: {names}</p>;
  }
  return state === 'none' ? (
    <p className="allergy-flag none">No known allergies</p>
  ) : (
    <p className="allergy-flag not-asked">Allergies not recorded</p>
  );
}
