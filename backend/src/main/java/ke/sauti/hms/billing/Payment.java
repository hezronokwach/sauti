package ke.sauti.hms.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** One attempt to settle a bill. The idempotency key makes a double click a single payment. */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentMethod method;

    @Column(name = "amount_kes", nullable = false, updatable = false)
    private BigDecimal amountKes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "mpesa_receipt")
    private String mpesaReceipt;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    protected Payment() {
        // for JPA
    }

    Payment(
            UUID invoiceId,
            PaymentMethod method,
            BigDecimal amountKes,
            PaymentStatus status,
            String idempotencyKey,
            UUID recordedBy) {
        this.invoiceId = invoiceId;
        this.method = method;
        this.amountKes = amountKes;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.recordedBy = recordedBy;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getAmountKes() {
        return amountKes;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCheckoutRequestId() {
        return checkoutRequestId;
    }

    public String getMpesaReceipt() {
        return mpesaReceipt;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }
}
