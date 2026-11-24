/** Mirrors of the backend's note DTOs. */

/** The written parts of a note. */
export interface NoteSections {
  presentingComplaint: string;
  history: string;
  examination: string;
  assessment: string;
  plan: string;
}

export type SectionName = keyof NoteSections;

/** A draft save (backend: SaveNoteRequest). */
export interface SaveNoteRequest extends Partial<NoteSections> {
  version: number | null;
  aiAssisted?: boolean;
}

/** One addendum (backend: AddendumResponse). */
export interface AddendumResponse {
  id: string;
  authorId: string;
  text: string;
  createdAt: string;
}

/** A visit's note (backend: NoteResponse). */
export interface NoteResponse {
  id: string;
  visitId: string;
  patientId: string;
  authorId: string;
  status: 'DRAFT' | 'SIGNED';
  version: number;
  presentingComplaint: string | null;
  history: string | null;
  examination: string | null;
  assessment: string | null;
  plan: string | null;
  aiAssisted: boolean;
  updatedAt: string;
  signedAt: string | null;
  signedBy: string | null;
  addenda: AddendumResponse[];
}

export const SECTIONS: { name: SectionName; label: string }[] = [
  { name: 'presentingComplaint', label: 'Presenting complaint' },
  { name: 'history', label: 'History' },
  { name: 'examination', label: 'Examination' },
  { name: 'assessment', label: 'Assessment' },
  { name: 'plan', label: 'Plan' },
];

export const EMPTY_SECTIONS: NoteSections = {
  presentingComplaint: '',
  history: '',
  examination: '',
  assessment: '',
  plan: '',
};

export function sectionsOf(note: NoteResponse | null): NoteSections {
  if (!note) {
    return EMPTY_SECTIONS;
  }
  return {
    presentingComplaint: note.presentingComplaint ?? '',
    history: note.history ?? '',
    examination: note.examination ?? '',
    assessment: note.assessment ?? '',
    plan: note.plan ?? '',
  };
}
