package ke.sauti.hms.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Marking an allergy entry as a mistake. The entry stays in the history.
 *
 * @param reason why it was wrong, for example "recorded for the wrong patient"
 */
public record EnteredInErrorRequest(
        @NotBlank @Size(max = 200) String reason) {}
