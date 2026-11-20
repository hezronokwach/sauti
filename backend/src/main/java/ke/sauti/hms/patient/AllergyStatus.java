package ke.sauti.hms.patient;

/** Allergy entries are never deleted; a wrong one is marked entered in error. */
public enum AllergyStatus {
    ACTIVE,
    ENTERED_IN_ERROR
}
