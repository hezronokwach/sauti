package ke.sauti.hms.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Signing a note: the version the doctor read, so they sign exactly what they saw, and a
 * fresh authenticator code.
 *
 * @param version the note version shown to the doctor
 * @param code the six-digit authenticator code
 */
public record SignNoteRequest(
        @NotNull @PositiveOrZero Long version, @NotBlank String code) {}
