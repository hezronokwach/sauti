package ke.sauti.hms.queue.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.queue.QueueTicket;
import ke.sauti.hms.queue.TicketLane;
import ke.sauti.hms.queue.TicketStatus;
import org.jspecify.annotations.Nullable;

/**
 * One arrival ticket, for staff screens.
 *
 * @param id the ticket id
 * @param code what the patient and the display see, such as N-007
 * @param lane the arrival lane
 * @param status WAITING, CALLED, SERVED or CANCELLED
 * @param arrivedAt when the ticket was issued
 * @param calledAt when it was called, if called
 * @param visitId the visit opened for it, if any
 */
public record TicketResponse(
        UUID id,
        String code,
        TicketLane lane,
        TicketStatus status,
        Instant arrivedAt,
        @Nullable Instant calledAt,
        @Nullable UUID visitId) {

    public static TicketResponse of(QueueTicket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getCode(),
                ticket.getLane(),
                ticket.getStatus(),
                ticket.getArrivedAt(),
                ticket.getCalledAt(),
                ticket.getVisitId());
    }
}
