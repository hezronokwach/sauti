export const DOSE_UNITS = [
  'MG',
  'MCG',
  'G',
  'ML',
  'TABLET',
  'CAPSULE',
  'PUFF',
  'DROP',
  'SACHET',
  'UNIT',
  'APPLICATION',
] as const;
export type DoseUnit = (typeof DOSE_UNITS)[number];

/** How often a medicine is taken, with the words shown on screen. */
export const FREQUENCIES = {
  OD: 'OD, once a day',
  BD: 'BD, twice a day',
  TDS: 'TDS, three times a day',
  QID: 'QID, four times a day',
  Q8H: 'Q8H, every 8 hours',
  STAT: 'STAT, once now',
  NOCTE: 'NOCTE, at night',
  AS_NEEDED: 'As needed',
} as const;
export type Frequency = keyof typeof FREQUENCIES;

/** A medicine from the list (backend: MedicineResponse). */
export interface Medicine {
  id: string;
  name: string;
  form: string;
  strength: string;
  routes: string[];
  priceKes: number;
}

/** One line of a prescription (backend: PrescriptionItemResponse). */
export interface PrescriptionItem {
  id: string;
  medicine: Medicine;
  doseValue: number | null;
  doseUnit: DoseUnit | null;
  frequency: Frequency | null;
  durationDays: number | null;
  route: string | null;
  quantity: number | null;
  instructions: string | null;
}

/** A visit's prescription (backend: PrescriptionResponse). */
export interface Prescription {
  id: string;
  visitId: string;
  status: 'DRAFT' | 'SIGNED' | 'CANCELLED';
  rulesVersion: string;
  revision: number;
  signedAt: string | null;
  items: PrescriptionItem[];
}

/** Adding or changing a line (backend: PrescriptionItemRequest). */
export interface PrescriptionItemRequest {
  revision?: number;
  medicineId: string;
  doseValue?: number;
  doseUnit?: DoseUnit;
  frequency?: Frequency;
  durationDays?: number;
  route?: string;
  quantity?: number;
  instructions?: string;
}
