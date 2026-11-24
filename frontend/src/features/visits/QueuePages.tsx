import { useState } from 'react';
import { InvoicePanel } from '../billing/InvoicePanel';
import { EmergencyAlerts } from '../queue/EmergencyAlerts';
import { QueueList } from './QueueList';
import { QueueStats } from './QueueStats';

/**
 * The nurse's screen: take the next patient, then open them to take vitals; saving the
 * vitals sends the patient on to the doctor.
 */
export function TriagePage() {
  return (
    <main className="wide">
      <h1>Triage</h1>
      <p className="page-subtitle">Take the next patient, then record vitals and allergies.</p>
      <EmergencyAlerts />
      <QueueStats
        waiting={{ status: 'WAITING_TRIAGE', label: 'Waiting', icon: 'users' }}
        active={{ status: 'IN_TRIAGE', label: 'In triage', icon: 'activity' }}
      />
      <div className="columns">
        <QueueList
          title="Waiting for triage"
          status="WAITING_TRIAGE"
          action={{ kind: 'take', label: 'Take' }}
        />
        <QueueList title="In triage" status="IN_TRIAGE" emptyText="Nobody in triage." openable />
      </div>
    </main>
  );
}

/** The doctor's screen: take the next patient, then send them to payment. */
export function DoctorPage() {
  return (
    <main className="wide">
      <h1>Doctor</h1>
      <p className="page-subtitle">Take the next patient and open their visit.</p>
      <EmergencyAlerts />
      <QueueStats
        waiting={{ status: 'WAITING_DOCTOR', label: 'Waiting', icon: 'users' }}
        active={{ status: 'WITH_DOCTOR', label: 'In consultation', icon: 'stethoscope' }}
      />
      <div className="columns">
        <QueueList
          title="Waiting for a doctor"
          status="WAITING_DOCTOR"
          action={{ kind: 'take', label: 'Take' }}
        />
        <QueueList
          title="With a doctor"
          status="WITH_DOCTOR"
          action={{ kind: 'finish', label: 'Finish consultation' }}
          emptyText="Nobody with a doctor."
          openable
        />
      </div>
    </main>
  );
}

/** The cashier's screen: patients waiting to pay, and the bill of the one selected. */
export function BillingPage() {
  const [visitId, setVisitId] = useState<string | null>(null);
  return (
    <main className="wide">
      <h1>Billing</h1>
      <p className="page-subtitle">Open a bill and take cash, M-Pesa or insurance.</p>
      <QueueStats waiting={{ status: 'WAITING_PAYMENT', label: 'Waiting to pay', icon: 'card' }} />
      <div className="columns">
        <QueueList
          title="Waiting for payment"
          status="WAITING_PAYMENT"
          action={{ kind: 'select', label: 'Open bill', onSelect: setVisitId }}
        />
        {visitId ? <InvoicePanel key={visitId} visitId={visitId} /> : null}
      </div>
    </main>
  );
}
