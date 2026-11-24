package ke.sauti.hms.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** A visit's bill. The total is fixed when it is created. */
@Entity
@Table(name = "invoice")
public class Invoice extends BaseEntity {

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(name = "total_kes", nullable = false, updatable = false)
    private BigDecimal totalKes;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    protected Invoice() {
        // for JPA
    }

    Invoice(UUID visitId, UUID patientId, BigDecimal totalKes, UUID createdBy) {
        this.visitId = visitId;
        this.patientId = patientId;
        this.totalKes = totalKes;
        this.createdBy = createdBy;
        this.status = InvoiceStatus.UNPAID;
    }

    void settle(InvoiceStatus settled) {
        this.status = settled;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalKes() {
        return totalKes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
