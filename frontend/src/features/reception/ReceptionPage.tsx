import { useEffect, useRef, useState } from 'react';
import { errorMessage } from '../../api/client';
import { MIN_QUERY, usePatientSearch } from '../patients/api';
import type { PatientSummaryResponse } from '../patients/types';
import { TicketDesk } from '../queue/TicketDesk';
import { EligibilityBadge } from '../registry/EligibilityBadge';
import type { TicketResponse } from '../queue/types';
import { QueueList } from '../visits/QueueList';
import { QueueStats } from '../visits/QueueStats';
import { useChangeVisitStatus, useOpenVisit } from '../visits/api';
import { PreRegistrationLookup } from './PreRegistrationLookup';
import type { PreFill } from './PreRegistrationLookup';
import { RegisterPatientForm } from './RegisterPatientForm';

/**
 * The reception desk: give arrival tickets and call the next one, find or register the
 * patient, open a visit for the called ticket (it goes straight to the triage queue), and
 * see today's lists.
 */
export function ReceptionPage() {
  const [query, setQuery] = useState('');
  const [registering, setRegistering] = useState(false);
  const [patient, setPatient] = useState<PatientSummaryResponse | null>(null);
  const [ticket, setTicket] = useState<TicketResponse | null>(null);
  const [preFill, setPreFill] = useState<PreFill | null>(null);
  const search = usePatientSearch(query);

  const choose = (chosen: PatientSummaryResponse) => {
    setPatient(chosen);
    setRegistering(false);
  };

  return (
    <main className="wide">
      <h1>Reception</h1>
      <p className="page-subtitle">Give tickets, find or register patients, and open visits.</p>
      <QueueStats
        waiting={{ status: 'AT_RECEPTION', label: 'At reception', icon: 'desk' }}
        active={{ status: 'WAITING_TRIAGE', label: 'Sent to triage', icon: 'activity' }}
      />
      {/* The desk's order of work: call the ticket, find or register, open the visit. */}
      <div className="reception-layout">
        <div>
          <TicketDesk current={ticket} onCurrent={setTicket} />
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

          <PreRegistrationLookup
            key={ticket?.lane === 'PRE_REGISTERED' ? ticket.code : 'none'}
            ticketCode={ticket?.lane === 'PRE_REGISTERED' ? ticket.code : null}
            onLoaded={(loaded) => {
              setPreFill(loaded);
              setRegistering(true);
              setPatient(null);
            }}
          />
          {registering ? (
            <RegisterPatientForm
              key={preFill?.id ?? 'blank'}
              onPatient={choose}
              initial={preFill?.values}
              patientEntered={preFill ? preFill.fields : []}
            />
          ) : null}
          {patient ? (
            <OpenVisitPanel
              patient={patient}
              ticket={ticket}
              onOpened={() => {
                setTicket(null);
              }}
              initialReason={preFill?.reasonForVisit ?? ''}
              onDone={() => {
                setPatient(null);
                setQuery('');
                setPreFill(null);
              }}
            />
          ) : null}
        </div>

        <div>
          <QueueList
            title="At reception"
            status="AT_RECEPTION"
            action={{ kind: 'move', label: 'Send to triage', to: 'WAITING_TRIAGE' }}
            openable
          />
          <QueueList title="Waiting for triage" status="WAITING_TRIAGE" />
        </div>
      </div>
    </main>
  );
}

interface OpenVisitPanelProps {
  patient: PatientSummaryResponse;
  /** The ticket being served; the visit counts its waiting time from it. */
  ticket: TicketResponse | null;
  /** The reason the patient typed when pre-registering, to check with them. */
  initialReason: string;
  onOpened: () => void;
  onDone: () => void;
}

function OpenVisitPanel({ patient, ticket, initialReason, onOpened, onDone }: OpenVisitPanelProps) {
  const [reason, setReason] = useState(initialReason);
  const [opened, setOpened] = useState<number | null>(null);
  const open = useOpenVisit();
  const move = useChangeVisitStatus();
  const error = open.error ?? move.error;

  const openAndSend = () => {
    open.mutate(
      {
        patientId: patient.id,
        reasonForVisit: reason.trim() || undefined,
        ticketId: ticket?.id,
      },
      {
        onSuccess: (visit) => {
          onOpened();
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

  // The panel appears under the search; bring it into view so the next step is on screen.
  const panel = useRef<HTMLElement>(null);
  useEffect(() => {
    panel.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, [patient.id]);

  return (
    <section ref={panel} aria-labelledby="open-visit-heading" className="panel">
      <h2 id="open-visit-heading">Open a visit</h2>
      <p>
        <strong>{patient.fullName}</strong> {patient.patientNumber}, {patient.ageYears} years
      </p>
      <EligibilityBadge patientId={patient.id} />
      {ticket && opened === null ? <p className="quiet">For ticket {ticket.code}</p> : null}
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
