package ke.sauti.hms.capture;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.jobs.JobFailure;
import ke.sauti.hms.jobs.JobHandler;
import ke.sauti.hms.jobs.JobQueue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transcribes one audio piece. Safe to run twice: a piece already transcribed or erased is
 * skipped, and the piece's old segments are deleted before the new ones are written.
 * When the last expected piece of a stopped recording is done, the recording is complete.
 */
@Component
public class TranscribeHandler implements JobHandler {

    public static final String TYPE = "TRANSCRIBE";

    /** How much of what was just said goes into the prompt, to keep context across pieces. */
    static final int CONTEXT_CHARS = 200;

    /** Segments are ordered across a recording as piece sequence * this + position in the piece. */
    static final int ORDER_STEP = 1_000;

    private final AudioPieceRepository pieces;
    private final CaptureSessionRepository sessions;
    private final TranscriptSegmentRepository segments;
    private final AudioStore store;
    private final SpeechToText speechToText;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final JobQueue jobs;

    public TranscribeHandler(
            AudioPieceRepository pieces,
            CaptureSessionRepository sessions,
            TranscriptSegmentRepository segments,
            AudioStore store,
            SpeechToText speechToText,
            TransactionTemplate transaction,
            Clock clock,
            JobQueue jobs) {
        this.pieces = pieces;
        this.sessions = sessions;
        this.segments = segments;
        this.store = store;
        this.speechToText = speechToText;
        this.transaction = transaction;
        this.clock = clock;
        this.jobs = jobs;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(UUID pieceId) throws Exception {
        AudioPiece piece = pieces.findById(pieceId).orElseThrow(() -> new JobFailure.Permanent("The piece is gone"));
        CaptureSession session = sessions.findById(piece.getSessionId())
                .orElseThrow(() -> new JobFailure.Permanent("The recording is gone"));
        if (piece.getStatus() == AudioPieceStatus.TRANSCRIBED
                || piece.getStatus() == AudioPieceStatus.ERASED
                || session.getStatus() == CaptureStatus.ERASED) {
            return;
        }
        int orderBase = piece.getSequenceNo() * ORDER_STEP;
        List<TranscriptSegment> before =
                segments.findTop5BySessionIdAndOrderNoLessThanOrderByOrderNoDesc(session.getId(), orderBase);
        String previousText = before.isEmpty() ? null : before.getFirst().getText();

        byte[] audio = store.get(piece.getStorageKey());
        List<SpeechToText.Segment> heard =
                speechToText.transcribe(audio, piece.getMimeType(), session.getLanguageHint(), prompt(before));
        List<TranscriptFilter.Kept> kept = TranscriptFilter.apply(heard, previousText);

        transaction.executeWithoutResult(status -> save(piece.getId(), session.getId(), orderBase, kept));
    }

    private void save(UUID pieceId, UUID sessionId, int orderBase, List<TranscriptFilter.Kept> kept) {
        AudioPiece piece = pieces.findById(pieceId).orElseThrow();
        CaptureSession session = sessions.findById(sessionId).orElseThrow();
        if (session.getStatus() == CaptureStatus.ERASED || piece.getStatus() == AudioPieceStatus.ERASED) {
            return;
        }
        segments.deleteByPieceId(pieceId);
        for (int i = 0; i < kept.size(); i++) {
            SpeechToText.Segment s = kept.get(i).segment();
            segments.save(new TranscriptSegment(
                    pieceId,
                    sessionId,
                    orderBase + i,
                    piece.getOffsetMs() + s.startMs(),
                    piece.getOffsetMs() + s.endMs(),
                    s.text().trim(),
                    kept.get(i).confidence(),
                    s.avgLogprob(),
                    s.noSpeechProb(),
                    s.compressionRatio(),
                    clock.instant()));
        }
        piece.markStatus(AudioPieceStatus.TRANSCRIBED);
        pieces.flush();
        if (piece.getFieldTag() != null && !kept.isEmpty()) {
            jobs.enqueue(DraftField.JOB_TYPE, pieceId);
        }
        Integer expected = session.getExpectedPieces();
        if (session.getStatus() == CaptureStatus.STOPPED
                && expected != null
                && pieces.countBySessionIdAndStatus(sessionId, AudioPieceStatus.TRANSCRIBED) >= expected) {
            session.complete();
        }
    }

    /** The medicine glossary, then the last words heard, so names and context carry across pieces. */
    static String prompt(List<TranscriptSegment> newestFirst) {
        StringBuilder recent = new StringBuilder();
        for (TranscriptSegment segment : newestFirst.reversed()) {
            recent.append(segment.getText()).append(' ');
        }
        String context = recent.toString().trim();
        if (context.length() > CONTEXT_CHARS) {
            context = context.substring(context.length() - CONTEXT_CHARS);
        }
        return MedicineGlossary.promptLine() + ". " + context;
    }
}
