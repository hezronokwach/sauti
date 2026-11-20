package ke.sauti.hms.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.common.time.FacilityTime;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arrival tickets and the order they are called in.
 *
 * <ul>
 *   <li>Reception issues a ticket in a lane; each lane numbers its tickets from 1 each day.
 *   <li>Reception calls the next ticket: a new patient waiting 15 minutes or more first,
 *       then pre-registered and returning patients, then new patients, each in arrival order.
 *   <li>Emergencies never wait in reception's queue: nurses and doctors see them at once and
 *       call them themselves.
 *   <li>Opening a visit for a ticket marks it served, and the visit's arrival time is the
 *       ticket's, so waiting times count from the door.
 * </ul>
 */
@Service
@Transactional
public class QueueTicketService {

    /** A new patient is never kept waiting longer than this when reception calls. */
    static final Duration NEW_PATIENT_LIMIT = Duration.ofMinutes(15);

    private final QueueTicketRepository tickets;
    private final TicketSql sql;
    private final FacilityTime facilityTime;
    private final Clock clock;

    public QueueTicketService(QueueTicketRepository tickets, TicketSql sql, FacilityTime facilityTime, Clock clock) {
        this.tickets = tickets;
        this.sql = sql;
        this.facilityTime = facilityTime;
        this.clock = clock;
    }

    public QueueTicket issue(TicketLane lane, Actor actor) {
        requireReception(actor);
        int number = sql.nextNumber(facilityTime.today(), lane);
        return tickets.save(new QueueTicket(facilityTime.today(), lane, number, clock.instant(), actor.staffId()));
    }

    /** Reception's next ticket, or empty when nobody is waiting. */
    public Optional<QueueTicket> callNext(Actor actor) {
        requireReception(actor);
        Instant now = clock.instant();
        return sql.lockNextToCall(facilityTime.today(), now.minus(NEW_PATIENT_LIMIT))
                .map(id -> {
                    QueueTicket ticket = tickets.findById(id).orElseThrow();
                    ticket.call(actor.staffId(), now);
                    return ticket;
                });
    }

    /** A nurse or doctor takes an emergency arrival. */
    public QueueTicket attendEmergency(UUID ticketId, Actor actor) {
        if (actor.role() != StaffRole.NURSE && actor.role() != StaffRole.DOCTOR) {
            throw new ForbiddenException("Emergencies are attended by clinical staff");
        }
        QueueTicket ticket = lock(ticketId);
        if (ticket.getLane() != TicketLane.EMERGENCY) {
            throw new ConflictException("This is not an emergency ticket");
        }
        if (ticket.getStatus() != TicketStatus.WAITING) {
            throw new ConflictException("Someone is already attending this emergency");
        }
        ticket.call(actor.staffId(), clock.instant());
        return ticket;
    }

    public QueueTicket cancel(UUID ticketId, Actor actor) {
        requireReception(actor);
        QueueTicket ticket = lock(ticketId);
        if (ticket.getStatus() == TicketStatus.SERVED) {
            throw new ConflictException("A visit is already open for this ticket");
        }
        ticket.cancel();
        return ticket;
    }

    /** Today's waiting and called tickets, for reception and clinical staff. */
    @Transactional(readOnly = true)
    public List<QueueTicket> today() {
        return tickets.findByTicketDateAndStatusInOrderByArrivedAtAscNumberAsc(
                facilityTime.today(), EnumSet.of(TicketStatus.WAITING, TicketStatus.CALLED));
    }

    /** The waiting-area display: the last called codes, and the codes still waiting. Emergencies are not shown. */
    @Transactional(readOnly = true)
    public QueueBoard board() {
        List<String> called = tickets
                .findTop5ByTicketDateAndLaneNotAndCalledAtNotNullOrderByCalledAtDesc(
                        facilityTime.today(), TicketLane.EMERGENCY)
                .stream()
                .map(QueueTicket::getCode)
                .toList();
        List<String> waiting = tickets
                .findByTicketDateAndStatusInOrderByArrivedAtAscNumberAsc(
                        facilityTime.today(), EnumSet.of(TicketStatus.WAITING))
                .stream()
                .filter(t -> t.getLane() != TicketLane.EMERGENCY)
                .map(QueueTicket::getCode)
                .toList();
        return new QueueBoard(called, waiting);
    }

    /**
     * For opening a visit: the ticket's arrival time, after checking the ticket is today's and
     * has been called (an emergency may also still be waiting). Joins the caller's transaction
     * and keeps the ticket locked until {@link #markServed}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant arrivalForVisit(UUID ticketId) {
        QueueTicket ticket = lock(ticketId);
        boolean ready = ticket.getStatus() == TicketStatus.CALLED
                || (ticket.getLane() == TicketLane.EMERGENCY && ticket.getStatus() == TicketStatus.WAITING);
        if (!ticket.getTicketDate().equals(facilityTime.today()) || !ready) {
            throw new ConflictException("This ticket is not waiting to be seen");
        }
        return ticket.getArrivedAt();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markServed(UUID ticketId, UUID visitId) {
        lock(ticketId).serve(visitId);
    }

    private QueueTicket lock(UUID ticketId) {
        return tickets.lockById(ticketId).orElseThrow(() -> new NotFoundException("No such ticket"));
    }

    private static void requireReception(Actor actor) {
        if (actor.role() != StaffRole.RECEPTIONIST) {
            throw new ForbiddenException("Tickets are handled by reception");
        }
    }

    /**
     * Who is acting.
     *
     * @param staffId the staff member
     * @param role their role
     */
    public record Actor(UUID staffId, StaffRole role) {}
}
