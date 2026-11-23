package ke.sauti.hms.billing;

/** Where a bill stands. Insurance claims stay pending after the visit has closed. */
public enum InvoiceStatus {
    UNPAID,
    PAID,
    INSURANCE_PENDING
}
