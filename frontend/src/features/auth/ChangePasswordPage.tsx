import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { z } from 'zod';
import { apiPost, errorMessage } from '../../api/client';
import { ROLE_HOME } from './roles';
import { SESSION_QUERY_KEY, useCurrentStaff } from './session';
import type { CurrentStaffResponse } from './types';

/** Same limits as the backend (Staff.MIN_PASSWORD_LENGTH and MAX_PASSWORD_LENGTH). */
const MIN_LENGTH = 12;
const MAX_LENGTH = 128;

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password').max(MAX_LENGTH),
    newPassword: z
      .string()
      .min(MIN_LENGTH, `Use at least ${String(MIN_LENGTH)} characters`)
      .max(MAX_LENGTH, `Use at most ${String(MAX_LENGTH)} characters`),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two new passwords do not match',
  });

type Form = z.infer<typeof schema>;

/** Choosing a new password; required after a one-time password from an admin. */
export function ChangePasswordPage() {
  const { data: staff } = useCurrentStaff();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const form = useForm<Form>({ resolver: zodResolver(schema) });
  const change = useMutation({
    mutationFn: ({ currentPassword, newPassword }: Form) =>
      apiPost<CurrentStaffResponse>('/api/auth/password', { currentPassword, newPassword }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(SESSION_QUERY_KEY, updated);
      await navigate(ROLE_HOME[updated.role], { replace: true });
    },
  });
  const { errors } = form.formState;

  return (
    <main className="narrow">
      <h1>Change your password</h1>
      {staff?.mustChangePassword ? (
        <p>You logged in with a one-time password. Choose your own before continuing.</p>
      ) : null}
      <form
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit((values) => {
            change.mutate(values);
          })(event);
        }}
      >
        <PasswordField
          id="currentPassword"
          label="Current password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          registration={form.register('currentPassword')}
        />
        <PasswordField
          id="newPassword"
          label={`New password (at least ${String(MIN_LENGTH)} characters)`}
          autoComplete="new-password"
          error={errors.newPassword?.message}
          registration={form.register('newPassword')}
        />
        <PasswordField
          id="confirmPassword"
          label="New password again"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          registration={form.register('confirmPassword')}
        />
        <div aria-live="polite">
          {change.error ? <p role="alert">{errorMessage(change.error)}</p> : null}
        </div>
        <button type="submit" disabled={change.isPending}>
          {change.isPending ? 'Saving...' : 'Change password'}
        </button>
      </form>
    </main>
  );
}

interface PasswordFieldProps {
  id: keyof Form;
  label: string;
  autoComplete: string;
  error: string | undefined;
  registration: ReturnType<ReturnType<typeof useForm<Form>>['register']>;
}

function PasswordField({ id, label, autoComplete, error, registration }: PasswordFieldProps) {
  return (
    <>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="password"
        autoComplete={autoComplete}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...registration}
      />
      {error ? (
        <p id={`${id}-error`} className="field-error">
          {error}
        </p>
      ) : null}
    </>
  );
}
