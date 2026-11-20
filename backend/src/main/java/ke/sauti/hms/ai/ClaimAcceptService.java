package ke.sauti.hms.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.note.NoteSections;
import ke.sauti.hms.note.NoteService;
import ke.sauti.hms.note.NoteWithAddenda;
import ke.sauti.hms.visit.VisitService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bringing summary claims into the note. The claims and verdicts come from the server's own
 * {@code ai_request} row, never from the browser, so a claim that failed the checks cannot
 * be accepted: the whole request is refused (422) and nothing is added.
 */
@Service
@Transactional
public class ClaimAcceptService {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final AiRequestRepository requests;
    private final NoteService notes;
    private final AuditService audit;

    public ClaimAcceptService(AiRequestRepository requests, NoteService notes, AuditService audit) {
        this.requests = requests;
        this.notes = notes;
        this.audit = audit;
    }

    public NoteWithAddenda accept(
            UUID visitId,
            UUID requestId,
            List<Integer> positions,
            @Nullable Long noteVersion,
            VisitService.Actor actor) {
        AiRequest request = requests.findById(requestId)
                .filter(r -> r.getVisitId().equals(visitId))
                .orElseThrow(() -> new NotFoundException("No such summary for this visit"));
        if (!AiRequest.OK.equals(request.getStatus())) {
            throw new ConflictException("This summary was unavailable; there is nothing to accept");
        }
        ClinicalAi.DraftClaim[] claims = JSON.readValue(request.getClaims(), ClinicalAi.DraftClaim[].class);
        ClaimChecker.Verdict[] verdicts = JSON.readValue(request.getChecks(), ClaimChecker.Verdict[].class);

        List<Map.Entry<String, String>> lines = new ArrayList<>();
        for (int position : new LinkedHashSet<>(positions)) {
            if (position >= claims.length) {
                throw new BusinessRuleException("The summary has no claim " + position);
            }
            if (!verdicts[position].supported()) {
                throw new BusinessRuleException(
                        "A claim that failed the checks cannot be accepted; write it yourself if it is right");
            }
            String section =
                    NoteSections.NAMES.contains(claims[position].section()) ? claims[position].section() : "history";
            lines.add(Map.entry(section, claims[position].text()));
        }

        // The access check, the version rule and the signed-note rule are the note's own.
        NoteWithAddenda note = notes.addAiLines(visitId, noteVersion, lines, actor);
        audit.record(
                AuditAction.AI_CLAIMS_ACCEPTED,
                AiRequest.class.getSimpleName(),
                requestId,
                note.note().getPatientId(),
                visitId,
                Map.of("count", lines.size()));
        return note;
    }
}
