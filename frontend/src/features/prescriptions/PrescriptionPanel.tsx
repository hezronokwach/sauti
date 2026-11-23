import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { errorMessage } from '../../api/client';
import {
  cancelPrescription,
  prescriptionKey,
  removeItem,
  signPrescription,
  usePrescription,
} from './api';
import { CodeConfirm } from './CodeConfirm';
import { PrescriptionItemForm } from './PrescriptionItemForm';
import { SafetyAlerts } from './SafetyAlerts';
import { SpokenInstructionsPanel } from './SpokenInstructionsPanel';
import { FREQUENCIES, type Prescription, type PrescriptionItem } from './types';

/** A line as a doctor reads it; a missing detail is shown, not hidden. */
function directions(item: PrescriptionItem): string {
  const missing = 'missing';
  const dose =
    item.doseValue === null
      ? missing
      : `${String(item.doseValue)} ${item.doseUnit?.toLowerCase() ?? missing}`;
  const frequency = item.frequency ? FREQUENCIES[item.frequency] : missing;
  const days = item.durationDays === null ? missing : `${String(item.durationDays)} days`;
  return `Dose: ${dose}. Frequency: ${frequency}. Duration: ${days}. Route: ${item.route?.toLowerCase() ?? missing}. Quantity: ${item.quantity === null ? missing : String(item.quantity)}.`;
}

/** The doctor's draft prescription for this visit. */
export function PrescriptionPanel({ visitId }: { visitId: string }) {
  const prescription = usePrescription(visitId);
  const queryClient = useQueryClient();
  const [editingId, setEditingId] = useState<string | null>(null);

  const update = (next: Prescription) => {
    queryClient.setQueryData(prescriptionKey(visitId), next);
    setEditingId(null);
  };
  // A refused change may mean the screen is out of date, so fetch the current version.
  const reload = () => {
    void queryClient.invalidateQueries({ queryKey: prescriptionKey(visitId) });
  };
  const remove = useMutation({
    mutationFn: ({ itemId, revision }: { itemId: string; revision: number }) =>
      removeItem(visitId, itemId, revision),
    onSuccess: update,
    onError: reload,
  });

  if (prescription.isPending) return <p role="status">Loading the prescription...</p>;
  if (prescription.error) return <p role="alert">{errorMessage(prescription.error)}</p>;

  const current = prescription.data;
  const canEdit = !current || current.status === 'DRAFT';

  return (
    <section aria-labelledby="prescription-heading">
      <h2 id="prescription-heading">Prescription</h2>
      <p className="quiet">
        Demonstration medicine list and rules, not clinically reviewed. Drug interactions are not
        checked.
      </p>
      {current?.status === 'SIGNED' ? (
        <p role="status">Signed. This prescription is final.</p>
      ) : null}
      {remove.error ? <p role="alert">{errorMessage(remove.error)}</p> : null}
      {current && current.items.length > 0 ? (
        <ul aria-label="Medicines on this prescription">
          {current.items.map((item) => (
            <li key={item.id}>
              {editingId === item.id ? (
                <PrescriptionItemForm
                  key={`${item.id}-${String(current.revision)}`}
                  visitId={visitId}
                  revision={current.revision}
                  editing={item}
                  onSaved={update}
                  onFailed={reload}
                  onCancel={() => {
                    setEditingId(null);
                  }}
                />
              ) : (
                <>
                  <strong>
                    {item.medicine.name} {item.medicine.strength}
                  </strong>{' '}
                  ({item.medicine.form})
                  <br />
                  {directions(item)}
                  {item.instructions ? (
                    <>
                      <br />
                      {item.instructions}
                    </>
                  ) : null}
                  {canEdit ? (
                    <div className="actions">
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Change ${item.medicine.name}`}
                        onClick={() => {
                          setEditingId(item.id);
                        }}
                      >
                        Change
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        aria-label={`Remove ${item.medicine.name}`}
                        disabled={remove.isPending}
                        onClick={() => {
                          remove.mutate({ itemId: item.id, revision: current.revision });
                        }}
                      >
                        Remove
                      </button>
                    </div>
                  ) : null}
                </>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p>No medicines yet.</p>
      )}
      {current ? (
        <SafetyAlerts
          visitId={visitId}
          alerts={current.alerts}
          canAct={current.status === 'DRAFT'}
          onChanged={update}
          onFailed={reload}
        />
      ) : null}
      {canEdit && !editingId ? (
        <PrescriptionItemForm
          visitId={visitId}
          revision={current?.revision ?? null}
          onSaved={update}
          onFailed={reload}
        />
      ) : null}
      {current?.status === 'DRAFT' && current.items.length > 0 && !editingId ? (
        <CodeConfirm
          key={`sign-${String(current.revision)}`}
          action="Sign prescription"
          disabled={current.alerts.some((alert) => alert.status === 'OPEN')}
          disabledNote="Deal with every safety alert before signing."
          run={async (code) => {
            update(await signPrescription(visitId, current.revision, code));
          }}
          onFailed={reload}
        />
      ) : null}
      {current?.status === 'SIGNED' ? (
        <SpokenInstructionsPanel visitId={visitId} prescriptionId={current.id} />
      ) : null}
      {current?.status === 'SIGNED' ? (
        <CodeConfirm
          action="Cancel prescription"
          run={async (code) => {
            await cancelPrescription(visitId, current.revision, code);
            queryClient.setQueryData(prescriptionKey(visitId), null);
          }}
          onFailed={reload}
        />
      ) : null}
    </section>
  );
}
