package ke.sauti.hms.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;
import ke.sauti.hms.medication.safety.AlertLevel;
import ke.sauti.hms.medication.safety.AlertStatus;
import ke.sauti.hms.medication.safety.Finding;

/** A problem a safety rule found, and what the doctor decided. Text is encrypted at rest. */
@Entity
@Table(name = "safety_alert")
public class SafetyAlert extends BaseEntity {

    @Column(name = "prescription_id", nullable = false, updatable = false)
    private UUID prescriptionId;

    @Column(name = "item_id", updatable = false)
    private UUID itemId;

    @Column(nullable = false, updatable = false)
    private String rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AlertLevel level;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected SafetyAlert() {
        // for JPA
    }

    SafetyAlert(UUID prescriptionId, Finding finding) {
        this.prescriptionId = prescriptionId;
        this.itemId = finding.itemId();
        this.rule = finding.rule();
        this.level = finding.level();
        this.message = finding.message();
        this.status = AlertStatus.OPEN;
    }

    /** Whether this is the same problem as the finding, so an earlier decision still applies. */
    boolean matches(Finding finding) {
        return rule.equals(finding.rule())
                && java.util.Objects.equals(itemId, finding.itemId())
                && message.equals(finding.message());
    }

    boolean canBeOverridden() {
        return level == AlertLevel.BLOCKING && !"R1".equals(rule);
    }

    void override(String reason, UUID doctorId, Instant at) {
        this.status = AlertStatus.OVERRIDDEN;
        this.overrideReason = reason;
        this.decidedBy = doctorId;
        this.decidedAt = at;
    }

    void acknowledge(UUID doctorId, Instant at) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.decidedBy = doctorId;
        this.decidedAt = at;
    }

    public boolean isOpen() {
        return status == AlertStatus.OPEN;
    }

    public UUID getPrescriptionId() {
        return prescriptionId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getRule() {
        return rule;
    }

    public AlertLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
