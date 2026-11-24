package ke.sauti.hms.visit;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;

/**
 * One visit by one patient. Its status changes only through
 * {@link VisitService#changeStatus}, which checks {@link VisitWorkflow} and writes the
 * history; nothing else sets it.
 */
@Entity
@Table(name = "visit")
public class Visit extends BaseEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "visit_date", nullable = false, updatable = false)
    private LocalDate visitDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "reason_for_visit")
    private String reasonForVisit;

    @Column(name = "queue_number", nullable = false, updatable = false)
    private int queueNumber;

    @Column(name = "assigned_doctor_id")
    private UUID assignedDoctorId;

    @Column(name = "reason_confirmed_by")
    private UUID reasonConfirmedBy;

    @Column(name = "reason_confirmed_at")
    private Instant reasonConfirmedAt;

    @Column(name = "registration_started_at")
    private Instant registrationStartedAt;

    @Column(name = "registration_completed_at")
    private Instant registrationCompletedAt;

    @Column(name = "arrived_at", nullable = false, updatable = false)
    private Instant arrivedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Visit() {
        // for JPA
    }

    Visit(UUID patientId, LocalDate visitDate, int queueNumber, String reasonForVisit, Instant arrivedAt) {
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.visitDate = Objects.requireNonNull(visitDate, "visitDate");
        this.queueNumber = queueNumber;
        this.reasonForVisit = reasonForVisit;
        this.arrivedAt = Objects.requireNonNull(arrivedAt, "arrivedAt");
        this.status = VisitStatus.AT_RECEPTION;
        this.statusChangedAt = arrivedAt;
    }

    /** Only {@link VisitService#changeStatus} calls this, after the workflow check. */
    void moveTo(VisitStatus next, Instant at, UUID staffId) {
        this.status = next;
        this.statusChangedAt = at;
        if (next == VisitStatus.WITH_DOCTOR) {
            this.assignedDoctorId = staffId;
            this.openedAt = at;
        }
        if (next.isFinal()) {
            this.closedAt = at;
        }
    }

    public UUID getPatientId() {
        return patientId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public VisitStatus getStatus() {
        return status;
    }

    /** Clinical text: read only through the access policy (issue 18). */
    String getReasonForVisit() {
        return reasonForVisit;
    }

    /** A changed reason needs the nurse's confirmation again. */
    void changeReason(String reason) {
        this.reasonForVisit = reason;
        this.reasonConfirmedBy = null;
        this.reasonConfirmedAt = null;
    }

    void startRegistration(Instant at) {
        this.registrationStartedAt = at;
    }

    /** The first time the visit goes on to triage ends registration; later moves do not change it. */
    void completeRegistration(Instant at) {
        if (registrationCompletedAt == null && registrationStartedAt != null) {
            this.registrationCompletedAt = at;
        }
    }

    public Instant getRegistrationStartedAt() {
        return registrationStartedAt;
    }

    public Instant getRegistrationCompletedAt() {
        return registrationCompletedAt;
    }

    void confirmReason(UUID nurseId, Instant at) {
        this.reasonConfirmedBy = nurseId;
        this.reasonConfirmedAt = at;
    }

    public boolean isReasonConfirmed() {
        return reasonConfirmedBy != null;
    }

    public int getQueueNumber() {
        return queueNumber;
    }

    public UUID getAssignedDoctorId() {
        return assignedDoctorId;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
