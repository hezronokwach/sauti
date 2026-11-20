/** Mirrors of the backend's ticket DTOs. */

export type TicketLane = 'PRE_REGISTERED' | 'RETURNING' | 'NEW' | 'EMERGENCY';
export type TicketStatus = 'WAITING' | 'CALLED' | 'SERVED' | 'CANCELLED';

/** One arrival ticket (backend: TicketResponse). */
export interface TicketResponse {
  id: string;
  code: string;
  lane: TicketLane;
  status: TicketStatus;
  arrivedAt: string;
  calledAt: string | null;
  visitId: string | null;
}

/** The waiting-area display (backend: QueueBoardResponse). */
export interface QueueBoardResponse {
  called: string[];
  waiting: string[];
}

export const LANE_LABELS: Record<TicketLane, string> = {
  PRE_REGISTERED: 'Pre-registered',
  RETURNING: 'Returning',
  NEW: 'New patient',
  EMERGENCY: 'Emergency',
};
