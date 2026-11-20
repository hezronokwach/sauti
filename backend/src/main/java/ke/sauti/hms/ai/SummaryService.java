package ke.sauti.hms.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import ke.sauti.hms.capture.ConsentPurpose;
import ke.sauti.hms.capture.ConsentService;
import ke.sauti.hms.capture.TranscriptSegment;
import ke.sauti.hms.capture.TranscriptService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.VisitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Click-to-summarise.
 *
 * <ol>
 *   <li>In a short transaction: the doctor on the care team, the patient's consent to
 *       recording still standing, and the consultation transcript.
 *   <li>Outside any transaction: the model, tried twice at most (each call times out after
 *       20 seconds); after that the summary is unavailable.
 *   <li>Every claim is checked in plain Java against the transcript.
 *   <li>Every call writes one {@code ai_request} row, whether it worked or not.
 * </ol>
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final int ATTEMPTS = 2;
    private static final Set<String> SECTIONS = Set.of("complaint", "history", "examination", "assessment", "plan");

    private final ClinicalAi ai;
    private final AiRequestRepository requests;
    private final TranscriptService transcripts;
    private final ConsentService consent;
    private final AccessPolicy accessPolicy;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SummaryService(
            ClinicalAi ai,
            AiRequestRepository requests,
            TranscriptService transcripts,
            ConsentService consent,
            AccessPolicy accessPolicy,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.ai = ai;
        this.requests = requests;
        this.transcripts = transcripts;
        this.consent = consent;
        this.accessPolicy = accessPolicy;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public SummaryResult summarise(UUID visitId, VisitService.Actor actor) {
        if (actor.role() != StaffRole.DOCTOR) {
            throw new ForbiddenException("Summaries are for the doctor");
        }
        List<TranscriptSegment> segments = transaction.execute(status -> {
            accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.TRANSCRIPT, true);
            if (!consent.isAgreed(visitId, ConsentPurpose.RECORD_CONSULTATION)) {
                throw new BusinessRuleException(
                        "The patient has not agreed to recording, so there is nothing to summarise");
            }
            return transcripts.consultationLines(visitId);
        });
        if (segments == null || segments.isEmpty()) {
            throw new BusinessRuleException("There is no transcript to summarise yet");
        }
        String transcript = String.join(
                "\n", segments.stream().map(TranscriptSegment::getText).toList());

        Instant started = clock.instant();
        List<ClinicalAi.DraftClaim> drafts = null;
        String error = null;
        for (int attempt = 1; attempt <= ATTEMPTS && drafts == null; attempt++) {
            try {
                drafts = ai.summarise(transcript);
            } catch (RuntimeException e) {
                // Never log the exception message: it may carry transcript text.
                error = e.getClass().getSimpleName();
                log.warn("Summary attempt {} failed: {}", attempt, error);
            }
        }
        int durationMs = (int) Math.min(
                Integer.MAX_VALUE, Duration.between(started, clock.instant()).toMillis());

        if (drafts == null) {
            AiRequest failed = requests.save(new AiRequest(
                    visitId,
                    actor.staffId(),
                    ai.model(),
                    ai.promptVersion(),
                    AiRequest.UNAVAILABLE,
                    null,
                    null,
                    "Failed twice: " + error,
                    durationMs));
            return new SummaryResult(failed.getId(), false, List.of());
        }

        ClaimChecker checker = new ClaimChecker(segments.stream()
                .map(s -> new ClaimChecker.Line(s.getText(), s.getConfidence(), s.getStartMs(), s.getSessionId()))
                .toList());
        List<CheckedClaim> checked = drafts.stream()
                .map(d -> new ClinicalAi.DraftClaim(
                        section(d.section()), d.text().strip(), d.quote().strip()))
                .map(d -> new CheckedClaim(d, checker.check(d.text(), d.quote())))
                .toList();
        AiRequest saved = requests.save(new AiRequest(
                visitId,
                actor.staffId(),
                ai.model(),
                ai.promptVersion(),
                AiRequest.OK,
                JSON.writeValueAsString(
                        checked.stream().map(CheckedClaim::claim).toList()),
                JSON.writeValueAsString(
                        checked.stream().map(CheckedClaim::verdict).toList()),
                null,
                durationMs));
        return new SummaryResult(saved.getId(), true, checked);
    }

    private static String section(String section) {
        String s = section == null ? "" : section.strip().toLowerCase(Locale.ROOT);
        return SECTIONS.contains(s) ? s : "history";
    }
}
