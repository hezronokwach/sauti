package ke.sauti.hms.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Recording a payment. The amount is never sent: the full invoice total is always what is paid.
 *
 * @param idempotencyKey made once by the screen, so repeating the request records one payment
 */
public record RecordPaymentRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+")
        String idempotencyKey) {}
