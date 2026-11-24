import { Outlet } from 'react-router';

/** Wraps every page, public or logged in, with the warning that this is a demonstration. */
export function DemoBanner() {
  return (
    <>
      <p className="demo-banner" role="note">
        Demonstration system. Do not enter real patient information.
      </p>
      <Outlet />
    </>
  );
}
