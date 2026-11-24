import { Navigate, Outlet, useLocation } from 'react-router';
import { errorMessage } from '../../api/client';
import { NoAccessPage } from '../../pages/NoAccessPage';
import { ROLE_HOME } from './roles';
import { useCurrentStaff } from './session';
import type { StaffRole } from './types';

interface RequireLoginProps {
  /** Roles allowed on these pages; leave out for "any logged-in person". */
  roles?: readonly StaffRole[];
  /** True only for the password change page, which a one-time password may reach. */
  forPasswordChange?: boolean;
}

/**
 * Route guard. Not logged in: off to the login page, coming back here afterwards. Still
 * on a one-time password: the password change page. Wrong role: a no-access page. The
 * backend checks every request again; this only keeps people on pages they can use.
 */
export function RequireLogin({ roles, forPasswordChange = false }: RequireLoginProps) {
  const { data: staff, error, isPending } = useCurrentStaff();
  const location = useLocation();

  if (isPending) {
    return <p role="status">Loading...</p>;
  }
  if (error) {
    return <p role="alert">Cannot check your login: {errorMessage(error)}</p>;
  }
  if (!staff) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (staff.mustChangePassword && !forPasswordChange) {
    return <Navigate to="/change-password" replace />;
  }
  if (roles && !roles.includes(staff.role)) {
    return <NoAccessPage home={ROLE_HOME[staff.role]} />;
  }
  return <Outlet />;
}
