import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { MIN_QUERY, usePatientSearch } from '../patients/api';
import type { PatientSummaryResponse } from '../patients/types';
import { QueueList } from '../visits/QueueList';
import { useChangeVisitStatus, useOpenVisit } from '../visits/api';
import { RegisterPatientForm } from './RegisterPatientForm';

/**
 * The reception desk: find or register the patient, open a visit (which goes straight to
 * the triage queue), and see today's lists.
 */
export function ReceptionPage() {
  const [query, setQuery] = useState('');
  const [registering, setRegistering] = useState(false);
  const [patient, setPatient] = useState<PatientSummaryResponse | null>(null);
  const search = usePatientSearch(query);

  const choose = (chosen: PatientSummaryResponse) => {
    setPatient(chosen);
    setRegistering(false);
  };

  return (
    <main className="wide">
      <h1>Reception</h1>
      <div className="columns">
        <div>
          <section aria-labelledby="search-heading">
            <h2 id="search-heading">Find a patient</h2>
            <label htmlFor="patient-search">Name, phone, national ID or SH number</label>
            <input
              id="patient-search"
              type="search"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
              }}
            />
            {query.trim().length > 0 && query.trim().length < MIN_QUERY ? (
              <p className="quiet">Type at least {MIN_QUERY} characters.</p>
            ) : null}
            {search.error ? <p role="alert">{errorMessage(search.error)}</p> : null}
            {search.data?.length === 0 ? <p>No patient found.</p> : null}
            {search.data && search.data.length > 0 ? (
              <ul className="results">
                {search.data.map((result) => (
                  <li key={result.id}>
                    {result.fullName}{' '}
                    <span className="quiet">
                      {result.patientNumber}, {result.ageYears} years
                    </span>{' '}
                    <button
                      type="button"
                      onClick={() => {
                        choose(result);
                      }}
                    >
                      Select {result.fullName}
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
            <button
              type="button"
              onClick={() => {
                setRegistering(true);
                setPatient(null);
              }}
            >
              Register a new patient
            </button>
          </section>

          {registering ? <RegisterPatientForm onPatient={choose} /> : null}
          {patient ? (
            <OpenVisitPanel
              patient={patient}
              onDone={() => {
                setPatient(null);
                setQuery('');
              }}
            />
          ) : null}
        </div>

        <div>
          <QueueList
            title="At reception"
            status="AT_RECEPTION"
            action={{ kind: 'move', label: 'Send to triage', to: 'WAITING_TRIAGE' }}
          />
          <QueueList title="Waiting for triage" status="WAITING_TRIAGE" />
        </div>
      </div>
    </main>
  );
}

interface OpenVisitPanelProps {
  patient: PatientSummaryResponse;
  onDone: () => void;
}

function OpenVisitPanel({ patient, onDone }: OpenVisitPanelProps) {
  const [reason, setReason] = useState('');
  const [opened, setOpened] = useState<number | null>(null);
  const open = useOpenVisit();
  const move = useChangeVisitStatus();
  const error = open.error ?? move.error;

  const openAndSend = () => {
    open.mutate(
      { patientId: patient.id, reasonForVisit: reason.trim() || undefined },
      {
        onSuccess: (visit) => {
          move.mutate(
            { visitId: visit.id, to: 'WAITING_TRIAGE' },
            {
              onSuccess: () => {
                setOpened(visit.queueNumber);
              },
            },
          );
        },
      },
    );
  };

  return (
    <section aria-labelledby="open-visit-heading" className="panel">
      <h2 id="open-visit-heading">Open a visit</h2>
      <p>
        <strong>{patient.fullName}</strong> {patient.patientNumber}, {patient.ageYears} years
      </p>
      {opened === null ? (
        <>
          <label htmlFor="reason-for-visit">Reason for the visit (optional)</label>
          <textarea
            id="reason-for-visit"
            maxLength={500}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
            }}
          />
          <div aria-live="polite">{error ? <p role="alert">{errorMessage(error)}</p> : null}</div>
          <button type="button" disabled={open.isPending || move.isPending} onClick={openAndSend}>
            Open visit and send to triage
          </button>
        </>
      ) : (
        <>
          <p role="status">
            Visit opened. Queue number <strong>{opened}</strong>, waiting for triage.
          </p>
          <button type="button" onClick={onDone}>
            Next patient
          </button>
        </>
      )}
    </section>
  );
}
