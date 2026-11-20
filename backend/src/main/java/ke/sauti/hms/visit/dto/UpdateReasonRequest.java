package ke.sauti.hms.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The reason for visit, typed or confirmed from a voice draft.
 *
 * @param reasonForVisit the patient's words
 */
public record UpdateReasonRequest(@NotBlank @Size(max = 500) String reasonForVisit) {}
