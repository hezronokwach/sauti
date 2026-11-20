package ke.sauti.hms.visit;

import static ke.sauti.hms.visit.VisitStatus.AT_RECEPTION;
import static ke.sauti.hms.visit.VisitStatus.CANCELLED;
import static ke.sauti.hms.visit.VisitStatus.DONE;
import static ke.sauti.hms.visit.VisitStatus.IN_TRIAGE;
import static ke.sauti.hms.visit.VisitStatus.WAITING_DOCTOR;
import static ke.sauti.hms.visit.VisitStatus.WAITING_PAYMENT;
import static ke.sauti.hms.visit.VisitStatus.WAITING_TRIAGE;
import static ke.sauti.hms.visit.VisitStatus.WITH_DOCTOR;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.staff.StaffRole;

/**
 * The one map of allowed status moves and the roles that may make each. Anything not in
 * the map is refused with 409; a move in the map made by the wrong role is refused with
 * 403.
 *
 * <pre>
 * AT_RECEPTION -> WAITING_TRIAGE -> IN_TRIAGE -> WAITING_DOCTOR -> WITH_DOCTOR
 *              -> WAITING_PAYMENT -> DONE        (CANCELLED from the waiting steps)
 * </pre>
 */
public final class VisitWorkflow {

    /** A move from one status to another. */
    record Move(VisitStatus from, VisitStatus to) {}

    static final Map<Move, Set<StaffRole>> ALLOWED = Map.of(
            new Move(AT_RECEPTION, WAITING_TRIAGE), Set.of(StaffRole.RECEPTIONIST),
            new Move(WAITING_TRIAGE, IN_TRIAGE), Set.of(StaffRole.NURSE),
            new Move(IN_TRIAGE, WAITING_DOCTOR), Set.of(StaffRole.NURSE),
            new Move(WAITING_DOCTOR, WITH_DOCTOR), Set.of(StaffRole.DOCTOR),
            new Move(WITH_DOCTOR, WAITING_PAYMENT), Set.of(StaffRole.DOCTOR),
            new Move(WAITING_PAYMENT, DONE), Set.of(StaffRole.CASHIER),
            new Move(AT_RECEPTION, CANCELLED), Set.of(StaffRole.RECEPTIONIST),
            new Move(WAITING_TRIAGE, CANCELLED), Set.of(StaffRole.RECEPTIONIST),
            new Move(WAITING_DOCTOR, CANCELLED), Set.of(StaffRole.RECEPTIONIST));

    /** The statuses where one person has the patient; only they may move the visit on. */
    static final Set<VisitStatus> HELD = Set.of(IN_TRIAGE, WITH_DOCTOR);

    /** Each clinical role's queue, and the status taking a patient from it moves to. */
    static final Map<StaffRole, Move> TAKE = Map.of(
            StaffRole.NURSE, new Move(WAITING_TRIAGE, IN_TRIAGE),
            StaffRole.DOCTOR, new Move(WAITING_DOCTOR, WITH_DOCTOR));

    private VisitWorkflow() {}

    /** Refuses a move that does not exist (409) or that this role may not make (403). */
    static void check(VisitStatus from, VisitStatus to, StaffRole role) {
        Set<StaffRole> roles = ALLOWED.get(new Move(from, to));
        if (roles == null) {
            throw new ConflictException("A visit cannot move from " + from + " to " + to);
        }
        if (!roles.contains(role)) {
            throw new ForbiddenException("Your role cannot move a visit from " + from + " to " + to);
        }
    }

    /** The move taking a patient makes for this role, if the role takes patients at all. */
    static Optional<Move> takeFor(StaffRole role) {
        return Optional.ofNullable(TAKE.get(role));
    }

    /** Whether this move hands the patient to the person making it. */
    static boolean isTake(VisitStatus from, VisitStatus to) {
        return TAKE.containsValue(new Move(from, to));
    }
}
