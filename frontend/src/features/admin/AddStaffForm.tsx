import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '../../api/client';
import { ROLE_LABEL } from '../auth/roles';
import type { StaffRole } from '../auth/types';
import { useCreateStaff } from './staffApi';
import type { CreatedStaff } from './types';

const ROLES = Object.keys(ROLE_LABEL) as StaffRole[];
const needsLicence = (role: string | undefined) => role === 'DOCTOR' || role === 'NURSE';

const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Enter the name').max(200),
    email: z.string().trim().toLowerCase().max(254).pipe(z.email('Enter a valid email')),
    phone: z.string().trim().max(20).optional(),
    role: z.enum(['RECEPTIONIST', 'NURSE', 'DOCTOR', 'CASHIER', 'ADMIN'], {
      message: 'Choose a role',
    }),
    licenceNumber: z.string().trim().max(50).optional(),
  })
  .refine((values) => !needsLicence(values.role) || Boolean(values.licenceNumber), {
    path: ['licenceNumber'],
    message: 'Doctors and nurses need a licence number',
  });

type Form = z.infer<typeof schema>;

/**
 * Adding a staff member. The server chooses a one-time password and shows it only once, so
 * it stays on screen until the admin confirms they have passed it on, and is never stored.
 */
export function AddStaffForm() {
  const form = useForm<Form>({ resolver: zodResolver(schema) });
  const role = useWatch({ control: form.control, name: 'role' });
  const create = useCreateStaff();
  const [created, setCreated] = useState<CreatedStaff | null>(null);
  const { errors } = form.formState;

  if (created) {
    return (
      <section aria-labelledby="created-heading">
        <h3 id="created-heading">Account created for {created.staff.fullName}</h3>
        <p>Give them this one-time password. It will not be shown again.</p>
        <p className="secret">{created.oneTimePassword}</p>
        <p className="quiet">
          At first login they choose a new password and set up an authenticator app.
        </p>
        <button
          type="button"
          onClick={() => {
            setCreated(null);
            create.reset(); // the mutation also holds the password until reset
          }}
        >
          I have passed it on
        </button>
      </section>
    );
  }

  const submit = form.handleSubmit((values) => {
    create.mutate(
      {
        fullName: values.fullName,
        email: values.email,
        phone: values.phone ? values.phone : undefined,
        role: values.role,
        licenceNumber:
          needsLicence(values.role) && values.licenceNumber ? values.licenceNumber : undefined,
      },
      {
        onSuccess: (result) => {
          form.reset();
          setCreated(result);
        },
      },
    );
  });

  const field = (name: keyof Form) => ({
    id: `staff-${name}`,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? `staff-${name}-error` : undefined,
  });
  const fieldError = (name: keyof Form) =>
    errors[name] ? (
      <p id={`staff-${name}-error`} className="field-error">
        {errors[name].message}
      </p>
    ) : null;

  return (
    <section aria-labelledby="add-staff-heading">
      <h3 id="add-staff-heading">Add a staff member</h3>
      <form
        noValidate
        onSubmit={(event) => {
          void submit(event);
        }}
      >
        <label htmlFor="staff-fullName">Full name</label>
        <input {...field('fullName')} {...form.register('fullName')} />
        {fieldError('fullName')}

        <label htmlFor="staff-email">Email</label>
        <input type="email" {...field('email')} {...form.register('email')} />
        {fieldError('email')}

        <label htmlFor="staff-phone">Phone (optional)</label>
        <input type="tel" {...field('phone')} {...form.register('phone')} />
        {fieldError('phone')}

        <label htmlFor="staff-role">Role</label>
        <select {...field('role')} {...form.register('role')} defaultValue="">
          <option value="" disabled>
            Choose
          </option>
          {ROLES.map((option) => (
            <option key={option} value={option}>
              {ROLE_LABEL[option]}
            </option>
          ))}
        </select>
        {fieldError('role')}

        {needsLicence(role) ? (
          <>
            <label htmlFor="staff-licenceNumber">Licence number</label>
            <input {...field('licenceNumber')} {...form.register('licenceNumber')} />
            {fieldError('licenceNumber')}
          </>
        ) : null}

        <div aria-live="polite">
          {create.error ? <p role="alert">{errorMessage(create.error)}</p> : null}
        </div>
        <button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Creating...' : 'Create account'}
        </button>
      </form>
    </section>
  );
}
