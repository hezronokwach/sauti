import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { addAddendum } from './api';
import type { NoteResponse } from './types';
import { SECTIONS } from './types';

const when = (iso: string) => new Date(iso).toLocaleString();

/** A signed note's text and addenda, read-only. */
export function SignedNoteText({ note }: { note: NoteResponse }) {
  return (
    <>
      <dl>
        {SECTIONS.map(({ name, label }) =>
          note[name] ? (
            <div key={name}>
              <dt>{label}</dt>
              <dd className="note-text">{note[name]}</dd>
            </div>
          ) : null,
        )}
      </dl>
      {note.addenda.length > 0 ? (
        <>
          <h3>Addenda</h3>
          <ul>
            {note.addenda.map((addendum) => (
              <li key={addendum.id}>
                <span className="quiet">{when(addendum.createdAt)}:</span>{' '}
                <span className="note-text">{addendum.text}</span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </>
  );
}

interface SignedNoteProps {
  visitId: string;
  note: NoteResponse;
  onChanged: (note: NoteResponse) => void;
}

/** This visit's signed note, with a form for an addendum. */
export function SignedNote({ visitId, note, onChanged }: SignedNoteProps) {
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const add = async () => {
    setError(null);
    if (!text.trim()) {
      setError('Write the addendum first');
      return;
    }
    setBusy(true);
    try {
      onChanged(await addAddendum(visitId, text));
      setText('');
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="note-heading">
      <h2 id="note-heading">Note</h2>
      <p className="quiet">Signed {note.signedAt ? when(note.signedAt) : ''}</p>
      <SignedNoteText note={note} />
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void add();
        }}
      >
        <label htmlFor="addendum">Add an addendum</label>
        <textarea
          id="addendum"
          rows={3}
          maxLength={5000}
          value={text}
          onChange={(event) => {
            setText(event.target.value);
          }}
        />
        {error ? <p className="field-error">{error}</p> : null}
        <button type="submit" disabled={busy}>
          {busy ? 'Adding...' : 'Add addendum'}
        </button>
      </form>
    </section>
  );
}
