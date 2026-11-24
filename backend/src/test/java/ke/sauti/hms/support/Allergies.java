package ke.sauti.hms.support;

import ke.sauti.hms.patient.dto.RecordAllergyRequest;

/** Allergy answers for tests: triage cannot send a patient on until allergies are asked. */
public final class Allergies {

    /** The patient was asked and has none. */
    public static final RecordAllergyRequest NONE_KNOWN = new RecordAllergyRequest(true, null, null, null);

    private Allergies() {}
}
