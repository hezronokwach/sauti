import { useState } from 'react';
import { ApiRequestError, errorMessage } from '../../api/client';
import type { PatientDetailsResponse } from '../patients/types';
import { focusField } from '../recorder/activeRecorder';
import type { DraftField } from '../recorder/pieceStore';
import { playAt } from '../transcript/playAudio';
import {
  confirmDraft,
  dismissDraft,
  saveContact,
  saveReason,
  useFieldDrafts,
  useReason,
  useRefreshDrafts,
} from './api';
import type { FieldDraftResponse } from './api';

const LABELS: Record<DraftField, string> = {
  REASON_FOR_VISIT: 'Reason for visit',
  RESIDENCE: 'Residence',
  NEXT_OF_KIN_NAME: 'Next of kin name',
};

const MAX_LENGTH: Record<DraftField, number> = {
  REASON_FOR_VISIT: 500,
  RESIDENCE: 200,
  NEXT_OF_KIN_NAME: 200,
};

interface VoiceFieldsProps {
  visitId: string;
  patient: PatientDetailsResponse;
}

/**
 * Reception's free-text fields with voice drafts. While recording, moving to a field starts a
 * new piece tagged with it; a few seconds later a draft may appear under the field, greyed,
 * with the words it came from and a play button. Nothing reaches the record until reception
 * uses the draft (or types) and saves through the normal checks.
 */
export function VoiceFields({ visitId, patient }: VoiceFieldsProps) {
  const reason = useReason(visitId);
  const drafts = useFieldDrafts(visitId);

  if (!reason.data) {
    return reason.error ? <p role="alert">{errorMessage(reason.error)}</p> : null;
  }
  return (
    <section aria-labelledby="voice-fields-heading" className="panel">
      <h2 id="voice-fields-heading">Details</h2>
      <p className="quiet">
        Record while asking: what the patient says on each field is drafted under it. Name, date of
        birth, phone and ID numbers are always typed.
      </p>
      <DraftableField
        visitId={visitId}
        field="REASON_FOR_VISIT"
        initial={reason.data.reasonForVisit ?? ''}
        drafts={drafts.data ?? []}
        save={(value) => saveReason(visitId, value).then(() => undefined)}
      />
      <ContactFields visitId={visitId} patient={patient} drafts={drafts.data ?? []} />
      {drafts.error ? <p role="alert">{errorMessage(drafts.error)}</p> : null}
    </section>
  );
}

function ContactFields({
  visitId,
  patient,
  drafts,
}: {
  visitId: string;
  patient: PatientDetailsResponse;
  drafts: FieldDraftResponse[];
}) {
  // The contact endpoint sets both fields, so each save sends the other's saved value.
  const [saved, setSaved] = useState({
    residence: patient.residence ?? '',
    nextOfKinName: patient.nextOfKinName ?? '',
  });
  return (
    <>
      <DraftableField
        visitId={visitId}
        field="RESIDENCE"
        initial={saved.residence}
        drafts={drafts}
        save={async (value) => {
          await saveContact(patient.id, value, saved.nextOfKinName);
          setSaved((s) => ({ ...s, residence: value }));
        }}
      />
      <DraftableField
        visitId={visitId}
        field="NEXT_OF_KIN_NAME"
        initial={saved.nextOfKinName}
        drafts={drafts}
        save={async (value) => {
          await saveContact(patient.id, saved.residence, value);
          setSaved((s) => ({ ...s, nextOfKinName: value }));
        }}
      />
    </>
  );
}

interface DraftableFieldProps {
  visitId: string;
  field: DraftField;
  initial: string;
  drafts: FieldDraftResponse[];
  save: (value: string) => Promise<void>;
}

function DraftableField({ visitId, field, initial, drafts, save }: DraftableFieldProps) {
  const [value, setValue] = useState(initial);
  const [usedDraft, setUsedDraft] = useState<string | null>(null);
  const [state, setState] = useState<'idle' | 'saving' | 'saved'>('idle');
  const [error, setError] = useState<string | null>(null);
  const refresh = useRefreshDrafts(visitId);
  const draft = drafts.filter((d) => d.field === field && d.id !== usedDraft).at(-1);
  const id = `voice-${field}`;

  const submit = async () => {
    setError(null);
    if (field === 'REASON_FOR_VISIT' && !value.trim()) {
      setError('Write the reason for visit');
      return;
    }
    setState('saving');
    try {
      await save(value.trim());
      if (usedDraft) {
        await confirmDraft(visitId, usedDraft);
        setUsedDraft(null);
        await refresh();
      }
      setState('saved');
    } catch (failure) {
      setState('idle');
      setError(failure instanceof ApiRequestError ? failure.message : errorMessage(failure));
    }
  };

  return (
    <form
      noValidate
      className="voice-field"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <label htmlFor={id}>{LABELS[field]}</label>
      <input
        id={id}
        maxLength={MAX_LENGTH[field]}
        value={value}
        onFocus={() => {
          focusField(field);
        }}
        onChange={(event) => {
          setValue(event.target.value);
          setState('idle');
        }}
      />
      {draft ? (
        <div className="draft" aria-label={`Voice draft for ${LABELS[field]}`}>
          <p>
            Heard: <span className="draft-value">{draft.value}</span>
          </p>
          <p className="claim-quote">&ldquo;{draft.quote}&rdquo;</p>
          <div className="actions">
            <button
              type="button"
              className="secondary"
              onClick={() => {
                void playAt(draft.sessionId, draft.startMs).then(setError);
              }}
            >
              Play
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setValue(draft.value);
                setUsedDraft(draft.id);
                setState('idle');
              }}
            >
              Use this
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                void dismissDraft(visitId, draft.id).then(refresh, (failure: unknown) => {
                  setError(errorMessage(failure));
                });
              }}
            >
              Dismiss
            </button>
          </div>
        </div>
      ) : null}
      {error ? <p className="field-error">{error}</p> : null}
      <button type="submit" disabled={state === 'saving'}>
        {state === 'saving' ? 'Saving...' : state === 'saved' ? 'Saved' : 'Save'}
      </button>
    </form>
  );
}
