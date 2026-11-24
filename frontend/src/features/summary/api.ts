import { apiPost } from '../../api/client';
import type { NoteResponse } from '../notes/types';
import type { SummaryResponse } from './types';

export function requestSummary(visitId: string) {
  return apiPost<SummaryResponse>(`/api/visits/${visitId}/summary`);
}

/** The server looks the claims up in its own record and refuses any that failed the checks. */
export function acceptClaims(
  visitId: string,
  requestId: string,
  claims: number[],
  noteVersion: number | null,
) {
  return apiPost<NoteResponse>(`/api/visits/${visitId}/summary/${requestId}/accept`, {
    claims,
    noteVersion,
  });
}
