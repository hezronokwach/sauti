import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { PreferredLanguage } from '../patients/types';

export type ConsentPurpose = 'RECORD_RECEPTION' | 'RECORD_CONSULTATION';
export type ConsentDecision = 'AGREED' | 'REFUSED' | 'WITHDRAWN';
export type ConsentGivenBy = 'PATIENT' | 'PARENT_OR_GUARDIAN' | 'REPRESENTATIVE';

/** The wording to read out (backend: ConsentWordingResponse). */
export interface ConsentWordingResponse {
  version: string;
  language: PreferredLanguage;
  text: string;
  demonstration: boolean;
}

/** One recorded decision (backend: ConsentResponse). */
export interface ConsentResponse {
  id: string;
  visitId: string;
  purpose: ConsentPurpose;
  decision: ConsentDecision;
  givenBy: ConsentGivenBy;
  wordingVersion: string;
  recordedBy: string;
  recordedAt: string;
}

export function useConsentWording(language: PreferredLanguage) {
  return useQuery({
    queryKey: ['consent', 'wording', language],
    queryFn: () => apiFetch<ConsentWordingResponse>(`/api/consent/wording?language=${language}`),
    staleTime: Infinity,
  });
}

export function useConsentHistory(visitId: string) {
  return useQuery({
    queryKey: ['consent', 'visit', visitId],
    queryFn: () => apiFetch<ConsentResponse[]>(`/api/visits/${visitId}/consents`),
  });
}

/** Whether recording for this purpose is allowed: the latest decision is AGREED. */
export function isAgreed(history: ConsentResponse[] | undefined, purpose: ConsentPurpose): boolean {
  const latest = history?.filter((consent) => consent.purpose === purpose).at(-1);
  return latest?.decision === 'AGREED';
}

export function useRecordConsent(visitId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: {
      purpose: ConsentPurpose;
      decision: ConsentDecision;
      givenBy: ConsentGivenBy;
    }) => apiPost<ConsentResponse>(`/api/visits/${visitId}/consents`, request),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['consent', 'visit', visitId] }),
  });
}
