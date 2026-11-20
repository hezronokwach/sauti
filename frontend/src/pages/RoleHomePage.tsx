import { useCurrentStaff } from '../features/auth/session';

interface RoleHomePageProps {
  title: string;
}

/** A role's landing page. Each role's real screens replace this in later issues. */
export function RoleHomePage({ title }: RoleHomePageProps) {
  const { data: staff } = useCurrentStaff();

  return (
    <main>
      <h1>{title}</h1>
      {staff ? <p>Welcome, {staff.fullName}.</p> : null}
      <p>The screens for this area are still being built.</p>
    </main>
  );
}
