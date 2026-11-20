import type { CurrentStaffResponse, StaffRole } from '../features/auth/types';
import { apiError, jsonResponse } from './render';
import type { MockRoute } from './render';

/** A logged-in staff member as GET /api/auth/me returns them. */
export function staffMember(
  role: StaffRole,
  overrides: Partial<CurrentStaffResponse> = {},
): CurrentStaffResponse {
  return {
    id: '018f3c2e-0000-7000-8000-000000000001',
    fullName: 'Amina Otieno',
    email: 'amina@example.test',
    role,
    mustChangePassword: false,
    ...overrides,
  };
}

/** GET /api/auth/me answering with this person, or 401 when nobody is logged in. */
export function meReturns(staff: () => CurrentStaffResponse | null): MockRoute {
  return () => {
    const current = staff();
    return current
      ? jsonResponse(200, current)
      : jsonResponse(401, apiError('UNAUTHENTICATED', 'You need to log in'));
  };
}

export const healthUp: MockRoute = () => jsonResponse(200, { status: 'UP' });
