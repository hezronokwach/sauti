import type { StaffRole } from './types';

/** Where each role lands after login. */
export const ROLE_HOME: Record<StaffRole, string> = {
  RECEPTIONIST: '/reception',
  NURSE: '/triage',
  DOCTOR: '/doctor',
  CASHIER: '/billing',
  ADMIN: '/admin',
};

/** How each role is named on screen. */
export const ROLE_LABEL: Record<StaffRole, string> = {
  RECEPTIONIST: 'Receptionist',
  NURSE: 'Nurse',
  DOCTOR: 'Doctor',
  CASHIER: 'Cashier',
  ADMIN: 'Administrator',
};

/**
 * The page to return to after login, if it is a path inside this app. Anything else
 * (another site, a protocol-relative "//host" link) is ignored, so a crafted login link
 * cannot send someone elsewhere after they log in.
 */
export function safeNextPath(next: string | null): string | null {
  if (!next?.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) {
    return null;
  }
  return next;
}
