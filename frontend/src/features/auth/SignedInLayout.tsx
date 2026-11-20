import { Outlet } from 'react-router';
import { ROLE_LABEL } from './roles';
import { useCurrentStaff } from './session';
import { IDLE_TIMEOUT_MS, useIdleTimeout } from './useIdleTimeout';
import { useLogout } from './useLogout';

/**
 * The frame around every logged-in page: who is logged in, a logout button, and the
 * idle timeout, which logs out after 10 minutes without input.
 */
export function SignedInLayout() {
  const { data: staff } = useCurrentStaff();
  const logout = useLogout();
  useIdleTimeout(IDLE_TIMEOUT_MS, () => {
    void logout('idle');
  });

  return (
    <>
      <header className="app-header">
        <span className="app-name">Sauti HMS</span>
        {staff ? (
          <span>
            {staff.fullName} <span className="role">{ROLE_LABEL[staff.role]}</span>
          </span>
        ) : null}
        <button
          type="button"
          onClick={() => {
            void logout();
          }}
        >
          Log out
        </button>
      </header>
      <Outlet />
    </>
  );
}
