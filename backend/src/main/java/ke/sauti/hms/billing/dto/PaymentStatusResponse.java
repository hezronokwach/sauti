package ke.sauti.hms.billing.dto;

import ke.sauti.hms.billing.InvoiceStatus;
import ke.sauti.hms.billing.PaymentStatus;
import org.jspecify.annotations.Nullable;

/**
 * Where the bill's payment stands, for the screen to poll. It carries nothing clinical.
 *
 * @param invoiceStatus the bill
 * @param paymentStatus the newest attempt; null when none was made
 */
public record PaymentStatusResponse(
        InvoiceStatus invoiceStatus, @Nullable PaymentStatus paymentStatus) {}
