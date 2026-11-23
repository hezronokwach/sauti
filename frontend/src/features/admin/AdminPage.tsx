import { StaffAccounts } from './StaffAccounts';
import { WaitingTimes } from './WaitingTimes';

/** The administrator's home: staff accounts and the waiting-time report. */
export function AdminPage() {
  return (
    <main className="wide">
      <h1>Administration</h1>
      <StaffAccounts />
      <WaitingTimes />
    </main>
  );
}
