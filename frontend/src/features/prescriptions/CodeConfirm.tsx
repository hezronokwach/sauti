import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { refusalMessage } from './api';

interface CodeConfirmProps {
  /** The button text, for example "Sign prescription". */
  action: string;
  disabled?: boolean;
  /** Shown instead of acting while disabled. */
  disabledNote?: string;
  run: (code: string) => Promise<unknown>;
  onFailed: () => void;
}

/** An action that needs a fresh authenticator code. The code is cleared after every try. */
export function CodeConfirm({ action, disabled, disabledNote, run, onFailed }: CodeConfirmProps) {
  const [code, setCode] = useState('');
  const mutation = useMutation({
    mutationFn: run,
    onSettled: () => {
      setCode('');
    },
    onError: onFailed,
  });

  if (disabled) return <p className="quiet">{disabledNote}</p>;

  return (
    <form
      aria-label={action}
      onSubmit={(event) => {
        event.preventDefault();
        mutation.mutate(code);
      }}
    >
      <label htmlFor={`code-${action}`}>Authenticator code</label>
      <input
        id={`code-${action}`}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        value={code}
        onChange={(event) => {
          setCode(event.target.value);
        }}
      />
      {mutation.error ? <p role="alert">{refusalMessage(mutation.error)}</p> : null}
      <button type="submit" disabled={mutation.isPending || code.length !== 6}>
        {action}
      </button>
    </form>
  );
}
