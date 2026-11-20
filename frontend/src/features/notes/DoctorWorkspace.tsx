import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { noteKey, useNote } from './api';
import { NoteEditor } from './NoteEditor';
import { PatientHistory } from './PatientHistory';
import { SignedNote } from './SignedNote';
import type { NoteResponse } from './types';

type Tab = 'note' | 'history';

/** The doctor's side of the visit page: this visit's note, or the patient's history. */
export function DoctorWorkspace({ visitId, patientId }: { visitId: string; patientId: string }) {
  const [tab, setTab] = useState<Tab>('note');
  const note = useNote(visitId);
  const queryClient = useQueryClient();
  const update = (next: NoteResponse) => {
    queryClient.setQueryData(noteKey(visitId), next);
  };

  return (
    <div>
      <div role="tablist" aria-label="Doctor's workspace" className="actions">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'note'}
          className={tab === 'note' ? undefined : 'secondary'}
          onClick={() => {
            setTab('note');
          }}
        >
          This visit
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'history'}
          className={tab === 'history' ? undefined : 'secondary'}
          onClick={() => {
            setTab('history');
          }}
        >
          History
        </button>
      </div>
      {/* The editor stays mounted on the History tab, so typing is never lost. */}
      <div role="tabpanel" hidden={tab !== 'note'}>
        {note.error ? <p role="alert">{errorMessage(note.error)}</p> : null}
        {note.isPending ? <p role="status">Loading the note...</p> : null}
        {note.isSuccess ? (
          note.data?.status === 'SIGNED' ? (
            <SignedNote visitId={visitId} note={note.data} onChanged={update} />
          ) : (
            <NoteEditor visitId={visitId} initial={note.data} onSigned={update} />
          )
        ) : null}
      </div>
      {tab === 'history' ? (
        <div role="tabpanel">
          <PatientHistory patientId={patientId} visitId={visitId} />
        </div>
      ) : null}
    </div>
  );
}
