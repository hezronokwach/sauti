import { useQuery } from '@tanstack/react-query';
import { toApiRequestError } from '../../api/client';

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';

export interface BackendHealth {
  status: HealthStatus;
}

/**
 * Spring Boot's health endpoint answers 200 when UP and 503 when DOWN, with
 * {"status": ...} in both cases; anything else is a real failure.
 */
export async function fetchBackendHealth(): Promise<BackendHealth> {
  const response = await fetch('/actuator/health', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (response.ok || response.status === 503) {
    const body = (await response.json()) as Partial<BackendHealth>;
    return { status: body.status ?? 'UNKNOWN' };
  }
  throw await toApiRequestError(response);
}

export function useBackendHealth() {
  return useQuery({
    queryKey: ['backend-health'],
    queryFn: fetchBackendHealth,
    refetchInterval: 30_000,
    retry: false,
  });
}
