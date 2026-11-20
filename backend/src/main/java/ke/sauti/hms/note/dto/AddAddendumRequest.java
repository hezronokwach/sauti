package ke.sauti.hms.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A correction or addition to a signed note.
 *
 * @param text the addendum text
 */
public record AddAddendumRequest(
        @NotBlank @Size(max = 5_000) String text) {}
