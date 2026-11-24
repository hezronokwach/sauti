import { useEligibility } from './api';
import type { EligibilityStatus } from './api';

const TEXT: Record<EligibilityStatus, string> = {
  NONE: 'SHA: not checked (no national ID)',
  PENDING: 'SHA: checking...',
  ELIGIBLE: 'SHA: eligible',
  INELIGIBLE: 'SHA: not eligible',
  NOT_FOUND: 'SHA: not found',
  UNAVAILABLE: 'SHA: service not answering, trying again',
};

/**
 * The SHA eligibility badge. Informational only: care goes on whatever it says, and the
 * patient can always pay cash.
 */
export function EligibilityBadge({ patientId }: { patientId: string }) {
  const eligibility = useEligibility(patientId);
  if (!eligibility.data) {
    return null;
  }
  const { status } = eligibility.data;
  return (
    <p role="status" className={`badge badge-${status.toLowerCase()}`}>
      {TEXT[status]}
    </p>
  );
}
