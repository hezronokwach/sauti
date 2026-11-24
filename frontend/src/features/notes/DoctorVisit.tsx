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

/** The doctor's working area: the note and prescription beside the consultation recording. */
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

  return (
    <div className="columns">
      <DoctorWorkspace visitId={visitId} patientId={patientId} tab={tab} onTabChange={setTab} />
      <RecordingPanel
        visitId={visitId}
        purpose="RECORD_CONSULTATION"
        language={language}
        onGoToSummary={canSummarise ? goToSummary : undefined}
      />
    </div>
  );
}
