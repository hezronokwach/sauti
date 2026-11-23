import { useQuery } from '@tanstack/react-query';
import { ApiRequestError, apiFetch, apiPost, apiPut } from '../../api/client';
import type { Medicine, Prescription, PrescriptionItemRequest } from './types';

export const prescriptionKey = (visitId: string) => ['visits', visitId, 'prescription'] as const;

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

export function removeItem(visitId: string, itemId: string, revision: number) {
  return apiFetch<Prescription>(
    `/api/visits/${visitId}/prescription/items/${itemId}?revision=${String(revision)}`,
    { method: 'DELETE' },
  );
}
