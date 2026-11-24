import { Link, NavLink, Outlet } from 'react-router';
import { BrandMark, Icon } from '../ui/Icon';
import type { IconName } from '../ui/Icon';
import { initials } from '../ui/initials';
import { ROLE_HOME, ROLE_LABEL } from './roles';
import { useCurrentStaff } from './session';
import type { StaffRole } from './types';
import { IDLE_TIMEOUT_MS, useIdleTimeout } from './useIdleTimeout';
import { useLogout } from './useLogout';

interface NavItem {
  label: string;
  to: string;
  icon: IconName;
}

/** The links each role sees. Each one leads to a screen that role can open. */
const NAV: Record<StaffRole, NavItem[]> = {
  RECEPTIONIST: [
    { label: 'Front desk', to: ROLE_HOME.RECEPTIONIST, icon: 'desk' },
    { label: 'Waiting-area screen', to: '/waiting-area', icon: 'monitor' },
  ],
  NURSE: [{ label: 'Triage queue', to: ROLE_HOME.NURSE, icon: 'activity' }],
  DOCTOR: [{ label: 'Patient queue', to: ROLE_HOME.DOCTOR, icon: 'stethoscope' }],
  CASHIER: [{ label: 'Payments', to: ROLE_HOME.CASHIER, icon: 'card' }],
  ADMIN: [{ label: 'Overview', to: ROLE_HOME.ADMIN, icon: 'grid' }],
};

function greeting(hour: number): string {
  if (hour < 12) return 'Good morning';
  return hour < 17 ? 'Good afternoon' : 'Good evening';
}

/**
 * The frame around every logged-in page: navigation for the role, who is logged in, a logout
 * button, and the idle timeout, which logs out after 10 minutes without input.
 */
export function SignedInLayout() {
  const { data: staff } = useCurrentStaff();
  const logout = useLogout();
  useIdleTimeout(IDLE_TIMEOUT_MS, () => {
    void logout('idle');
  });
  const now = new Date();
  // Someone who must change their password cannot use the other screens yet.
  const links = staff && !staff.mustChangePassword ? NAV[staff.role] : [];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link to="/" className="brand">
          <BrandMark />
          <span className="app-name">Sauti HMS</span>
        </Link>
        <nav className="side-nav" aria-label="Main">
          {links.map((item) => (
            <NavLink key={item.to} to={item.to}>
              <Icon name={item.icon} />
              {item.label}
            </NavLink>
          ))}
        </nav>
        {staff ? (
          <div className="side-user">
            <span className="avatar" aria-hidden="true">
              {initials(staff.fullName)}
            </span>
            <div>
              <strong>{staff.fullName}</strong>
              <span className="role">{ROLE_LABEL[staff.role]}</span>
            </div>
          </div>
        ) : null}
        <button
          type="button"
          className="log-out"
          onClick={() => {
            void logout();
          }}
        >
          <Icon name="logout" />
          Log out
        </button>
      </aside>
      <div className="app-content">
        <header className="topbar">
          <div>
            <strong>
              {greeting(now.getHours())}
              {staff ? `, ${staff.fullName.split(' ')[0] ?? ''}` : ''}
            </strong>
            <span>
              {now.toLocaleDateString('en-KE', {
                weekday: 'long',
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })}
            </span>
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  );
}
