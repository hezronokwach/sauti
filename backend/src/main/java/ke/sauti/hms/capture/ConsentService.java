package ke.sauti.hms.capture;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recording consent. Decisions are recorded by the people looking after the visit, with
 * the wording version shown; the latest decision for a visit and purpose counts.
 * {@link #requireAgreed} is the guard every recording goes through.
 */
@Service
@Transactional
public class ConsentService {

    private static final String CONSENT = Consent.class.getSimpleName();

    private final ConsentRepository repository;
    private final ConsentWording wording;
    private final VisitService visits;
    private final PatientService patients;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ConsentService(
            ConsentRepository repository,
            ConsentWording wording,
            VisitService visits,
            PatientService patients,
            AccessPolicy accessPolicy,
            AuditService audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.wording = wording;
        this.visits = visits;
        this.patients = patients;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Records a decision. The wording version is the patient's preferred language at the
     * current version, since that is what they were shown. Withdrawing needs an earlier
     * agreement, and stops any recording for this visit and purpose.
     */
    public Consent record(
            UUID visitId,
            ConsentPurpose purpose,
            ConsentDecision decision,
            ConsentGivenBy givenBy,
            VisitService.Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.CONSENT, false);
        Visit visit = visits.get(visitId);
        if (visit.getStatus().isFinal()) {
            throw new BusinessRuleException("This visit is closed");
        }
        if (decision == ConsentDecision.WITHDRAWN && !isAgreed(visitId, purpose)) {
            throw new BusinessRuleException("There is no agreement to withdraw");
        }
        PreferredLanguage language = patients.get(visit.getPatientId()).getPreferredLanguage();
        Consent consent = repository.save(new Consent(
                visitId, purpose, decision, givenBy, wording.versionLabel(language), actor.staffId(), clock.instant()));
        audit.record(
                AuditAction.CONSENT_RECORDED,
                CONSENT,
                consent.getId(),
                visit.getPatientId(),
                visitId,
                Map.of("purpose", purpose.name(), "decision", decision.name(), "givenBy", givenBy.name()));
        if (decision == ConsentDecision.WITHDRAWN) {
            events.publishEvent(new ConsentWithdrawnEvent(visitId, purpose));
        }
        return consent;
    }

    /** Every decision for the visit, oldest first, for the people looking after it. */
    @Transactional
    public List<Consent> history(UUID visitId, VisitService.Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.CONSENT, true);
        return repository.findByVisitIdOrderByRecordedAtAsc(visitId);
    }

    /**
     * The guard for recording: refuses (422) unless the latest decision for this visit and
     * purpose is AGREED. Capture calls this when a recording starts and for every piece.
     */
    @Transactional(readOnly = true)
    public void requireAgreed(UUID visitId, ConsentPurpose purpose) {
        if (!isAgreed(visitId, purpose)) {
            throw new BusinessRuleException("Recording needs the patient's consent first");
        }
    }

    /** Whether the latest decision for this visit and purpose is AGREED. */
    @Transactional(readOnly = true)
    public boolean isAgreed(UUID visitId, ConsentPurpose purpose) {
        return repository
                .findFirstByVisitIdAndPurposeOrderByRecordedAtDescIdDesc(visitId, purpose)
                .map(consent -> consent.getDecision() == ConsentDecision.AGREED)
                .orElse(false);
    }
}
