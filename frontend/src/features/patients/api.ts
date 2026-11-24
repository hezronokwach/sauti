import { useMutation, useQuery } from '@tanstack/react-query';
import { ApiRequestError, apiFetch, apiPost } from '../../api/client';
import type {
  DuplicatePatientDetails,
  PatientDetailsResponse,
  PatientSummaryResponse,
  RegisterPatientRequest,
} from './types';

/** Searches start at two characters, as the backend requires for names. */
export const MIN_QUERY = 2;

export function usePatientSearch(query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: ['patients', 'search', q],
    queryFn: () => apiFetch<PatientSummaryResponse[]>(`/api/patients?q=${encodeURIComponent(q)}`),
    enabled: q.length >= MIN_QUERY,
    staleTime: 10_000,
  });
}

export function useRegisterPatient() {
  return useMutation({
    mutationFn: (request: RegisterPatientRequest) =>
      apiPost<PatientDetailsResponse>('/api/patients', request),
  });
}

/** The matches from a 409 DUPLICATE_PATIENT, or null for any other error. */
export function duplicateDetails(error: unknown): DuplicatePatientDetails | null {
  if (!(error instanceof ApiRequestError) || error.code !== 'DUPLICATE_PATIENT') {
    return null;
  }
  const body = error.body as Partial<DuplicatePatientDetails> | null;
  return {
    matches: Array.isArray(body?.matches) ? body.matches : [],
    confirmable: body?.confirmable === true,
  };
}
