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
