package ke.sauti.hms.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.capture.DraftField;
import ke.sauti.hms.capture.PieceTranscript;
import ke.sauti.hms.capture.TranscriptSegment;
import ke.sauti.hms.capture.TranscriptService;
import ke.sauti.hms.jobs.JobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drafts a reception field from a tagged piece, after its transcription. The model proposes
 * a value with the transcript words it rests on; the draft is kept only if those words are
 * really in this piece's transcript, otherwise it is dropped. Every call writes an
 * {@code ai_request} row. Safe to run twice: the piece's pending draft is replaced.
 */
@Component
public class DraftFieldHandler implements JobHandler {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final ClinicalAi ai;
    private final TranscriptService transcripts;
    private final FieldDraftRepository drafts;
    private final AiRequestRepository requests;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DraftFieldHandler(
            ClinicalAi ai,
            TranscriptService transcripts,
            FieldDraftRepository drafts,
            AiRequestRepository requests,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.ai = ai;
        this.transcripts = transcripts;
        this.drafts = drafts;
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    public String type() {
        return DraftField.JOB_TYPE;
    }

    /** Not in a transaction while the model is called; the writes at the end are one. */
    @Override
    public void handle(UUID pieceId) {
        Optional<PieceTranscript> found = transcripts.pieceTranscript(pieceId);
        if (found.isEmpty() || found.get().segments().isEmpty()) {
            return;
        }
        PieceTranscript piece = found.get();
        List<TranscriptSegment> lines = piece.segments();
        String transcript =
                String.join("\n", lines.stream().map(TranscriptSegment::getText).toList());

        Instant started = clock.instant();
        Optional<ClinicalAi.FieldProposal> proposal = ai.draftField(piece.field(), transcript);
        int durationMs = (int) Math.min(
                Integer.MAX_VALUE, Duration.between(started, clock.instant()).toMillis());

        ClaimChecker checker = new ClaimChecker(lines.stream()
                .map(s -> new ClaimChecker.Line(s.getText(), s.getConfidence(), s.getStartMs(), s.getSessionId()))
                .toList());
        Optional<ClaimChecker.Verdict> verdict =
                proposal.map(p -> checker.check(p.value(), p.quote().strip()));
        boolean kept = verdict.map(ClaimChecker.Verdict::quotePresent).orElse(false);

        transaction.executeWithoutResult(status -> {
            requests.save(new AiRequest(
                    AiRequest.FIELD_DRAFT,
                    piece.visitId(),
                    piece.recordedBy(),
                    ai.model(),
                    ai.fieldPromptVersion(),
                    AiRequest.OK,
                    proposal.map(JSON::writeValueAsString).orElse(null),
                    verdict.map(JSON::writeValueAsString).orElse(null),
                    proposal.isPresent() && !kept ? "Dropped: the quote is not in the transcript" : null,
                    durationMs));

            drafts.deleteByPieceIdAndStatus(pieceId, FieldDraft.Status.PENDING);
            if (kept) {
                ClinicalAi.FieldProposal p = proposal.get();
                Integer startMs = verdict.get().startMs();
                drafts.save(new FieldDraft(
                        piece.visitId(),
                        piece.sessionId(),
                        pieceId,
                        piece.field(),
                        p.value().strip(),
                        p.quote().strip(),
                        startMs == null ? lines.getFirst().getStartMs() : startMs));
            }
        });
    }
}
