import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { PrescriptionPanel } from '../prescriptions/PrescriptionPanel';
import { noteKey, useNote } from './api';
import { NoteEditor } from './NoteEditor';
import { PatientHistory } from './PatientHistory';
import { SignedNote } from './SignedNote';
import type { NoteResponse } from './types';

export type DoctorTab = 'note' | 'prescription' | 'history';

interface DoctorWorkspaceProps {
  visitId: string;
  patientId: string;
  tab: DoctorTab;
  onTabChange: (tab: DoctorTab) => void;
}

/** The doctor's side of the visit page: this visit's note, or the patient's history. */
export function DoctorWorkspace({ visitId, patientId, tab, onTabChange }: DoctorWorkspaceProps) {
  // Loaded on first opening (each read is access-logged), then kept so a half-typed line survives.
  const [prescriptionOpened, setPrescriptionOpened] = useState(false);
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
            onTabChange('note');
          }}
        >
          This visit
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'prescription'}
          className={tab === 'prescription' ? undefined : 'secondary'}
          onClick={() => {
            setPrescriptionOpened(true);
            onTabChange('prescription');
          }}
        >
          Prescription
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'history'}
          className={tab === 'history' ? undefined : 'secondary'}
          onClick={() => {
            onTabChange('history');
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
      {prescriptionOpened ? (
        <div role="tabpanel" hidden={tab !== 'prescription'}>
          <PrescriptionPanel visitId={visitId} />
        </div>
      ) : null}
      {tab === 'history' ? (
        <div role="tabpanel">
          <PatientHistory patientId={patientId} visitId={visitId} />
        </div>
      ) : null}
    </div>
  );
}
