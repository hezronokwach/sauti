import { StaffAccounts } from './StaffAccounts';
import { SystemStatus } from './SystemStatus';
import { WaitingTimes } from './WaitingTimes';

/** The administrator's home: system health, staff accounts and the waiting-time report. */
export function AdminPage() {
  return (
    <main className="wide">
      <h1>Administration</h1>
      <p className="page-subtitle">System health, staff accounts and waiting times.</p>
      <SystemStatus />
      <StaffAccounts />
      <WaitingTimes />
    </main>
  );
}
