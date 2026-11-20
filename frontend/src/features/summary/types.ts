/** Mirrors of the backend's summary DTOs. */

/** One claim with the server's checks (backend: ClaimResponse). */
export interface ClaimResponse {
  section: 'complaint' | 'history' | 'examination' | 'assessment' | 'plan';
  text: string;
  quote: string;
  supported: boolean;
  quotePresent: boolean;
  negationConsistent: boolean;
  numbersMatch: boolean;
  checkAudio: boolean;
  startMs: number | null;
  sessionId: string | null;
}

/** A summary (backend: SummaryResponse). */
export interface SummaryResponse {
  requestId: string;
  available: boolean;
  message: string | null;
  claims: ClaimResponse[];
}

export const SECTION_LABELS: Record<ClaimResponse['section'], string> = {
  complaint: 'Presenting complaint',
  history: 'History',
  examination: 'Examination',
  assessment: 'Assessment',
  plan: 'Plan',
};

/** Why a claim failed, in plain words. */
export function failures(claim: ClaimResponse): string[] {
  const reasons: string[] = [];
  if (!claim.quotePresent) {
    reasons.push('the quote is not in the transcript');
  }
  if (!claim.negationConsistent) {
    reasons.push('it does not match what was said about "no" or "not"');
  }
  if (!claim.numbersMatch) {
    reasons.push('a number in it was not said');
  }
  return reasons;
}
