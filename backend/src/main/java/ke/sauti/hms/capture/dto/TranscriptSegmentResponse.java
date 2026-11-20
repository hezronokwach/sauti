package ke.sauti.hms.capture.dto;

import ke.sauti.hms.capture.Confidence;
import ke.sauti.hms.capture.TranscriptSegment;

/**
 * One transcript segment, decrypted.
 *
 * @param orderNo its position; poll with after=the last one received
 * @param startMs start, from the beginning of the recording
 * @param endMs end, from the beginning of the recording
 * @param text what was heard
 * @param confidence HIGH, or REVIEW and LOW to show marked for checking
 */
public record TranscriptSegmentResponse(int orderNo, int startMs, int endMs, String text, Confidence confidence) {

    public static TranscriptSegmentResponse of(TranscriptSegment segment) {
        return new TranscriptSegmentResponse(
                segment.getOrderNo(),
                segment.getStartMs(),
                segment.getEndMs(),
                segment.getText(),
                segment.getConfidence());
    }
}
