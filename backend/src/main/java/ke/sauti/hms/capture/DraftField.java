package ke.sauti.hms.capture;

/**
 * Fields drafted from speech. New recordings may only use {@link #REASON_FOR_VISIT}; the other
 * two remain so drafts already stored can still be read. Identity fields are always typed.
 */
public enum DraftField {
    REASON_FOR_VISIT,
    RESIDENCE,
    NEXT_OF_KIN_NAME;

    /** The job that drafts a tagged piece's field after transcription. */
    public static final String JOB_TYPE = "DRAFT_FIELD";
}
