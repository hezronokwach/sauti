import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useSearchParams } from 'react-router';
import { z } from 'zod';
import { ApiRequestError, apiPost, errorMessage } from '../../api/client';
import { BackendStatus } from '../system/BackendStatus';
import { AuthenticatorSetup } from './AuthenticatorSetup';
import { ROLE_HOME, safeNextPath } from './roles';
import { SESSION_QUERY_KEY, useCurrentStaff } from './session';
import type { CurrentStaffResponse, LoginNextStepResponse } from './types';

const passwordSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email').max(254),
  password: z.string().min(1, 'Enter your password').max(128),
});

const codeSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, 'Enter the 6-digit code from your authenticator app'),
});

type PasswordForm = z.infer<typeof passwordSchema>;
type CodeForm = z.infer<typeof codeSchema>;

type Step = { kind: 'password' } | { kind: 'code'; otpauthUri: string | null };

const IDLE_NOTICE = 'You were logged out after 10 minutes without activity.';

/**
 * Login in two steps: email and password, then the authenticator code. The first login
 * also shows the authenticator setup. Once the code is right, the session cache holds
 * the person and this page sends them on.
 */
export function LoginPage() {
  const [params] = useSearchParams();
  const [step, setStep] = useState<Step>({ kind: 'password' });
  const [notice, setNotice] = useState<string | null>(
    params.get('reason') === 'idle' ? IDLE_NOTICE : null,
  );
  const { data: staff, isPending } = useCurrentStaff();

  if (!isPending && staff) {
    const next = safeNextPath(params.get('next'));
    const target = staff.mustChangePassword ? '/change-password' : (next ?? ROLE_HOME[staff.role]);
    return <Navigate to={target} replace />;
  }

  return (
    <main className="narrow">
      <h1>Log in to Sauti HMS</h1>
      {notice ? (
        <p role="status" className="notice">
          {notice}
        </p>
      ) : null}
      {step.kind === 'password' ? (
        <PasswordStep
          onNext={(next) => {
            setNotice(null);
            setStep({ kind: 'code', otpauthUri: next.setupRequired ? next.otpauthUri : null });
          }}
        />
      ) : (
        <CodeStep
          otpauthUri={step.otpauthUri}
          onExpired={(message) => {
            setNotice(message);
            setStep({ kind: 'password' });
          }}
        />
      )}
      <footer className="quiet">
        <BackendStatus />
      </footer>
    </main>
  );
}

function PasswordStep({ onNext }: { onNext: (next: LoginNextStepResponse) => void }) {
  const form = useForm<PasswordForm>({ resolver: zodResolver(passwordSchema) });
  const login = useMutation({
    mutationFn: (values: PasswordForm) => apiPost<LoginNextStepResponse>('/api/auth/login', values),
    onSuccess: onNext,
  });
  const { errors } = form.formState;

  return (
    <form
      noValidate
      onSubmit={(event) => {
        void form.handleSubmit((values) => {
          login.mutate(values);
        })(event);
      }}
    >
      <label htmlFor="email">Email</label>
      <input
        id="email"
        type="email"
        autoComplete="username"
        aria-invalid={errors.email ? true : undefined}
        aria-describedby={errors.email ? 'email-error' : undefined}
        {...form.register('email')}
      />
      {errors.email ? (
        <p id="email-error" className="field-error">
          {errors.email.message}
        </p>
      ) : null}

      <label htmlFor="password">Password</label>
      <input
        id="password"
        type="password"
        autoComplete="current-password"
        aria-invalid={errors.password ? true : undefined}
        aria-describedby={errors.password ? 'password-error' : undefined}
        {...form.register('password')}
      />
      {errors.password ? (
        <p id="password-error" className="field-error">
          {errors.password.message}
        </p>
      ) : null}

      <div aria-live="polite">
        {login.error ? <p role="alert">{errorMessage(login.error)}</p> : null}
      </div>
      <button type="submit" disabled={login.isPending}>
        {login.isPending ? 'Checking...' : 'Continue'}
      </button>
    </form>
  );
}

interface CodeStepProps {
  otpauthUri: string | null;
  onExpired: (message: string) => void;
}

function CodeStep({ otpauthUri, onExpired }: CodeStepProps) {
  const queryClient = useQueryClient();
  const form = useForm<CodeForm>({ resolver: zodResolver(codeSchema) });
  const verify = useMutation({
    mutationFn: (values: CodeForm) => apiPost<CurrentStaffResponse>('/api/auth/code', values),
    onSuccess: async (staff) => {
      queryClient.setQueryData(SESSION_QUERY_KEY, staff);
      // Asking again picks up the CSRF cookie the login replaced.
      await queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
    },
    onError: (error) => {
      if (error instanceof ApiRequestError && error.code === 'LOGIN_EXPIRED') {
        onExpired(error.message);
      }
    },
  });
  const { errors } = form.formState;

  return (
    <>
      {otpauthUri ? <AuthenticatorSetup otpauthUri={otpauthUri} /> : null}
      <form
        noValidate
        onSubmit={(event) => {
          void form.handleSubmit((values) => {
            verify.mutate(values);
          })(event);
        }}
      >
        <label htmlFor="code">Authenticator code</label>
        <input
          id="code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          aria-invalid={errors.code ? true : undefined}
          aria-describedby={errors.code ? 'code-error' : undefined}
          {...form.register('code')}
        />
        {errors.code ? (
          <p id="code-error" className="field-error">
            {errors.code.message}
          </p>
        ) : null}
        <div aria-live="polite">
          {verify.error ? <p role="alert">{errorMessage(verify.error)}</p> : null}
        </div>
        <button type="submit" disabled={verify.isPending}>
          {verify.isPending ? 'Checking...' : 'Log in'}
        </button>
      </form>
    </>
  );
}
