import { Navigate } from 'react-router';
import { errorMessage } from '../api/client';
import { ROLE_HOME } from '../features/auth/roles';
import { useCurrentStaff } from '../features/auth/session';

/** "/" goes to the logged-in person's home, or to the login page. */
export function RootRedirect() {
  const { data: staff, error, isPending } = useCurrentStaff();

  if (isPending) {
    return <p role="status">Loading...</p>;
  }
  if (error) {
    return <p role="alert">Cannot check your login: {errorMessage(error)}</p>;
  }
  return <Navigate to={staff ? ROLE_HOME[staff.role] : '/login'} replace />;
}
