package ke.sauti.hms.capture.dto;

import java.util.UUID;
import ke.sauti.hms.capture.AudioPiece;
import ke.sauti.hms.capture.AudioPieceStatus;

/**
 * A received audio piece.
 *
 * @param id the piece id
 * @param sequenceNo its place in the recording
 * @param status stored, transcribed, failed or erased
 */
public record AudioPieceResponse(UUID id, int sequenceNo, AudioPieceStatus status) {

    public static AudioPieceResponse of(AudioPiece piece) {
        return new AudioPieceResponse(piece.getId(), piece.getSequenceNo(), piece.getStatus());
    }
}
