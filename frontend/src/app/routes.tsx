import type { RouteObject } from 'react-router';
import { AdminPage } from '../features/admin/AdminPage';
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage';
import { LoginPage } from '../features/auth/LoginPage';
import { RequireLogin } from '../features/auth/RequireLogin';
import { ROLE_HOME } from '../features/auth/roles';
import { SignedInLayout } from '../features/auth/SignedInLayout';
import type { StaffRole } from '../features/auth/types';
import { PreRegisterPage } from '../features/prereg/PreRegisterPage';
import { WaitingAreaPage } from '../features/queue/WaitingAreaPage';
import { ReceptionPage } from '../features/reception/ReceptionPage';
import { BillingPage, DoctorPage, TriagePage } from '../features/visits/QueuePages';
import { VisitPage } from '../features/visits/VisitPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { RootRedirect } from '../pages/RootRedirect';

/** A page for the listed roles only. Every logged-in page is declared through this. */
function page(
  path: string,
  roles: readonly StaffRole[],
  element: RouteObject['element'],
): RouteObject {
  return { element: <RequireLogin roles={roles} />, children: [{ path, element }] };
}

/** Every page of the app. Shared by the browser router and by tests. */
export const routes: RouteObject[] = [
  { path: '/', element: <RootRedirect /> },
  { path: '/login', element: <LoginPage /> },
  // The waiting-area screen: no login, ticket codes only.
  { path: '/waiting-area', element: <WaitingAreaPage /> },
  // Reached by QR code: saves only, answers with a ticket code.
  { path: '/pre-register', element: <PreRegisterPage /> },
  {
    element: <RequireLogin forPasswordChange />,
    children: [
      {
        element: <SignedInLayout />,
        children: [{ path: '/change-password', element: <ChangePasswordPage /> }],
      },
    ],
  },
  {
    element: <RequireLogin />,
    children: [
      {
        element: <SignedInLayout />,
        children: [
          page(ROLE_HOME.RECEPTIONIST, ['RECEPTIONIST'], <ReceptionPage />),
          page(ROLE_HOME.NURSE, ['NURSE'], <TriagePage />),
          page(ROLE_HOME.DOCTOR, ['DOCTOR'], <DoctorPage />),
          page(ROLE_HOME.CASHIER, ['CASHIER'], <BillingPage />),
          page(ROLE_HOME.ADMIN, ['ADMIN'], <AdminPage />),
          page('/visits/:visitId', ['RECEPTIONIST', 'NURSE', 'DOCTOR'], <VisitPage />),
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];
