/** Mirrors of the backend's triage and allergy DTOs. */

/** New vital signs (backend: RecordVitalsRequest). */
export interface RecordVitalsRequest {
  temperatureC: number;
  pulseBpm: number;
  respiratoryRate: number;
  systolicMmhg: number;
  diastolicMmhg: number;
  spo2Percent: number;
  weightKg?: number;
  heightCm?: number;
}

/** One set of vital signs (backend: VitalsResponse). */
export interface VitalsResponse extends Required<
  Omit<RecordVitalsRequest, 'weightKg' | 'heightCm'>
> {
  id: string;
  visitId: string;
  recordedBy: string;
  recordedAt: string;
  weightKg: number | null;
  heightCm: number | null;
}

/** One allergy entry (backend: AllergyResponse). */
export interface AllergyResponse {
  id: string;
  kind: 'ALLERGY' | 'NO_KNOWN_ALLERGIES';
  substance: string | null;
  substanceGroup: string | null;
  reaction: string | null;
  status: 'ACTIVE' | 'ENTERED_IN_ERROR';
  recordedAt: string;
  enteredInErrorReason: string | null;
}
