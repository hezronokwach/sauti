import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { apiFetch, errorMessage } from '../../api/client';
import { ROLE_HOME } from '../auth/roles';
import { useCurrentStaff } from '../auth/session';
import { ReasonConfirm } from '../drafts/ReasonConfirm';
import { ReasonSummary } from '../drafts/ReasonSummary';
import { VoiceFields } from '../drafts/VoiceFields';
import { DoctorVisit } from '../notes/DoctorVisit';
import type { PatientDetailsResponse } from '../patients/types';
import { AllergyList } from '../triage/AllergyList';
import { allergyState, usePatientAllergies } from '../triage/api';
import { VitalsForm } from '../triage/VitalsForm';
import { VitalsSummary } from '../triage/VitalsSummary';
import { FinishConsultation } from './FinishConsultation';
import { PatientBanner } from './PatientBanner';
import type { VisitResponse } from './types';

/**
 * One visit: who the patient is, typed vitals and allergies at triage, the reason for visit,
 * and for the doctor the note beside the consultation recording and its transcript.
 */
export function VisitPage() {
  const { visitId = '' } = useParams();
  const navigate = useNavigate();
  const { data: staff } = useCurrentStaff();
  const visit = useQuery({
    queryKey: ['visits', visitId],
    queryFn: () => apiFetch<VisitResponse>(`/api/visits/${visitId}`),
  });
  const patientId = visit.data?.patientId;
  const patient = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => apiFetch<PatientDetailsResponse>(`/api/patients/${patientId ?? ''}`),
    enabled: Boolean(patientId),
  });
  const error = visit.error ?? patient.error;

  return (
    <main className="wide">
      {staff ? (
        <p className="back-link">
          <Link to={ROLE_HOME[staff.role]}>Back to the queue</Link>
        </p>
      ) : null}
      {error ? <p role="alert">{errorMessage(error)}</p> : null}
      {patient.data && visit.data && staff ? (
        <>
          <PatientBanner
            patient={patient.data}
            visit={visit.data}
            showAllergies={staff.role === 'NURSE' || staff.role === 'DOCTOR'}
            action={
              staff.role === 'DOCTOR' && visit.data.status === 'WITH_DOCTOR' ? (
                <FinishConsultation visitId={visitId} />
              ) : undefined
            }
          />
          {staff.role === 'NURSE' ? (
            // Triage in the order the nurse works: the reason, allergies, then vitals.
            <div className="triage-layout">
              <div>
                <ReasonConfirm visitId={visitId} />
                <AllergyList patientId={visit.data.patientId} editable />
              </div>
              {visit.data.status === 'IN_TRIAGE' ? (
                <TriageVitals
                  visitId={visitId}
                  patientId={visit.data.patientId}
                  onRecorded={() => {
                    void navigate(ROLE_HOME[staff.role]);
                  }}
                />
              ) : (
                <VitalsSummary visitId={visitId} />
              )}
            </div>
          ) : null}
          {staff.role === 'DOCTOR' ? (
            <div className="doctor-context">
              <VitalsSummary visitId={visitId} />
              <ReasonSummary visitId={visitId} />
              <AllergyList patientId={visit.data.patientId} editable />
            </div>
          ) : null}
          {staff.role === 'RECEPTIONIST' &&
          visit.data.status !== 'DONE' &&
          visit.data.status !== 'CANCELLED' ? (
            <VoiceFields visitId={visitId} patient={patient.data} />
          ) : null}
          {staff.role === 'DOCTOR' ? (
            <DoctorVisit
              visitId={visitId}
              patientId={visit.data.patientId}
              language={patient.data.preferredLanguage}
            />
          ) : null}
        </>
      ) : (
        !error && <p role="status">Loading...</p>
      )}
    </main>
  );
}

interface TriageVitalsProps {
  visitId: string;
  patientId: string;
  onRecorded: () => void;
}

/**
 * The vitals form, which cannot send the patient on until allergies are answered (an
 * allergy or "no known allergies"). The backend refuses it too.
 */
function TriageVitals({ visitId, patientId, onRecorded }: TriageVitalsProps) {
  const state = allergyState(usePatientAllergies(patientId).data);
  const blockedBy =
    state === 'not-asked'
      ? 'Ask about allergies first. Record what the patient reacts to, or press No known allergies.'
      : state === 'loading'
        ? 'Checking allergies...'
        : undefined;
  return <VitalsForm visitId={visitId} onRecorded={onRecorded} blockedBy={blockedBy} />;
}
