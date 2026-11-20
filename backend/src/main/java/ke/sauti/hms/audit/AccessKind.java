package ke.sauti.hms.audit;

/** Kinds of clinical data whose every opening is written to the access log. */
public enum AccessKind {
    NOTE_OPENED,
    TRANSCRIPT_OPENED,
    AUDIO_PLAYED,
    REASON_FOR_VISIT_VIEWED,
    ALLERGIES_VIEWED,
    VITALS_VIEWED,
    PRESCRIPTION_OPENED
}
