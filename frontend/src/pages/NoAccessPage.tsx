import { Link } from 'react-router';

interface NoAccessPageProps {
  /** The logged-in person's own home page. */
  home: string;
}

export function NoAccessPage({ home }: NoAccessPageProps) {
  return (
    <main>
      <h1>You don&apos;t have access to this</h1>
      <p>This page is for a different role.</p>
      <p>
        <Link to={home}>Go to your home page</Link>
      </p>
    </main>
  );
}
