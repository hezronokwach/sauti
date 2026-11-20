package ke.sauti.hms.visit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.common.time.FacilityTime;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Opening visits and moving them through the queues. {@link #changeStatus} is the only
 * place a visit's status changes; every change is checked against {@link VisitWorkflow},
 * made under a row lock on the visit, and written to the history and the audit log.
 */
@Service
@Transactional
public class VisitService {

    private static final String VISIT = Visit.class.getSimpleName();

    private final VisitRepository visits;
    private final QueueNumbers queueNumbers;
    private final VisitStatusChangeRepository history;
    private final CareAssignmentRepository assignments;
    private final PatientService patients;
    private final AuditService audit;
    private final FacilityTime facilityTime;
    private final Clock clock;
    private final AccessPolicy accessPolicy;

    public VisitService(
            VisitRepository visits,
            QueueNumbers queueNumbers,
            VisitStatusChangeRepository history,
            CareAssignmentRepository assignments,
            PatientService patients,
            AuditService audit,
            FacilityTime facilityTime,
            Clock clock,
            AccessPolicy accessPolicy) {
        this.visits = visits;
        this.queueNumbers = queueNumbers;
        this.history = history;
        this.assignments = assignments;
        this.patients = patients;
        this.audit = audit;
        this.facilityTime = facilityTime;
        this.clock = clock;
        this.accessPolicy = accessPolicy;
    }

    /** Who is acting: the logged-in staff member and their role. */
    public record Actor(UUID staffId, StaffRole role) {}

    /**
     * Opens a visit at reception with the day's next queue number. A patient can have only
     * one visit in progress.
     */
    public Visit open(UUID patientId, String reasonForVisit, Actor actor) {
        if (actor.role() != StaffRole.RECEPTIONIST) {
            throw new ForbiddenException("Only reception opens visits");
        }
        patients.get(patientId);
        if (visits.findOpenByPatientId(patientId).isPresent()) {
            throw new ConflictException("This patient already has a visit in progress");
        }
        Instant now = clock.instant();
        int queueNumber = queueNumbers.next(facilityTime.today());
        String reason = StringUtils.hasText(reasonForVisit) ? reasonForVisit.trim() : null;
        Visit visit = visits.saveAndFlush(new Visit(patientId, facilityTime.today(), queueNumber, reason, now));

        history.save(new VisitStatusChange(visit.getId(), null, VisitStatus.AT_RECEPTION, actor.staffId(), now, null));
        assignments.save(new CareAssignment(visit.getId(), actor.staffId(), actor.role(), now));
        audit.record(
                AuditAction.VISIT_OPENED, VISIT, visit.getId(), patientId, visit.getId(), Map.of("queue", queueNumber));
        return visit;
    }

    /**
     * Takes the next patient from the actor's queue: a nurse from WAITING_TRIAGE, a doctor
     * from WAITING_DOCTOR. Two people taking the same patient at once go one after the
     * other on the row lock; the second gets 409 "already taken".
     */
    public Visit take(UUID visitId, Actor actor) {
        VisitWorkflow.Move move = VisitWorkflow.takeFor(actor.role())
                .orElseThrow(() -> new ForbiddenException("Your role does not take patients from a queue"));
        Visit visit = lock(visitId);
        if (visit.getStatus() != move.from()) {
            throw new ConflictException("This patient has already been taken or is no longer waiting");
        }
        return apply(visit, move.to(), actor, null);
    }

    /**
     * Moves a visit to another status: the one entry point for every change. Refused with
     * 409 for a move the workflow does not have, 403 for the wrong role, and 403 when the
     * patient is held by someone else (in triage or with a doctor).
     */
    public Visit changeStatus(UUID visitId, VisitStatus to, String reason, Actor actor) {
        return apply(lock(visitId), to, actor, reason);
    }

    /** What brought the patient in, for someone looking after the visit; access-logged. */
    public String reasonForVisit(UUID visitId, Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.REASON_FOR_VISIT, true);
        return get(visitId).getReasonForVisit();
    }

    @Transactional(readOnly = true)
    public Visit get(UUID visitId) {
        return visits.findById(visitId).orElseThrow(() -> new NotFoundException("No such visit"));
    }

    /** Today's visits in one status, in queue order. */
    @Transactional(readOnly = true)
    public List<Visit> queue(VisitStatus status) {
        return visits.findByVisitDateAndStatusOrderByQueueNumberAsc(facilityTime.today(), status);
    }

    @Transactional(readOnly = true)
    public List<VisitStatusChange> history(UUID visitId) {
        get(visitId);
        return history.findByVisitIdOrderByChangedAtAsc(visitId);
    }

    @Transactional(readOnly = true)
    public List<CareAssignment> assignments(UUID visitId) {
        return assignments.findByVisitIdOrderByStartedAtAsc(visitId);
    }

    private Visit lock(UUID visitId) {
        return visits.findByIdForUpdate(visitId).orElseThrow(() -> new NotFoundException("No such visit"));
    }

    private Visit apply(Visit visit, VisitStatus to, Actor actor, String reason) {
        VisitStatus from = visit.getStatus();
        VisitWorkflow.check(from, to, actor.role());
        List<CareAssignment> open = assignments.findByVisitIdAndEndedAtIsNull(visit.getId());
        if (VisitWorkflow.HELD.contains(from)
                && open.stream().noneMatch(a -> a.getStaffId().equals(actor.staffId()))) {
            throw new ForbiddenException("Only the person looking after this patient can move them on");
        }
        String cancelReason = StringUtils.hasText(reason) ? reason.trim() : null;
        if (to == VisitStatus.CANCELLED && cancelReason == null) {
            throw new BusinessRuleException("Give a reason for cancelling the visit");
        }

        Instant now = clock.instant();
        open.forEach(assignment -> assignment.end(now));
        if (VisitWorkflow.isTake(from, to)) {
            assignments.save(new CareAssignment(visit.getId(), actor.staffId(), actor.role(), now));
        }
        visit.moveTo(to, now, actor.staffId());
        history.save(new VisitStatusChange(visit.getId(), from, to, actor.staffId(), now, cancelReason));
        audit.record(
                AuditAction.VISIT_STATUS_CHANGED,
                VISIT,
                visit.getId(),
                visit.getPatientId(),
                visit.getId(),
                Map.of("from", from.name(), "to", to.name()));
        return visit;
    }
}
