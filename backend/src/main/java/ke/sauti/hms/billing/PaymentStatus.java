package ke.sauti.hms.billing;

/** The state of one payment attempt. */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    UNCONFIRMED
}
