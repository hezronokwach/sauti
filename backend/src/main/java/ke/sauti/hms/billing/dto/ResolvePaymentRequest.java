package ke.sauti.hms.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The cashier's finding after checking the M-Pesa statement.
 *
 * @param paid true when the statement shows the full amount was received
 * @param receipt the M-Pesa receipt; required when paid
 * @param reason what the cashier checked
 */
public record ResolvePaymentRequest(
        @NotNull Boolean paid,

        @Nullable @Pattern(regexp = "[A-Za-z0-9]{6,40}", message = "Enter the M-Pesa receipt")
        String receipt,

        @NotBlank @Size(min = 10, max = 500) String reason) {}
