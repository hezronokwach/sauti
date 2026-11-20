package ke.sauti.hms.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** One arrival ticket. No patient details: the code is all the waiting area ever sees. */
@Entity
@Table(name = "queue_ticket")
public class QueueTicket extends BaseEntity {

    @Column(name = "ticket_date", nullable = false, updatable = false)
    private LocalDate ticketDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketLane lane;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "arrived_at", nullable = false, updatable = false)
    private Instant arrivedAt;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "called_by")
    private UUID calledBy;

    @Column(name = "visit_id")
    private UUID visitId;

    protected QueueTicket() {
        // for JPA
    }

    QueueTicket(LocalDate ticketDate, TicketLane lane, int number, Instant arrivedAt, UUID issuedBy) {
        this.ticketDate = ticketDate;
        this.lane = lane;
        this.number = number;
        this.code = lane.code(number);
        this.status = TicketStatus.WAITING;
        this.arrivedAt = arrivedAt;
        this.issuedBy = issuedBy;
    }

    void call(UUID staffId, Instant at) {
        this.status = TicketStatus.CALLED;
        this.calledBy = staffId;
        this.calledAt = at;
    }

    void serve(UUID visitId) {
        this.status = TicketStatus.SERVED;
        this.visitId = visitId;
    }

    void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public LocalDate getTicketDate() {
        return ticketDate;
    }

    public TicketLane getLane() {
        return lane;
    }

    public int getNumber() {
        return number;
    }

    public String getCode() {
        return code;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public UUID getCalledBy() {
        return calledBy;
    }

    public UUID getVisitId() {
        return visitId;
    }
}
