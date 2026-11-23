package ke.sauti.hms.medication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Prescribing despite a blocking alert.
 *
 * @param reason why, in the doctor's words: at least 10 characters
 * @param code a fresh authenticator code
 */
public record OverrideAlertRequest(
        @NotBlank @Size(min = 10, max = 500) String reason,
        @NotBlank String code) {}
