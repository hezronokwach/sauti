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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One row of the audit log. Immutable: Hibernate never writes changes back, and the
 * repository offers no update or delete. Created only by {@link AuditService}.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_role", length = 20, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Column(name = "visit_id", updatable = false)
    private UUID visitId;

    @Column(name = "request_id", length = 36, updatable = false)
    private String requestId;

    @Column(length = 45, updatable = false)
    private String ip;

    /** The detail as a JSON string, stored in a jsonb column. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private String detail;

    protected AuditEvent() {
        // for JPA
    }

    AuditEvent(
            Instant occurredAt,
            UUID actorId,
            String actorRole,
            AuditAction action,
            String entityType,
            UUID entityId,
            UUID patientId,
            UUID visitId,
            String requestId,
            String ip,
            String detail) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.patientId = patientId;
        this.visitId = visitId;
        this.requestId = requestId;
        this.ip = ip;
        this.detail = detail;
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

    public String getActorRole() {
        return actorRole;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getIp() {
        return ip;
    }

    public String getDetail() {
        return detail;
    }
}
