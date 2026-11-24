import { useState } from 'react';
import { flushSync } from 'react-dom';
import type { PreferredLanguage } from '../patients/types';
import { RecordingPanel } from '../recorder/RecordingPanel';
import { useNote } from './api';
import { DoctorWorkspace } from './DoctorWorkspace';
import type { DoctorTab } from './DoctorWorkspace';

interface DoctorVisitProps {
  visitId: string;
  patientId: string;
  language: PreferredLanguage;
}

/** The doctor's working area: the consultation recording beside the note and prescription. */
export function DoctorVisit({ visitId, patientId, language }: DoctorVisitProps) {
  const [tab, setTab] = useState<DoctorTab>('note');
  const note = useNote(visitId);
  const canSummarise = note.isSuccess && note.data?.status !== 'SIGNED';

  const goToSummary = () => {
    // The tab must be showing before the summary can be scrolled to.
    flushSync(() => {
      setTab('note');
    });
    document.getElementById('summary-heading')?.scrollIntoView({ block: 'start' });
    document.getElementById('summarise-transcript')?.focus();
  };

  // Left to right in the order the doctor works: the conversation (consent, recording,
  // transcript), then the note with its AI suggestions, prescription and history.
  return (
    <div className="consult-layout">
      <RecordingPanel
        visitId={visitId}
        purpose="RECORD_CONSULTATION"
        language={language}
        onGoToSummary={canSummarise ? goToSummary : undefined}
      />
      <DoctorWorkspace visitId={visitId} patientId={patientId} tab={tab} onTabChange={setTab} />
    </div>
  );
}
