import { useQuery } from '@tanstack/react-query';
import { ApiRequestError, apiFetch, apiPost, apiPut, errorMessage } from '../../api/client';
import type {
  InstructionLanguage,
  Medicine,
  Prescription,
  PrescriptionItemRequest,
  SpokenInstructions,
} from './types';

export const prescriptionKey = (visitId: string) => ['visits', visitId, 'prescription'] as const;

/** The backend's own reason for a refusal, so a wrong code is not shown as "no access". */
export function refusalMessage(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : errorMessage(error);
}

/** The visit's prescription, or null when none has been started. */
async function fetchPrescription(visitId: string): Promise<Prescription | null> {
  try {
    return await apiFetch<Prescription>(`/api/visits/${visitId}/prescription`);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export function usePrescription(visitId: string) {
  return useQuery({
    queryKey: prescriptionKey(visitId),
    queryFn: () => fetchPrescription(visitId),
  });
}

/** Medicines matching the text; waits for at least two characters. */
export function useMedicineSearch(text: string) {
  const query = text.trim();
  return useQuery({
    queryKey: ['medicines', query.toLowerCase()],
    queryFn: () =>
      apiFetch<Medicine[]>(`/api/medicines?${new URLSearchParams({ q: query }).toString()}`),
    enabled: query.length >= 2,
    staleTime: 5 * 60_000,
  });
}

/** Adds a line, or changes the given one. */
export function saveItem(visitId: string, itemId: string | null, body: PrescriptionItemRequest) {
  const items = `/api/visits/${visitId}/prescription/items`;
  return itemId
    ? apiPut<Prescription>(`${items}/${itemId}`, body)
    : apiPost<Prescription>(items, body);
}

export function overrideAlert(visitId: string, alertId: string, reason: string, code: string) {
  return apiPost<Prescription>(`/api/visits/${visitId}/prescription/alerts/${alertId}/override`, {
    reason,
    code,
  });
}

export function acknowledgeAlert(visitId: string, alertId: string) {
  return apiPost<Prescription>(`/api/visits/${visitId}/prescription/alerts/${alertId}/acknowledge`);
}

export function signPrescription(visitId: string, revision: number, code: string) {
  return apiPost<Prescription>(`/api/visits/${visitId}/prescription/sign`, { revision, code });
}

export function cancelPrescription(visitId: string, revision: number, code: string) {
  return apiPost<undefined>(`/api/visits/${visitId}/prescription/cancel`, { revision, code });
}

/** The words for the patient, in the chosen language or else the patient's own. */
export function useSpokenInstructions(
  visitId: string,
  prescriptionId: string,
  language: InstructionLanguage | null,
) {
  const query = language ? `?language=${language}` : '';
  return useQuery({
    queryKey: [...prescriptionKey(visitId), prescriptionId, 'instructions', language],
    queryFn: () =>
      apiFetch<SpokenInstructions>(`/api/visits/${visitId}/prescription/instructions${query}`),
  });
}

export function removeItem(visitId: string, itemId: string, revision: number) {
  return apiFetch<Prescription>(
    `/api/visits/${visitId}/prescription/items/${itemId}?revision=${String(revision)}`,
    { method: 'DELETE' },
  );
}
