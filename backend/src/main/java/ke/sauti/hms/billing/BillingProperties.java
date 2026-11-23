package ke.sauti.hms.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Billing settings.
 *
 * @param consultationFeeKes the fee on every invoice; a demonstration amount until the facility sets one
 */
@Validated
@ConfigurationProperties(prefix = "sauti.billing")
public record BillingProperties(@NotNull @PositiveOrZero BigDecimal consultationFeeKes) {}
