import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../../api/client';
import type { Sex } from '../patients/types';

/** A registry lookup (backend: RegistryLookupResponse). */
export interface RegistryLookupResponse {
  found: boolean;
  fullName: string | null;
  dateOfBirth: string | null;
  sex: Sex | null;
}

export type EligibilityStatus =
  'NONE' | 'PENDING' | 'ELIGIBLE' | 'INELIGIBLE' | 'NOT_FOUND' | 'UNAVAILABLE';

/** A patient's SHA eligibility (backend: EligibilityResponse). */
export interface EligibilityResponse {
  status: EligibilityStatus;
  checkedAt: string | null;
}

/** The ID number is always typed by reception, never taken from speech. */
export function lookupRegistry(nationalId: string) {
  const params = new URLSearchParams({ idType: 'NATIONAL_ID', idNumber: nationalId });
  return apiFetch<RegistryLookupResponse>(`/api/registry/lookup?${params.toString()}`);
}

/** While the background check runs (or is being retried), the badge asks again every 3 seconds. */
export const ELIGIBILITY_REFRESH_MS = 3_000;

export function useEligibility(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'eligibility'],
    queryFn: () => apiFetch<EligibilityResponse>(`/api/patients/${patientId}/eligibility`),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'UNAVAILABLE' ? ELIGIBILITY_REFRESH_MS : false;
    },
  });
}
