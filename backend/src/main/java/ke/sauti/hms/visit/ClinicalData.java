package ke.sauti.hms.visit;

import ke.sauti.hms.audit.AccessKind;

/**
 * The kinds of clinical data the access policy guards, each with the access-log entry an
 * allowed read writes.
 */
public enum ClinicalData {
    REASON_FOR_VISIT(AccessKind.REASON_FOR_VISIT_VIEWED),
    ALLERGIES(AccessKind.ALLERGIES_VIEWED),
    VITALS(AccessKind.VITALS_VIEWED),
    NOTE(AccessKind.NOTE_OPENED),
    TRANSCRIPT(AccessKind.TRANSCRIPT_OPENED),
    AUDIO(AccessKind.AUDIO_PLAYED),
    PRESCRIPTION(AccessKind.PRESCRIPTION_OPENED),
    /** A signed note from an earlier visit of the same patient. */
    PAST_SIGNED_NOTE(AccessKind.NOTE_OPENED),
    /** A prescription from an earlier visit of the same patient. */
    PAST_PRESCRIPTION(AccessKind.PRESCRIPTION_OPENED);

    private final AccessKind accessKind;

    ClinicalData(AccessKind accessKind) {
        this.accessKind = accessKind;
    }

    public AccessKind accessKind() {
        return accessKind;
    }

    boolean isPastRecord() {
        return this == PAST_SIGNED_NOTE || this == PAST_PRESCRIPTION;
    }
}
