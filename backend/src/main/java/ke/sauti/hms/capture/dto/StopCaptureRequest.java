package ke.sauti.hms.capture.dto;

import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

/**
 * Stopping a recording.
 *
 * @param expectedPieces how many pieces the browser recorded, so transcription knows when it is done
 */
public record StopCaptureRequest(@Nullable @Min(0) Integer expectedPieces) {}
