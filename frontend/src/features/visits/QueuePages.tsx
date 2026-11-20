import { QueueList } from './QueueList';

/**
 * The nurse's screen: take the next patient, then open them to take vitals; saving the
 * vitals sends the patient on to the doctor.
 */
export function TriagePage() {
  return (
    <main className="wide">
      <h1>Triage</h1>
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
      <div className="columns">
        <QueueList
          title="Waiting for a doctor"
          status="WAITING_DOCTOR"
          action={{ kind: 'take', label: 'Take' }}
        />
        <QueueList
          title="With a doctor"
          status="WITH_DOCTOR"
          action={{ kind: 'move', label: 'Send to payment', to: 'WAITING_PAYMENT' }}
          emptyText="Nobody with a doctor."
          openable
        />
      </div>
    </main>
  );
}

/** The cashier's screen: patients waiting to pay; marking paid ends the visit. */
export function BillingPage() {
  return (
    <main className="wide">
      <h1>Billing</h1>
      <QueueList
        title="Waiting for payment"
        status="WAITING_PAYMENT"
        action={{ kind: 'move', label: 'Mark paid', to: 'DONE' }}
      />
    </main>
  );
}
