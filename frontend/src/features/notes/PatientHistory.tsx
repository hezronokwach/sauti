import { errorMessage } from '../../api/client';
import { usePatientHistory } from './api';
import { SignedNoteText } from './SignedNote';

/**
 * The patient's earlier signed notes, newest first. No old audio or transcripts.
 * Prescriptions join this list with issue 41.
 */
export function PatientHistory({ patientId, visitId }: { patientId: string; visitId: string }) {
  const history = usePatientHistory(patientId);

  if (history.error) {
    return <p role="alert">{errorMessage(history.error)}</p>;
  }
  if (!history.data) {
    return <p role="status">Loading history...</p>;
  }
  const earlier = history.data.filter((note) => note.visitId !== visitId);
  if (earlier.length === 0) {
    return <p>No earlier signed notes.</p>;
  }
  return (
    <ol className="history">
      {earlier.map((note) => (
        <li key={note.id}>
          <h3>{note.signedAt ? new Date(note.signedAt).toLocaleDateString() : ''}</h3>
          <SignedNoteText note={note} />
        </li>
      ))}
    </ol>
  );
}
