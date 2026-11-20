/** The five staff roles; each person has exactly one. Mirrors the backend's StaffRole. */
export type StaffRole = 'RECEPTIONIST' | 'NURSE' | 'DOCTOR' | 'CASHIER' | 'ADMIN';

/** The logged-in staff member (backend: CurrentStaffResponse, from GET /api/auth/me). */
export interface CurrentStaffResponse {
  id: string;
  fullName: string;
  email: string;
  role: StaffRole;
  mustChangePassword: boolean;
}

/** The reply to the password step (backend: LoginNextStepResponse). */
export interface LoginNextStepResponse {
  codeRequired: boolean;
  setupRequired: boolean;
  /** The setup QR content; only present when setup is required. */
  otpauthUri: string | null;
}
