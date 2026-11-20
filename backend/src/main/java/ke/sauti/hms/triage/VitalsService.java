package ke.sauti.hms.triage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.triage.dto.RecordVitalsRequest;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vital signs at triage. Recording them is the nurse's last step: the visit moves on to the
 * doctor's queue in the same transaction, through the workflow, which also checks that this
 * nurse is the one holding the patient.
 */
@Service
@Transactional
public class VitalsService {

    private static final String VITALS = Vitals.class.getSimpleName();

    private final VitalsRepository repository;
    private final VisitService visits;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;
    private final Clock clock;

    public VitalsService(
            VitalsRepository repository,
            VisitService visits,
            AccessPolicy accessPolicy,
            AuditService audit,
            Clock clock) {
        this.repository = repository;
        this.visits = visits;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    public Vitals record(UUID visitId, RecordVitalsRequest request, VisitService.Actor actor) {
        if (actor.role() != StaffRole.NURSE) {
            throw new ForbiddenException("Vitals are taken by the triage nurse");
        }
        if (request.systolicMmhg() <= request.diastolicMmhg()) {
            throw new BusinessRuleException("The systolic pressure must be higher than the diastolic");
        }
        Visit visit = visits.get(visitId);
        if (visit.getStatus() != VisitStatus.IN_TRIAGE) {
            throw new ConflictException("Vitals are taken while the patient is in triage");
        }
        // Locks the visit and refuses a nurse who is not holding this patient.
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor);

        Vitals vitals = repository.save(new Vitals(
                visitId,
                actor.staffId(),
                clock.instant(),
                new Vitals.Readings(
                        request.temperatureC(),
                        request.pulseBpm(),
                        request.respiratoryRate(),
                        request.systolicMmhg(),
                        request.diastolicMmhg(),
                        request.spo2Percent(),
                        scale(request.weightKg()),
                        scale(request.heightCm()))));
        audit.record(AuditAction.VITALS_RECORDED, VITALS, vitals.getId(), visit.getPatientId(), visitId, Map.of());
        return vitals;
    }

    /** A visit's readings, for the people looking after it; the read is access-logged. */
    public List<Vitals> list(UUID visitId, VisitService.Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.VITALS, true);
        return repository.findByVisitIdOrderByRecordedAtAsc(visitId);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }
}
