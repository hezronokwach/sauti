import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiRequestError, errorMessage } from '../../api/client';
import { saveNote } from './api';
import type { NoteResponse, NoteSections, SectionName } from './types';
import { sectionsOf } from './types';

/** A draft is saved this long after typing stops. */
export const AUTOSAVE_MS = 2_000;
/** After a failed save (not a conflict), it is tried again this long after. */
export const RETRY_MS = 5_000;

export type SaveState = 'saved' | 'unsaved' | 'saving' | 'conflict' | 'error';

export interface NoteDraft {
  sections: NoteSections;
  state: SaveState;
  error: string | null;
  /** The version the editor holds, sent with every save and with the sign. */
  version: () => number | null;
  edit: (name: SectionName, value: string) => void;
  /** Saves any unsaved typing now; resolves false if it could not be saved. */
  saveNow: () => Promise<boolean>;
  /** Replaces the editor's text with the server's, after a conflict. */
  reload: (note: NoteResponse | null) => void;
}

/**
 * The note editor's copy of the draft. Typing marks it unsaved; 2 seconds after typing
 * stops it is saved with the version the editor holds. Saves run one at a time. A 409
 * (another tab or doctor saved first, or the note was signed) stops autosaving until the
 * doctor reloads, so nothing is overwritten.
 */
export function useNoteDraft(visitId: string, initial: NoteResponse | null): NoteDraft {
  const [sections, setSections] = useState<NoteSections>(() => sectionsOf(initial));
  const [state, setState] = useState<SaveState>('saved');
  const [error, setError] = useState<string | null>(null);

  const sectionsRef = useRef(sections);
  const versionRef = useRef<number | null>(initial?.version ?? null);
  const dirty = useRef(false);
  const conflict = useRef(false);
  const inFlight = useRef<Promise<boolean> | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const alive = useRef(true);
  /** The latest saveNow, for the retry timer it sets on itself. */
  const retry = useRef<() => void>(() => undefined);

  const clearTimer = () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  };

  const saveNow = useCallback(async (): Promise<boolean> => {
    clearTimer();
    if (inFlight.current) {
      await inFlight.current;
    }
    if (conflict.current) {
      return false;
    }
    if (!dirty.current) {
      return true;
    }
    dirty.current = false;
    setState('saving');
    const request = { ...sectionsRef.current, version: versionRef.current };
    const attempt = saveNote(visitId, request).then(
      (saved) => {
        versionRef.current = saved.version;
        setError(null);
        setState(dirty.current ? 'unsaved' : 'saved');
        return true;
      },
      (failure: unknown) => {
        if (failure instanceof ApiRequestError && failure.status === 409) {
          conflict.current = true;
          setState('conflict');
          setError(failure.message);
          return false;
        }
        dirty.current = true;
        setState('error');
        setError(errorMessage(failure));
        if (alive.current) {
          timer.current = setTimeout(() => {
            retry.current();
          }, RETRY_MS);
        }
        return false;
      },
    );
    inFlight.current = attempt;
    const ok = await attempt;
    inFlight.current = null;
    return ok;
  }, [visitId]);

  useEffect(() => {
    retry.current = () => {
      void saveNow();
    };
  }, [saveNow]);

  const edit = useCallback(
    (name: SectionName, value: string) => {
      const next = { ...sectionsRef.current, [name]: value };
      sectionsRef.current = next;
      setSections(next);
      if (conflict.current) {
        return;
      }
      dirty.current = true;
      setState('unsaved');
      clearTimer();
      timer.current = setTimeout(() => {
        void saveNow();
      }, AUTOSAVE_MS);
    },
    [saveNow],
  );

  const reload = useCallback((note: NoteResponse | null) => {
    clearTimer();
    const fresh = sectionsOf(note);
    sectionsRef.current = fresh;
    versionRef.current = note?.version ?? null;
    dirty.current = false;
    conflict.current = false;
    setSections(fresh);
    setError(null);
    setState('saved');
  }, []);

  // Leaving the page saves whatever was typed in the last two seconds.
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      if (dirty.current && !conflict.current) {
        void saveNow();
      }
      clearTimer();
    };
  }, [saveNow]);

  return {
    sections,
    state,
    error,
    version: () => versionRef.current,
    edit,
    saveNow,
    reload,
  };
}
