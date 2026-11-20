/** Mirrors of the backend's visit DTOs. */

export type VisitStatus =
  | 'AT_RECEPTION'
  | 'WAITING_TRIAGE'
  | 'IN_TRIAGE'
  | 'WAITING_DOCTOR'
  | 'WITH_DOCTOR'
  | 'WAITING_PAYMENT'
  | 'DONE'
  | 'CANCELLED';

/** One patient in a queue (backend: QueueEntryResponse). */
export interface QueueEntryResponse {
  visitId: string;
  queueNumber: number;
  status: VisitStatus;
  patientId: string;
  patientName: string;
  patientNumber: string;
  waitingSince: string;
}

/** A visit's place in the workflow (backend: VisitResponse). */
export interface VisitResponse {
  id: string;
  patientId: string;
  visitDate: string;
  queueNumber: number;
  status: VisitStatus;
  arrivedAt: string;
  statusChangedAt: string;
  assignedDoctorId: string | null;
}
