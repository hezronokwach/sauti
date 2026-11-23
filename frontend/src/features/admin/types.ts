import type { StaffRole } from '../auth/types';

/** One staff account (backend: StaffSummaryResponse). Never includes a password. */
export interface StaffSummary {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  role: StaffRole;
  licenceNumber: string | null;
  status: 'ACTIVE' | 'DISABLED';
  authenticatorSetUp: boolean;
  mustChangePassword: boolean;
  lockedUntil: string | null;
}

/** A new account (backend: CreateStaffRequest). The server chooses the password. */
export interface CreateStaffRequest {
  fullName: string;
  email: string;
  phone?: string;
  role: StaffRole;
  licenceNumber?: string;
}

/** The new account and its password, which the server shows only this once. */
export interface CreatedStaff {
  staff: StaffSummary;
  oneTimePassword: string;
}
