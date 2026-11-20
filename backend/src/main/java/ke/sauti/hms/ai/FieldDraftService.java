package ke.sauti.hms.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voice drafts waiting for reception or the triage nurse. Confirming one only records the
 * decision: the value itself goes into the record through the normal form and its checks.
 */
@Service
@Transactional
public class FieldDraftService {

    private final FieldDraftRepository drafts;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;

    public FieldDraftService(FieldDraftRepository drafts, AccessPolicy accessPolicy, AuditService audit) {
        this.drafts = drafts;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
    }

    public List<FieldDraft> pending(UUID visitId, VisitService.Actor actor) {
        requireDesk(actor);
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.REASON_FOR_VISIT, false);
        return drafts.findByVisitIdAndStatusOrderByCreatedAtAsc(visitId, FieldDraft.Status.PENDING);
    }

    public FieldDraft decide(UUID visitId, UUID draftId, boolean confirmed, VisitService.Actor actor) {
        requireDesk(actor);
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.REASON_FOR_VISIT, false);
        FieldDraft draft = drafts.findById(draftId)
                .filter(d -> d.getVisitId().equals(visitId))
                .orElseThrow(() -> new NotFoundException("No such draft for this visit"));
        if (draft.getStatus() != FieldDraft.Status.PENDING) {
            throw new ConflictException("This draft was already decided");
        }
        draft.decide(confirmed ? FieldDraft.Status.CONFIRMED : FieldDraft.Status.DISMISSED, actor.staffId());
        audit.record(
                AuditAction.FIELD_DRAFT_DECIDED,
                FieldDraft.class.getSimpleName(),
                draftId,
                null,
                visitId,
                Map.of(
                        "field",
                        draft.getField().name(),
                        "decision",
                        draft.getStatus().name()));
        return draft;
    }

    private static void requireDesk(VisitService.Actor actor) {
        if (actor.role() != StaffRole.RECEPTIONIST && actor.role() != StaffRole.NURSE) {
            throw new ForbiddenException("Voice drafts are for reception and triage");
        }
    }
}
