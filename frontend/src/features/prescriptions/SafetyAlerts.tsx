import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { acknowledgeAlert, overrideAlert, refusalMessage } from './api';
import type { Prescription, SafetyAlert } from './types';

const MIN_REASON = 10;

interface AlertRowProps {
  visitId: string;
  alert: SafetyAlert;
  canAct: boolean;
  onChanged: (next: Prescription) => void;
  onFailed: () => void;
}

/** One alert, with the only action its level allows. */
function AlertRow({ visitId, alert, canAct, onChanged, onFailed }: AlertRowProps) {
  const [overriding, setOverriding] = useState(false);
  const [reason, setReason] = useState('');
  const [code, setCode] = useState('');
  const acknowledge = useMutation({
    mutationFn: () => acknowledgeAlert(visitId, alert.id),
    onSuccess: onChanged,
    onError: onFailed,
  });
  const override = useMutation({
    mutationFn: () => overrideAlert(visitId, alert.id, reason.trim(), code),
    onSuccess: onChanged,
    onError: () => {
      setCode('');
      onFailed();
    },
  });
  const failure = acknowledge.error ?? override.error;
  const open = alert.status === 'OPEN';

  return (
    <li>
      <strong>{alert.level === 'BLOCKING' ? 'Blocks signing' : 'Needs review'}:</strong>{' '}
      {alert.message}
      {alert.status === 'OVERRIDDEN' ? (
        <p className="quiet">Overridden: {alert.overrideReason}</p>
      ) : null}
      {alert.status === 'ACKNOWLEDGED' ? <p className="quiet">Acknowledged</p> : null}
      {failure ? <p role="alert">{refusalMessage(failure)}</p> : null}
      {open && canAct && alert.level === 'BLOCKING' && !alert.canOverride ? (
        <p className="quiet">Fill in the missing details to clear this.</p>
      ) : null}
      {open && canAct && alert.level === 'REVIEW' ? (
        <div className="actions">
          <button
            type="button"
            className="secondary"
            disabled={acknowledge.isPending}
            onClick={() => {
              acknowledge.mutate();
            }}
          >
            Acknowledge
          </button>
        </div>
      ) : null}
      {open && canAct && alert.canOverride && !overriding ? (
        <div className="actions">
          <button
            type="button"
            className="secondary"
            onClick={() => {
              setOverriding(true);
            }}
          >
            Override
          </button>
        </div>
      ) : null}
      {open && canAct && overriding ? (
        <form
          aria-label="Override this alert"
          onSubmit={(event) => {
            event.preventDefault();
            override.mutate();
          }}
        >
          <label htmlFor={`reason-${alert.id}`}>Why is it safe? (at least 10 characters)</label>
          <textarea
            id={`reason-${alert.id}`}
            rows={2}
            maxLength={500}
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
            }}
          />
          <label htmlFor={`code-${alert.id}`}>Authenticator code</label>
          <input
            id={`code-${alert.id}`}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(event) => {
              setCode(event.target.value);
            }}
          />
          <div className="actions">
            <button
              type="submit"
              disabled={
                override.isPending || reason.trim().length < MIN_REASON || code.length !== 6
              }
            >
              Confirm override
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setOverriding(false);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : null}
    </li>
  );
}

interface SafetyAlertsProps {
  visitId: string;
  alerts: SafetyAlert[];
  /** False once signed, or for someone who only reads the prescription. */
  canAct: boolean;
  onChanged: (next: Prescription) => void;
  onFailed: () => void;
}

/** The problems the safety rules found. They state the problem and never suggest a medicine. */
export function SafetyAlerts({ visitId, alerts, canAct, onChanged, onFailed }: SafetyAlertsProps) {
  if (alerts.length === 0) return null;
  return (
    <section aria-labelledby="alerts-heading">
      <h3 id="alerts-heading">Safety alerts</h3>
      <ul aria-label="Safety alerts">
        {alerts.map((alert) => (
          <AlertRow
            key={alert.id}
            visitId={visitId}
            alert={alert}
            canAct={canAct}
            onChanged={onChanged}
            onFailed={onFailed}
          />
        ))}
      </ul>
    </section>
  );
}
