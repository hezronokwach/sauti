import { useQuery } from '@tanstack/react-query';
import { apiFetch, errorMessage } from '../../api/client';
import { StatCard } from '../ui/StatCard';

const REFRESH_MS = 30_000;

/** The system's health (backend: SystemStatusResponse). */
export interface SystemStatusData {
  jobs: {
    queued: number;
    running: number;
    failedLast24Hours: number;
    oldestWaitingSince: string | null;
    failedByType: { type: string; count: number }[];
  };
  backup: {
    lastRunAt: string | null;
    lastResult: 'SUCCESS' | 'FAILED' | null;
    lastSuccessAt: string | null;
    overdue: boolean;
  };
  rateLimitBlocks: { limit: string; count: number }[];
}

const when = (value: string | null) => (value ? new Date(value).toLocaleString('en-KE') : 'never');

function backupProblem(backup: SystemStatusData['backup']): string | null {
  if (backup.lastResult === 'FAILED') return 'The last backup failed.';
  if (backup.lastResult === null) return 'No backup has run yet.';
  return backup.overdue ? 'There has been no good backup in the last 26 hours.' : null;
}

/** Jobs, backups and refused requests, as counts and times only. */
export function SystemStatus() {
  const status = useQuery({
    queryKey: ['admin', 'status'],
    queryFn: () => apiFetch<SystemStatusData>('/api/admin/status'),
    refetchInterval: REFRESH_MS,
  });

  if (status.isPending) return <p role="status">Loading system status...</p>;
  if (status.error) return <p role="alert">{errorMessage(status.error)}</p>;
  const { jobs, backup, rateLimitBlocks } = status.data;
  const problem = backupProblem(backup);

  return (
    <section aria-labelledby="system-status-heading">
      <h2 id="system-status-heading">System status</h2>
      <div className="stat-grid">
        <StatCard
          featured={!problem}
          alert={Boolean(problem)}
          icon="shield"
          label="Backup"
          value={problem ? 'Needs attention' : 'Healthy'}
        />
        <StatCard icon="clock" label="Jobs waiting" value={jobs.queued} />
        <StatCard icon="activity" label="Jobs running" value={jobs.running} />
        <StatCard
          icon="grid"
          label="Failed in 24 hours"
          value={jobs.failedLast24Hours}
          alert={jobs.failedLast24Hours > 0}
        />
      </div>
      <h3>Background jobs</h3>
      {jobs.failedLast24Hours > 0 ? (
        <p role="alert">{jobs.failedLast24Hours} background job(s) failed in the last 24 hours.</p>
      ) : null}
      <p>
        Queued: {jobs.queued}. Running: {jobs.running}. Failed in 24 hours: {jobs.failedLast24Hours}
        . Oldest waiting since: {when(jobs.oldestWaitingSince)}.
      </p>
      {jobs.failedByType.length > 0 ? (
        <ul aria-label="Failed jobs by type">
          {jobs.failedByType.map((failure) => (
            <li key={failure.type}>
              {failure.type}: {failure.count}
            </li>
          ))}
        </ul>
      ) : null}
      <h3>Backup</h3>
      {problem ? <p role="alert">{problem}</p> : null}
      <p>
        Last run: {when(backup.lastRunAt)} ({backup.lastResult ?? 'none'}). Last good backup:{' '}
        {when(backup.lastSuccessAt)}.
      </p>
      <h3>Refused requests</h3>
      {rateLimitBlocks.length > 0 ? (
        <ul aria-label="Refused requests by limit">
          {rateLimitBlocks.map((block) => (
            <li key={block.limit}>
              {block.limit}: {block.count}
            </li>
          ))}
        </ul>
      ) : (
        <p className="quiet">None in the last 24 hours.</p>
      )}
    </section>
  );
}
