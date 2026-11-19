package ke.sauti.hms.audit;

/** Kinds of clinical data whose every opening is written to the access log. */
public enum AccessKind {
    NOTE_OPENED,
    TRANSCRIPT_OPENED,
    AUDIO_PLAYED
}
