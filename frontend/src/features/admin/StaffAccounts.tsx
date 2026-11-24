import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { ROLE_LABEL } from '../auth/roles';
import { useCurrentStaff } from '../auth/session';
import { AddStaffForm } from './AddStaffForm';
import { useDisableStaff, useStaffList } from './staffApi';
import type { StaffSummary } from './types';

/** The one thing an admin most needs to know about an account right now. */
function accountState(staff: StaffSummary): string {
  if (staff.status === 'DISABLED') return 'Disabled';
  if (staff.lockedUntil && new Date(staff.lockedUntil) > new Date()) return 'Locked';
  if (staff.mustChangePassword) return 'Waiting for first login';
  if (!staff.authenticatorSetUp) return 'Authenticator not set up';
  return 'Active';
}

/** The admin's list of staff accounts, with adding and disabling. */
export function StaffAccounts() {
  const { data: me } = useCurrentStaff();
  const staff = useStaffList();
  const disable = useDisableStaff();
  const [confirming, setConfirming] = useState<string | null>(null);

  return (
    <section aria-labelledby="staff-heading">
      <h2 id="staff-heading">Staff accounts</h2>
      {staff.error ? <p role="alert">{errorMessage(staff.error)}</p> : null}
      {disable.error ? <p role="alert">{errorMessage(disable.error)}</p> : null}
      {staff.data ? (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Licence</th>
              <th>Account</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {staff.data.map((person) => (
              <tr key={person.id}>
                <td>{person.fullName}</td>
                <td>{person.email}</td>
                <td>{ROLE_LABEL[person.role]}</td>
                <td>{person.licenceNumber ?? '-'}</td>
                <td>{accountState(person)}</td>
                <td>
                  {person.status === 'ACTIVE' && person.id !== me?.id ? (
                    confirming === person.id ? (
                      <>
                        <button
                          type="button"
                          disabled={disable.isPending}
                          onClick={() => {
                            disable.mutate(person.id, {
                              onSettled: () => {
                                setConfirming(null);
                              },
                            });
                          }}
                        >
                          Yes, disable {person.fullName}
                        </button>{' '}
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => {
                            setConfirming(null);
                          }}
                        >
                          Cancel
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Disable ${person.fullName}`}
                        onClick={() => {
                          setConfirming(person.id);
                        }}
                      >
                        Disable
                      </button>
                    )
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      <AddStaffForm />
    </section>
  );
}
