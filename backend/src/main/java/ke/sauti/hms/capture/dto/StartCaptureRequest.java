package ke.sauti.hms.capture.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ke.sauti.hms.capture.ConsentPurpose;
import org.jspecify.annotations.Nullable;

/**
 * Starting a recording.
 *
 * @param purpose reception or consultation; consent must be agreed for it
 * @param languageHint "sw" or "en", a hint for transcription
 */
public record StartCaptureRequest(
        @NotNull ConsentPurpose purpose,

        @Nullable @Pattern(regexp = "sw|en", message = "must be sw or en")
        String languageHint) {}
