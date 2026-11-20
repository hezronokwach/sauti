package ke.sauti.hms.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** The latest eligibility answer for one patient. */
@Entity
@Table(name = "patient_eligibility")
public class PatientEligibility extends BaseEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EligibilityStatus status;

    @Column(name = "checked_at")
    private Instant checkedAt;

    protected PatientEligibility() {
        // for JPA
    }

    PatientEligibility(UUID patientId) {
        this.patientId = patientId;
        this.status = EligibilityStatus.PENDING;
    }

    void answer(EligibilityStatus status, Instant at) {
        this.status = status;
        this.checkedAt = at;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public EligibilityStatus getStatus() {
        return status;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
