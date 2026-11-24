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
import { VitalsForm } from '../triage/VitalsForm';
import { VitalsSummary } from '../triage/VitalsSummary';
import { initials } from '../ui/initials';
import { FinishConsultation } from './FinishConsultation';
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
          <div className="patient-hero">
            <span className="avatar" aria-hidden="true">
              {initials(patient.data.fullName)}
            </span>
            <h1>
              {patient.data.fullName}{' '}
              <span className="quiet">
                {patient.data.patientNumber}, {patient.data.ageYears} years
              </span>
            </h1>
            <span className="status-pill">
              Queue number {visit.data.queueNumber},{' '}
              {visit.data.status.replaceAll('_', ' ').toLowerCase()}
            </span>
            {staff.role === 'DOCTOR' && visit.data.status === 'WITH_DOCTOR' ? (
              <FinishConsultation visitId={visitId} />
            ) : null}
          </div>
          {staff.role === 'NURSE' || staff.role === 'DOCTOR' ? (
            <div className="columns">
              {staff.role === 'NURSE' && visit.data.status === 'IN_TRIAGE' ? (
                <VitalsForm
                  visitId={visitId}
                  onRecorded={() => {
                    void navigate(ROLE_HOME[staff.role]);
                  }}
                />
              ) : (
                <VitalsSummary visitId={visitId} />
              )}
              <AllergyList patientId={visit.data.patientId} />
              {staff.role === 'NURSE' ? (
                <ReasonConfirm visitId={visitId} />
              ) : (
                <ReasonSummary visitId={visitId} />
              )}
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
