package ke.sauti.hms.capture;

/**
 * Reception's free-text fields that may be drafted from speech. Identity fields (name, date
 * of birth, phone, ID numbers) are never drafted: they are always typed.
 */
public enum DraftField {
    REASON_FOR_VISIT,
    RESIDENCE,
    NEXT_OF_KIN_NAME;

    /** The job that drafts a tagged piece's field after transcription. */
    public static final String JOB_TYPE = "DRAFT_FIELD";
}
