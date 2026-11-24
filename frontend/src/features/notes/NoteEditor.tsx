import { useState } from 'react';
import { ApiRequestError, errorMessage } from '../../api/client';
import { acceptClaims } from '../summary/api';
import { SummaryDrawer } from '../summary/SummaryDrawer';
import { fetchNote, signNote } from './api';
import type { NoteResponse } from './types';
import { SECTIONS } from './types';
import { useNoteDraft } from './useNoteDraft';
import type { SaveState } from './useNoteDraft';

const SAVE_TEXT: Record<SaveState, string> = {
  saved: 'All changes saved',
  unsaved: 'Unsaved changes',
  saving: 'Saving...',
  conflict: 'Not saved',
  error: 'Not saved; trying again',
};

interface NoteEditorProps {
  visitId: string;
  initial: NoteResponse | null;
  /** Called with the signed note. */
  onSigned: (note: NoteResponse) => void;
}

/** The failure to show: the backend's own words, even for a 403 wrong code or a refused claim. */
function backendReason(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : errorMessage(error);
}

/**
 * The draft note, saved 2 seconds after typing stops. Signing first saves what is on
 * screen, then asks the server to sign exactly that version with an authenticator code.
 */
export function NoteEditor({ visitId, initial, onSigned }: NoteEditorProps) {
  const draft = useNoteDraft(visitId, initial);
  const [signing, setSigning] = useState(false);
  const [code, setCode] = useState('');
  const [signError, setSignError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** Saves the doctor's own typing first, then adds the claims and shows the note as it now is. */
  const acceptFromSummary = async (requestId: string, claims: number[]): Promise<string | null> => {
    if (!(await draft.saveNow())) {
      return draft.inConflict()
        ? 'The note changed elsewhere; reload it before accepting claims.'
        : 'The note could not be saved, so nothing was added. Try again.';
    }
    try {
      draft.reload(await acceptClaims(visitId, requestId, claims, draft.version()));
      return null;
    } catch (failure) {
      return backendReason(failure);
    }
  };

  const reload = async () => {
    try {
      draft.reload(await fetchNote(visitId));
    } catch (failure) {
      setSignError(errorMessage(failure));
    }
  };

  const sign = async () => {
    setSignError(null);
    if (!draft.sections.assessment.trim() || !draft.sections.plan.trim()) {
      setSignError('Write the assessment and the plan before signing');
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setSignError('Enter the 6-digit code from your authenticator app');
      return;
    }
    setBusy(true);
    try {
      if (!(await draft.saveNow())) {
        // A conflict has its own warning above; anything else is said here.
        if (!draft.inConflict()) {
          setSignError('The note could not be saved, so it was not signed. Try again.');
        }
        return;
      }
      const version = draft.version();
      if (version === null) {
        setSignError('Write the note before signing');
        return;
      }
      onSigned(await signNote(visitId, version, code));
    } catch (failure) {
      setSignError(backendReason(failure));
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="note-heading">
      <h2 id="note-heading">Note</h2>
      <p className="quiet">
        You write the note. Summarise suggests lines from the conversation; each one you accept is
        added to its section below, where you can still change it.
      </p>
      <p role="status" className="quiet save-state">
        {SAVE_TEXT[draft.state]}
      </p>
      {draft.state === 'conflict' ? (
        <div role="alert" className="notice">
          <p>
            {draft.error} Your latest typing is still on screen but has not been saved. Copy
            anything you need, then reload to see the saved note.
          </p>
          <button
            type="button"
            onClick={() => {
              void reload();
            }}
          >
            Reload the note
          </button>
        </div>
      ) : null}
      <SummaryDrawer visitId={visitId} onAccept={acceptFromSummary} />
      {SECTIONS.map(({ name, label }) => (
        <div key={name}>
          <label htmlFor={`note-${name}`}>{label}</label>
          <textarea
            id={`note-${name}`}
            rows={name === 'plan' || name === 'history' ? 4 : 3}
            value={draft.sections[name]}
            onChange={(event) => {
              draft.edit(name, event.target.value);
            }}
          />
        </div>
      ))}
      {signing ? (
        <form
          className="panel"
          aria-label="Sign the note"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void sign();
          }}
        >
          <p>Signing is final: the note cannot change after it, only have addenda.</p>
          <label htmlFor="sign-code">Authenticator code</label>
          <input
            id="sign-code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(event) => {
              setCode(event.target.value.replace(/\D/g, ''));
            }}
          />
          {signError ? <p className="field-error">{signError}</p> : null}
          <div className="actions">
            <button type="submit" disabled={busy}>
              {busy ? 'Signing...' : 'Sign note'}
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setSigning(false);
                setSignError(null);
                setCode('');
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <div className="actions">
          <button
            type="button"
            disabled={draft.state === 'conflict'}
            onClick={() => {
              setSigning(true);
            }}
          >
            Sign...
          </button>
        </div>
      )}
    </section>
  );
}
