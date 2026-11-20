package ke.sauti.hms.triage.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Vital signs from triage, each within plausible human limits; anything outside is a typing
 * mistake and is refused field by field.
 *
 * @param temperatureC 30 to 45 degrees Celsius, one decimal
 * @param pulseBpm 20 to 250 beats per minute
 * @param respiratoryRate 4 to 80 breaths per minute
 * @param systolicMmhg 50 to 260 mmHg
 * @param diastolicMmhg 20 to 160 mmHg, below systolic
 * @param spo2Percent 50 to 100 percent
 * @param weightKg 0.5 to 300 kg, optional
 * @param heightCm 30 to 250 cm, optional
 */
public record RecordVitalsRequest(
        @NotNull @DecimalMin("30.0") @DecimalMax("45.0") @Digits(integer = 2, fraction = 1)
        BigDecimal temperatureC,

        @NotNull @Min(20) @Max(250) Integer pulseBpm,
        @NotNull @Min(4) @Max(80) Integer respiratoryRate,
        @NotNull @Min(50) @Max(260) Integer systolicMmhg,
        @NotNull @Min(20) @Max(160) Integer diastolicMmhg,
        @NotNull @Min(50) @Max(100) Integer spo2Percent,

        @Nullable @DecimalMin("0.5") @DecimalMax("300.0") @Digits(integer = 3, fraction = 1)
        BigDecimal weightKg,

        @Nullable @DecimalMin("30.0") @DecimalMax("250.0") @Digits(integer = 3, fraction = 1)
        BigDecimal heightCm) {}
