package ke.sauti.hms.capture;

import java.util.List;
import java.util.UUID;

/**
 * One piece's transcript, for drafting the field it was recorded for.
 *
 * @param visitId the visit
 * @param sessionId the recording
 * @param recordedBy who made the recording
 * @param pieceId the piece
 * @param field the field the piece was tagged with
 * @param segments the piece's lines, in order
 */
public record PieceTranscript(
        UUID visitId,
        UUID sessionId,
        UUID recordedBy,
        UUID pieceId,
        DraftField field,
        List<TranscriptSegment> segments) {}
