package ke.sauti.hms.visit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ke.sauti.hms.audit.AccessLogService;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one decision on who may open clinical data. Every service that reads or writes it
 * calls this first.
 *
 * <pre>
 * role not allowed this kind of data at all          -> NO
 * open care assignment on the visit                  -> YES
 * assignment ended less than 24 hours ago             -> YES (finishing notes)
 * past signed note or prescription, and the person
 *   is a doctor on a current visit of the same patient -> YES
 * otherwise                                           -> NO, audited as ACCESS_DENIED
 * </pre>
 *
 * Every allowed read writes one access-log row.
 */
@Service
public class AccessPolicy {

    static final Duration GRACE = Duration.ofHours(24);

    /** Which roles may see which kinds of data at all. Admins see no clinical content. */
    static final Map<StaffRole, Set<ClinicalData>> ROLE_TABLE = Map.of(
            StaffRole.DOCTOR, EnumSet.allOf(ClinicalData.class),
            StaffRole.NURSE,
                    EnumSet.of(
                            ClinicalData.REASON_FOR_VISIT,
                            ClinicalData.ALLERGIES,
                            ClinicalData.VITALS,
                            ClinicalData.NOTE,
                            ClinicalData.PRESCRIPTION),
            StaffRole.RECEPTIONIST, EnumSet.of(ClinicalData.REASON_FOR_VISIT),
            StaffRole.CASHIER, EnumSet.of(ClinicalData.PRESCRIPTION),
            StaffRole.ADMIN, EnumSet.noneOf(ClinicalData.class));

    private final VisitRepository visits;
    private final CareAssignmentRepository assignments;
    private final AccessLogService accessLog;
    private final AuditService audit;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;

    public AccessPolicy(
            VisitRepository visits,
            CareAssignmentRepository assignments,
            AccessLogService accessLog,
            AuditService audit,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.visits = visits;
        this.assignments = assignments;
        this.accessLog = accessLog;
        this.audit = audit;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Allows or refuses (403) opening one visit's data, and logs an allowed read.
     *
     * @param read true when the data is being shown, so the access log gets a row
     */
    @Transactional
    public void requireVisitAccess(VisitService.Actor actor, UUID visitId, ClinicalData kind, boolean read) {
        Visit visit = visits.findById(visitId).orElseThrow(() -> new NotFoundException("No such visit"));
        boolean allowed = roleMay(actor.role(), kind)
                && (recentlyAssigned(assignments.findByVisitIdAndStaffId(visitId, actor.staffId()))
                        || pastRecordForCurrentDoctor(actor, visit.getPatientId(), kind));
        decide(allowed, actor, visit.getPatientId(), visitId, kind, read);
    }

    /**
     * Allows or refuses (403) opening patient-wide data such as allergies: the person must
     * be looking after, or have looked after in the last 24 hours, a visit of this patient.
     */
    @Transactional
    public void requirePatientAccess(VisitService.Actor actor, UUID patientId, ClinicalData kind, boolean read) {
        boolean allowed = roleMay(actor.role(), kind)
                && recentlyAssigned(assignments.findForPatientAndStaff(patientId, actor.staffId()));
        decide(allowed, actor, patientId, null, kind, read);
    }

    static boolean roleMay(StaffRole role, ClinicalData kind) {
        return ROLE_TABLE.getOrDefault(role, Set.of()).contains(kind);
    }

    private boolean recentlyAssigned(List<CareAssignment> list) {
        Instant cutoff = clock.instant().minus(GRACE);
        return list.stream().anyMatch(a -> a.isOpen() || a.getEndedAt().isAfter(cutoff));
    }

    private boolean pastRecordForCurrentDoctor(VisitService.Actor actor, UUID patientId, ClinicalData kind) {
        return kind.isPastRecord()
                && actor.role() == StaffRole.DOCTOR
                && !assignments
                        .findOpenOnCurrentVisits(patientId, actor.staffId())
                        .isEmpty();
    }

    private void decide(
            boolean allowed, VisitService.Actor actor, UUID patientId, UUID visitId, ClinicalData kind, boolean read) {
        if (!allowed) {
            // The caller's transaction rolls back with the 403, so the denial is kept in its own.
            ownTransaction.executeWithoutResult(status -> audit.record(
                    AuditAction.ACCESS_DENIED,
                    "ClinicalData",
                    visitId,
                    patientId,
                    visitId,
                    Map.of("kind", kind.name(), "role", actor.role().name())));
            throw new ForbiddenException("You are not looking after this patient");
        }
        if (read) {
            accessLog.record(patientId, visitId, kind.accessKind());
        }
    }
}
