package ke.sauti.hms.queue.dto;

import jakarta.validation.constraints.NotNull;
import ke.sauti.hms.queue.TicketLane;

/**
 * Issuing an arrival ticket.
 *
 * @param lane which lane the patient joins
 */
public record IssueTicketRequest(@NotNull TicketLane lane) {}
