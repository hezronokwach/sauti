import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '../../api/client';

/** A saved consultation recording (backend: CaptureSessionResponse). */
export interface SavedRecordingResponse {
  id: string;
  status: 'RECORDING' | 'STOPPED' | 'COMPLETE' | 'ERASED';
  startedAt: string;
  stoppedAt: string | null;
}

/** While a recording is still being transcribed, its status is checked this often. */
export const RECORDINGS_REFRESH_MS = 5_000;

const recordingsKey = (visitId: string) => ['visits', visitId, 'recordings'] as const;

/** This visit's consultation recordings that were not erased, oldest first. */
export function useConsultationRecordings(visitId: string) {
  return useQuery({
    queryKey: recordingsKey(visitId),
    queryFn: () => apiFetch<SavedRecordingResponse[]>(`/api/visits/${visitId}/captures`),
    refetchInterval: (query) =>
      query.state.data?.some((recording) => recording.status !== 'COMPLETE')
        ? RECORDINGS_REFRESH_MS
        : false,
  });
}

export function useRefreshRecordings(visitId: string) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: recordingsKey(visitId) });
}
