package ke.sauti.hms.capture;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * Starting and stopping recordings. A recording starts only for the right role for its
 * purpose, a member of the visit's care team, an open visit, an AGREED consent, and no
 * other recording running for the same visit and purpose. A consent withdrawal stops it.
 */
@Service
@Transactional
public class CaptureService {

    private static final String SESSION = CaptureSession.class.getSimpleName();

    /** Reception dictates the reason for visit; only doctors record consultations. Nurses type. */
    static final Map<ConsentPurpose, Set<StaffRole>> RECORDERS = Map.of(
            ConsentPurpose.RECORD_RECEPTION, Set.of(StaffRole.RECEPTIONIST),
            ConsentPurpose.RECORD_CONSULTATION, Set.of(StaffRole.DOCTOR));

    private final CaptureSessionRepository sessions;
    private final ConsentService consents;
    private final VisitService visits;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;
    private final Clock clock;

    public CaptureService(
            CaptureSessionRepository sessions,
            ConsentService consents,
            VisitService visits,
            AccessPolicy accessPolicy,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.consents = consents;
        this.visits = visits;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    /** Whether any recording is still running for this visit. */
    public boolean isRecording(UUID visitId) {
        return RECORDERS.keySet().stream()
                .anyMatch(
                        purpose -> !sessions.findByVisitIdAndPurposeAndStatus(visitId, purpose, CaptureStatus.RECORDING)
                                .isEmpty());
    }

    public CaptureSession start(UUID visitId, ConsentPurpose purpose, String languageHint, VisitService.Actor actor) {
        if (!RECORDERS.get(purpose).contains(actor.role())) {
            throw new ForbiddenException("Your role does not make this kind of recording");
        }
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.CONSENT, false);
        if (visits.get(visitId).getStatus().isFinal()) {
            throw new BusinessRuleException("This visit is closed");
        }
        consents.requireAgreed(visitId, purpose);
        if (!sessions.findByVisitIdAndPurposeAndStatus(visitId, purpose, CaptureStatus.RECORDING)
                .isEmpty()) {
            throw new ConflictException("A recording is already running for this visit");
        }
        String hint = StringUtils.hasText(languageHint) ? languageHint.trim() : null;
        CaptureSession session =
                sessions.saveAndFlush(new CaptureSession(visitId, purpose, actor.staffId(), hint, clock.instant()));
        audit.record(
                AuditAction.CAPTURE_STARTED,
                SESSION,
                session.getId(),
                null,
                visitId,
                Map.of("purpose", purpose.name()));
        return session;
    }

    /** Stops a recording; stopping one that already stopped changes nothing. */
    public CaptureSession stop(UUID sessionId, Integer expectedPieces, VisitService.Actor actor) {
        CaptureSession session = load(sessionId);
        accessPolicy.requireVisitAccess(actor, session.getVisitId(), ClinicalData.CONSENT, false);
        if (session.isRecording()) {
            session.stop(expectedPieces, clock.instant());
            audit.record(
                    AuditAction.CAPTURE_STOPPED,
                    SESSION,
                    session.getId(),
                    null,
                    session.getVisitId(),
                    Map.of("reason", "STOPPED_BY_USER"));
        }
        return session;
    }

    @Transactional(readOnly = true)
    public CaptureSession get(UUID sessionId, VisitService.Actor actor) {
        CaptureSession session = load(sessionId);
        accessPolicy.requireVisitAccess(actor, session.getVisitId(), ClinicalData.CONSENT, false);
        return session;
    }

    /** A withdrawal stops every recording for that visit and purpose, once it is saved. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConsentWithdrawn(ConsentWithdrawnEvent event) {
        Instant now = clock.instant();
        sessions.findByVisitIdAndPurposeAndStatus(event.visitId(), event.purpose(), CaptureStatus.RECORDING)
                .forEach(session -> {
                    session.stop(null, now);
                    audit.record(
                            AuditAction.CAPTURE_STOPPED,
                            SESSION,
                            session.getId(),
                            null,
                            session.getVisitId(),
                            Map.of("reason", "CONSENT_WITHDRAWN"));
                });
    }

    private CaptureSession load(UUID sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("No such recording"));
    }
}
