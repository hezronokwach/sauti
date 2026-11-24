/** Whole minutes since the patient joined this queue. */
export function minutesWaiting(since: string, now: number = Date.now()): number {
  return Math.max(0, Math.floor((now - new Date(since).getTime()) / 60_000));
}
