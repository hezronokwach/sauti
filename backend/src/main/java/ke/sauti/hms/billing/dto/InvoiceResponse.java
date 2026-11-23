package ke.sauti.hms.billing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.billing.InvoiceStatus;
import ke.sauti.hms.billing.PaymentMethod;
import org.jspecify.annotations.Nullable;

/**
 * A visit's bill.
 *
 * @param paymentMethod how it was settled; null while unpaid
 */
public record InvoiceResponse(
        UUID id,
        UUID visitId,
        InvoiceStatus status,
        BigDecimal totalKes,
        @Nullable PaymentMethod paymentMethod,
        List<InvoiceLineResponse> lines) {}
