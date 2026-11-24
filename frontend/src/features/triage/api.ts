import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiPost } from '../../api/client';
import type { AllergyResponse, RecordVitalsRequest, VitalsResponse } from './types';

export function usePatientAllergies(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'allergies'],
    queryFn: () => apiFetch<AllergyResponse[]>(`/api/patients/${patientId}/allergies`),
  });
}

export function useVisitVitals(visitId: string) {
  return useQuery({
    queryKey: ['visits', visitId, 'vitals'],
    queryFn: () => apiFetch<VitalsResponse[]>(`/api/visits/${visitId}/vitals`),
  });
}

/** Recording vitals also sends the patient to the doctor, so the visit and queues refresh. */
export function useRecordVitals(visitId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: RecordVitalsRequest) =>
      apiPost<VitalsResponse>(`/api/visits/${visitId}/vitals`, request),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['visits'] }),
  });
}

/** Either an allergy, or that the patient was asked and has none (backend: RecordAllergyRequest). */
export type RecordAllergyRequest =
  { noKnownAllergies: true } | { substance: string; reaction?: string };

export function useRecordAllergy(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: RecordAllergyRequest) =>
      apiPost<AllergyResponse>(`/api/patients/${patientId}/allergies`, request),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'allergies'] }),
  });
}

/** What the patient's record says about allergies, in one word, for the banner and warnings. */
export type AllergyState = 'loading' | 'allergic' | 'none' | 'not-asked';

export function allergyState(entries: AllergyResponse[] | undefined): AllergyState {
  if (!entries) {
    return 'loading';
  }
  const active = entries.filter((entry) => entry.status === 'ACTIVE');
  if (active.some((entry) => entry.kind === 'ALLERGY')) {
    return 'allergic';
  }
  return active.length > 0 ? 'none' : 'not-asked';
}
