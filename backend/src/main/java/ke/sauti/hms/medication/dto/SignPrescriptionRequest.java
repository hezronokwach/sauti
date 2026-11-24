package ke.sauti.hms.medication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Signing, or cancelling a signed prescription.
 *
 * @param revision the revision the doctor read, so they act on exactly what they saw
 * @param code a fresh authenticator code
 */
public record SignPrescriptionRequest(
        @NotNull @PositiveOrZero Integer revision, @NotBlank String code) {}
