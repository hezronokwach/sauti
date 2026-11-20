package ke.sauti.hms.patient.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.patient.AllergyKind;
import ke.sauti.hms.patient.AllergyStatus;
import ke.sauti.hms.patient.PatientAllergy;
import org.jspecify.annotations.Nullable;

/**
 * One allergy entry, active or entered in error.
 *
 * @param id the entry id
 * @param kind an allergy, or no known allergies
 * @param substance what the patient reacts to
 * @param substanceGroup the wider group
 * @param reaction what happens
 * @param status active or entered in error
 * @param recordedAt when it was recorded
 * @param enteredInErrorReason why it was marked wrong, if it was
 */
public record AllergyResponse(
        UUID id,
        AllergyKind kind,
        @Nullable String substance,
        @Nullable String substanceGroup,
        @Nullable String reaction,
        AllergyStatus status,
        Instant recordedAt,
        @Nullable String enteredInErrorReason) {

    public static AllergyResponse of(PatientAllergy allergy) {
        return new AllergyResponse(
                allergy.getId(),
                allergy.getKind(),
                allergy.getSubstance(),
                allergy.getSubstanceGroup(),
                allergy.getReaction(),
                allergy.getStatus(),
                allergy.getCreatedAt(),
                allergy.getEnteredInErrorReason());
    }
}
