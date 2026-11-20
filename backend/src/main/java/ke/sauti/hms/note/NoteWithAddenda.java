package ke.sauti.hms.note;

import java.util.List;

/**
 * A note together with its addenda, oldest first.
 *
 * @param note the note
 * @param addenda its addenda
 */
public record NoteWithAddenda(ClinicalNote note, List<NoteAddendum> addenda) {}
