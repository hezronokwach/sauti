import { StatCard } from '../ui/StatCard';
import type { IconName } from '../ui/Icon';
import { useQueue } from './api';
import { minutesWaiting } from './time';
import type { VisitStatus } from './types';

interface QueueStatsProps {
  /** The queue patients are waiting in; its longest wait is shown too. */
  waiting: { status: VisitStatus; label: string; icon: IconName };
  /** The queue being worked on now, if the screen has one. */
  active?: { status: VisitStatus; label: string; icon: IconName };
}

/** Live counts from the same queues as the lists below, so the two can never disagree. */
export function QueueStats({ waiting, active }: QueueStatsProps) {
  const waitingQueue = useQueue(waiting.status);
  const activeQueue = useQueue(active?.status ?? waiting.status);
  const longest = Math.max(
    0,
    ...(waitingQueue.data ?? []).map((entry) => minutesWaiting(entry.waitingSince)),
  );

  return (
    <div className="stat-grid">
      <StatCard
        featured
        icon={waiting.icon}
        label={waiting.label}
        value={waitingQueue.data?.length ?? '-'}
      />
      {active ? (
        <StatCard icon={active.icon} label={active.label} value={activeQueue.data?.length ?? '-'} />
      ) : null}
      <StatCard icon="clock" label="Longest wait" value={`${String(longest)} min`} />
    </div>
  );
}
