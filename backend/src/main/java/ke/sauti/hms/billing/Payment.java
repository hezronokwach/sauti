package ke.sauti.hms.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;
import org.jspecify.annotations.Nullable;

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

    @Column(name = "prompted_at")
    private Instant promptedAt;

    @Column(name = "callback_at")
    private Instant callbackAt;

    @Column(name = "callback_amount_kes")
    private BigDecimal callbackAmountKes;

    @Column(name = "callback_receipt")
    private String callbackReceipt;

    @Column(name = "verification_queued_at")
    private Instant verificationQueuedAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "resolution_reason")
    private String resolutionReason;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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

    void prompted(Instant at) {
        this.promptedAt = at;
    }

    void attachCheckout(String checkoutId) {
        this.checkoutRequestId = checkoutId;
    }

    /** Keeps the first callback's claims. They count only once the status query confirms. */
    void recordCallback(@Nullable BigDecimal amount, @Nullable String receipt, Instant at) {
        if (this.callbackAt == null) {
            this.callbackAt = at;
            this.callbackAmountKes = amount;
            this.callbackReceipt = receipt;
        }
    }

    void verificationQueued(Instant at) {
        this.verificationQueuedAt = at;
    }

    void markPaid(String receipt) {
        this.mpesaReceipt = receipt;
        this.status = PaymentStatus.PAID;
    }

    void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    void markUnconfirmed() {
        this.status = PaymentStatus.UNCONFIRMED;
    }

    void resolved(String reason, UUID by, Instant at) {
        this.resolutionReason = reason;
        this.resolvedBy = by;
        this.resolvedAt = at;
    }

    public @Nullable Instant getPromptedAt() {
        return promptedAt;
    }

    public @Nullable Instant getVerificationQueuedAt() {
        return verificationQueuedAt;
    }

    public @Nullable BigDecimal getCallbackAmountKes() {
        return callbackAmountKes;
    }

    public @Nullable String getCallbackReceipt() {
        return callbackReceipt;
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
