import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { apiFetch, errorMessage } from '../../api/client';
import { ROLE_HOME } from '../auth/roles';
import { useCurrentStaff } from '../auth/session';
import type { PatientDetailsResponse } from '../patients/types';
import { RecordingPanel } from '../recorder/RecordingPanel';
import { AllergyList } from '../triage/AllergyList';
import { VitalsForm } from '../triage/VitalsForm';
import { VitalsSummary } from '../triage/VitalsSummary';
import type { VisitResponse } from './types';

/**
 * One visit: who the patient is, vitals with the allergy list at triage, and recording with
 * consent. The note editor (issue 33) joins this page.
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
        <p>
          <Link to={ROLE_HOME[staff.role]}>Back to the queue</Link>
        </p>
      ) : null}
      {error ? <p role="alert">{errorMessage(error)}</p> : null}
      {patient.data && visit.data && staff ? (
        <>
          <h1>
            {patient.data.fullName}{' '}
            <span className="quiet">
              {patient.data.patientNumber}, {patient.data.ageYears} years
            </span>
          </h1>
          <p>
            Queue number {visit.data.queueNumber},{' '}
            {visit.data.status.replaceAll('_', ' ').toLowerCase()}
          </p>
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
            </div>
          ) : null}
          <RecordingPanel
            visitId={visitId}
            purpose={staff.role === 'RECEPTIONIST' ? 'RECORD_RECEPTION' : 'RECORD_CONSULTATION'}
            language={patient.data.preferredLanguage}
          />
        </>
      ) : (
        !error && <p role="status">Loading...</p>
      )}
    </main>
  );
}
