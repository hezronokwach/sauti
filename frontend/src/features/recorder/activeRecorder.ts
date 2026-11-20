import type { DraftField } from './pieceStore';
import type { Recorder } from './recorder';

/** The recorder running on this page, so form fields can tell it which field reception is on. */
let active: Recorder | null = null;

export function setActiveRecorder(recorder: Recorder | null): void {
  active = recorder;
}

/**
 * Reception moved to a field (or left the voice fields): the running recording starts a new
 * piece tagged with it, so what is said next can be drafted into that field. Does nothing
 * when nothing is being recorded.
 */
export function focusField(field: DraftField | null): void {
  active?.cut(field);
}
