package ke.sauti.hms.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** A visit's prescription. Only a draft can change; signing arrives with the safety rules (#42). */
@Entity
@Table(name = "prescription")
public class Prescription extends BaseEntity {

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "prescriber_id", nullable = false, updatable = false)
    private UUID prescriberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrescriptionStatus status;

    @Column(name = "rules_version", nullable = false, updatable = false)
    private String rulesVersion;

    @Column(nullable = false)
    private int revision;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    protected Prescription() {
        // for JPA
    }

    Prescription(UUID visitId, UUID patientId, UUID prescriberId, String rulesVersion) {
        this.visitId = visitId;
        this.patientId = patientId;
        this.prescriberId = prescriberId;
        this.rulesVersion = rulesVersion;
        this.status = PrescriptionStatus.DRAFT;
    }

    /** Records that an item changed, so a screen holding an older copy is refused. */
    void itemsChanged() {
        revision++;
    }

    void sign(Instant at) {
        this.status = PrescriptionStatus.SIGNED;
        this.signedAt = at;
    }

    void cancel(UUID doctorId, Instant at) {
        this.status = PrescriptionStatus.CANCELLED;
        this.cancelledBy = doctorId;
        this.cancelledAt = at;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public boolean isDraft() {
        return status == PrescriptionStatus.DRAFT;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getPrescriberId() {
        return prescriberId;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public int getRevision() {
        return revision;
    }

    public Instant getSignedAt() {
        return signedAt;
    }
}
