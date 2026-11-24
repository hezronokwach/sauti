import { useQuery } from '@tanstack/react-query';
import { ApiRequestError, apiFetch, apiPost, apiPut } from '../../api/client';
import type { NoteResponse, SaveNoteRequest } from './types';

export const noteKey = (visitId: string) => ['visits', visitId, 'note'] as const;

/** The visit's note, or null when none has been saved yet. */
export async function fetchNote(visitId: string): Promise<NoteResponse | null> {
  try {
    return await apiFetch<NoteResponse>(`/api/visits/${visitId}/note`);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export function useNote(visitId: string) {
  return useQuery({
    queryKey: noteKey(visitId),
    queryFn: () => fetchNote(visitId),
    // The editor holds its own copy while typing; refetching would fight with it.
    refetchOnWindowFocus: false,
    staleTime: Infinity,
  });
}

export function saveNote(visitId: string, request: SaveNoteRequest) {
  return apiPut<NoteResponse>(`/api/visits/${visitId}/note`, request);
}

export function signNote(visitId: string, version: number, code: string) {
  return apiPost<NoteResponse>(`/api/visits/${visitId}/note/sign`, { version, code });
}

export function addAddendum(visitId: string, text: string) {
  return apiPost<NoteResponse>(`/api/visits/${visitId}/note/addenda`, { text });
}

export function usePatientHistory(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'history'],
    queryFn: () => apiFetch<NoteResponse[]>(`/api/patients/${patientId}/history`),
  });
}
