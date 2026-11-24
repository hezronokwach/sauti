import { StaffAccounts } from './StaffAccounts';
import { SystemStatus } from './SystemStatus';
import { WaitingTimes } from './WaitingTimes';

/** The administrator's home: system health, the waiting-time report, then staff accounts. */
export function AdminPage() {
  return (
    <main className="wide">
      <h1>Administration</h1>
      <p className="page-subtitle">System health, waiting times and staff accounts.</p>
      <SystemStatus />
      <WaitingTimes />
      <StaffAccounts />
    </main>
  );
}
