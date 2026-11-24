package ke.sauti.hms.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/** One opening of clinical data. Immutable, created only by {@link AccessLogService}. */
@Entity
@Immutable
@Table(name = "access_log")
public class AccessLogEntry {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Column(name = "visit_id", updatable = false)
    private UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AccessKind what;

    protected AccessLogEntry() {
        // for JPA
    }

    AccessLogEntry(Instant occurredAt, UUID actorId, UUID patientId, UUID visitId, AccessKind what) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.patientId = patientId;
        this.visitId = visitId;
        this.what = what;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public AccessKind getWhat() {
        return what;
    }
}
