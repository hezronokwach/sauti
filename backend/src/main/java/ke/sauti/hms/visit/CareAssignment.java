package ke.sauti.hms.visit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.staff.StaffRole;
import org.hibernate.annotations.UuidGenerator;

/**
 * A staff member looking after a visit for one stage. Created only by the workflow
 * (opening a visit, taking a patient) and ended when the visit moves on. The access
 * policy (issue 18) reads these to decide who may open clinical details.
 */
@Entity
@Table(name = "care_assignment")
public class CareAssignment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StaffRole role;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CareAssignment() {
        // for JPA
    }

    CareAssignment(UUID visitId, UUID staffId, StaffRole role, Instant startedAt) {
        this.visitId = visitId;
        this.staffId = staffId;
        this.role = role;
        this.startedAt = startedAt;
    }

    void end(Instant at) {
        if (endedAt == null) {
            endedAt = at;
        }
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public StaffRole getRole() {
        return role;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
