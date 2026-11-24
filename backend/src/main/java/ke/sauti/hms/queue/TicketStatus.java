package ke.sauti.hms.queue;

/** WAITING until called, CALLED once reception or clinical staff call it, SERVED once a visit opens for it. */
public enum TicketStatus {
    WAITING,
    CALLED,
    SERVED,
    CANCELLED
}
