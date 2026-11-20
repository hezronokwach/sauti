/** Mirrors of the backend's patient DTOs. */

export type Sex = 'FEMALE' | 'MALE' | 'INTERSEX';
export type PreferredLanguage = 'SWAHILI' | 'ENGLISH';
export type PayerType = 'CASH' | 'SHA' | 'INSURANCE';

/** A search result or duplicate match (backend: PatientSummaryResponse). */
export interface PatientSummaryResponse {
  id: string;
  patientNumber: string;
  fullName: string;
  sex: Sex;
  dateOfBirth: string;
  ageYears: number;
}

/** A registered patient's details (backend: PatientDetailsResponse). */
export interface PatientDetailsResponse extends PatientSummaryResponse {
  phone: string | null;
  nationalId: string | null;
  shaNumber: string | null;
  residence: string | null;
  nextOfKinName: string | null;
  nextOfKinPhone: string | null;
  preferredLanguage: PreferredLanguage;
  payerType: PayerType;
}

/** A new patient (backend: RegisterPatientRequest). */
export interface RegisterPatientRequest {
  fullName: string;
  dateOfBirth: string;
  sex: Sex;
  phone?: string;
  nationalId?: string;
  shaNumber?: string;
  residence?: string;
  nextOfKinName?: string;
  nextOfKinPhone?: string;
  preferredLanguage?: PreferredLanguage;
  payerType?: PayerType;
  confirmNewPatient?: boolean;
}

/** The extra fields of the 409 DUPLICATE_PATIENT body (backend: DuplicatePatientResponse). */
export interface DuplicatePatientDetails {
  matches: PatientSummaryResponse[];
  confirmable: boolean;
}
