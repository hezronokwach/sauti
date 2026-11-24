package ke.sauti.hms.patient.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Either an allergy (substance given) or "no known allergies" (flag set, no substance).
 *
 * @param noKnownAllergies true to record that the patient was asked and has none
 * @param substance what the patient reacts to, for example amoxicillin
 * @param substanceGroup the wider group, for example penicillins, for safety checks
 * @param reaction what happens, for example rash
 */
public record RecordAllergyRequest(
        @Nullable Boolean noKnownAllergies,
        @Nullable @Size(max = 100) String substance,
        @Nullable @Size(max = 100) String substanceGroup,
        @Nullable @Size(max = 200) String reaction) {

    /** Whether this records "no known allergies"; not sent means no. */
    public boolean recordsNoKnownAllergies() {
        return Boolean.TRUE.equals(noKnownAllergies);
    }
}
