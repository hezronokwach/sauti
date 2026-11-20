import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiRequestError, errorMessage } from '../../api/client';
import { confirmReason, saveReason, useReason } from './api';

/**
 * The triage nurse checks the reason for visit with the patient (it may have been drafted
 * from speech at reception), corrects it if needed, and confirms it.
 */
export function ReasonConfirm({ visitId }: { visitId: string }) {
  const reason = useReason(visitId);
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['visits', visitId, 'reason'] });
  const run = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      setEditing(null);
      await refresh();
    } catch (failure) {
      setError(failure instanceof ApiRequestError ? failure.message : errorMessage(failure));
    }
  };

  if (!reason.data) {
    return reason.error ? <p role="alert">{errorMessage(reason.error)}</p> : null;
  }
  const { reasonForVisit, confirmed } = reason.data;

  return (
    <section aria-labelledby="reason-heading">
      <h2 id="reason-heading">Reason for visit</h2>
      {editing !== null ? (
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            if (!editing.trim()) {
              setError('Write the reason for visit');
              return;
            }
            void run(() => saveReason(visitId, editing.trim()));
          }}
        >
          <label htmlFor="reason-edit">Reason, as the patient says it</label>
          <input
            id="reason-edit"
            maxLength={500}
            value={editing}
            onChange={(event) => {
              setEditing(event.target.value);
            }}
          />
          <div className="actions">
            <button type="submit">Save</button>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEditing(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <>
          <p>{reasonForVisit ?? 'Not recorded yet.'}</p>
          {reasonForVisit ? (
            confirmed ? (
              <p role="status" className="claim-ok">
                Confirmed with the patient
              </p>
            ) : (
              <p className="notice">Check this with the patient before confirming.</p>
            )
          ) : null}
          <div className="actions">
            {reasonForVisit && !confirmed ? (
              <button
                type="button"
                onClick={() => {
                  void run(() => confirmReason(visitId));
                }}
              >
                Confirm with the patient
              </button>
            ) : null}
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEditing(reasonForVisit ?? '');
              }}
            >
              {reasonForVisit ? 'Correct it' : 'Write it'}
            </button>
          </div>
        </>
      )}
      {error ? <p className="field-error">{error}</p> : null}
    </section>
  );
}
