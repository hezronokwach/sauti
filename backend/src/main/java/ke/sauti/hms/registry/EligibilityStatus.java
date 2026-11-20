package ke.sauti.hms.registry;

/**
 * A patient's SHA eligibility. PENDING while the background check runs; UNAVAILABLE when the
 * service did not answer (the check is retried). None of these ever blocks care.
 */
public enum EligibilityStatus {
    PENDING,
    ELIGIBLE,
    INELIGIBLE,
    NOT_FOUND,
    UNAVAILABLE
}
