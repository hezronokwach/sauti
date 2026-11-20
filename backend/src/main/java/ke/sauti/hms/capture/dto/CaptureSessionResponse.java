package ke.sauti.hms.capture.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.capture.CaptureSession;
import ke.sauti.hms.capture.CaptureStatus;
import ke.sauti.hms.capture.ConsentPurpose;
import org.jspecify.annotations.Nullable;

/**
 * A recording session.
 *
 * @param id the session id
 * @param visitId the visit
 * @param purpose reception or consultation
 * @param status recording, stopped, complete or erased
 * @param languageHint the transcription hint, if any
 * @param expectedPieces pieces the browser reported when stopping
 * @param startedAt when recording started
 * @param stoppedAt when it stopped
 */
public record CaptureSessionResponse(
        UUID id,
        UUID visitId,
        ConsentPurpose purpose,
        CaptureStatus status,
        @Nullable String languageHint,
        @Nullable Integer expectedPieces,
        Instant startedAt,
        @Nullable Instant stoppedAt) {

    public static CaptureSessionResponse of(CaptureSession session) {
        return new CaptureSessionResponse(
                session.getId(),
                session.getVisitId(),
                session.getPurpose(),
                session.getStatus(),
                session.getLanguageHint(),
                session.getExpectedPieces(),
                session.getStartedAt(),
                session.getStoppedAt());
    }
}
