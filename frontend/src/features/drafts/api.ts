import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost, apiPut } from '../../api/client';
import type { PatientDetailsResponse } from '../patients/types';
import type { DraftField } from '../recorder/pieceStore';

/** A field value drafted from speech (backend: FieldDraftResponse). */
export interface FieldDraftResponse {
  id: string;
  field: DraftField;
  value: string;
  quote: string;
  sessionId: string;
  startMs: number;
  status: 'PENDING' | 'CONFIRMED' | 'DISMISSED';
}

/** The reason for visit and whether triage confirmed it (backend: ReasonForVisitResponse). */
export interface ReasonResponse {
  reasonForVisit: string | null;
  confirmed: boolean;
}

/** Drafts appear a few seconds after speech; the list is asked for this often. */
export const DRAFTS_REFRESH_MS = 2_000;

export function useFieldDrafts(visitId: string) {
  return useQuery({
    queryKey: ['visits', visitId, 'field-drafts'],
    queryFn: () => apiFetch<FieldDraftResponse[]>(`/api/visits/${visitId}/field-drafts`),
    refetchInterval: DRAFTS_REFRESH_MS,
  });
}

export function useRefreshDrafts(visitId: string) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['visits', visitId, 'field-drafts'] });
}

export function useReason(visitId: string) {
  return useQuery({
    queryKey: ['visits', visitId, 'reason'],
    queryFn: () => apiFetch<ReasonResponse>(`/api/visits/${visitId}/reason`),
  });
}

export function confirmDraft(visitId: string, draftId: string) {
  return apiPost<FieldDraftResponse>(`/api/visits/${visitId}/field-drafts/${draftId}/confirm`);
}

export function dismissDraft(visitId: string, draftId: string) {
  return apiPost<FieldDraftResponse>(`/api/visits/${visitId}/field-drafts/${draftId}/dismiss`);
}

export function saveReason(visitId: string, reasonForVisit: string) {
  return apiPut<ReasonResponse>(`/api/visits/${visitId}/reason`, { reasonForVisit });
}

export function confirmReason(visitId: string) {
  return apiPost<ReasonResponse>(`/api/visits/${visitId}/reason/confirm`);
}

export function saveContact(patientId: string, residence: string, nextOfKinName: string) {
  return apiPut<PatientDetailsResponse>(`/api/patients/${patientId}/contact`, {
    residence,
    nextOfKinName,
  });
}
