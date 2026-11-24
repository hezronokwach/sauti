package ke.sauti.hms.medication.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import ke.sauti.hms.medication.DoseUnit;
import ke.sauti.hms.medication.Frequency;
import org.jspecify.annotations.Nullable;

/**
 * One medicine on a draft. Only the medicine is required here: the safety rules refuse signing
 * while a detail is missing.
 *
 * @param revision the prescription revision the doctor read; empty for the first item
 * @param medicineId the medicine from the list
 * @param doseValue the amount per dose
 * @param doseUnit the unit of the dose
 * @param frequency how often it is taken
 * @param durationDays for how many days
 * @param route how it is given; must be one the medicine allows
 * @param quantity how many units to dispense
 * @param instructions extra directions for the patient
 */
public record PrescriptionItemRequest(
        @Nullable @PositiveOrZero Integer revision,
        @NotNull UUID medicineId,

        @Nullable @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3)
        BigDecimal doseValue,

        @Nullable DoseUnit doseUnit,
        @Nullable Frequency frequency,
        @Nullable @Min(1) @Max(365) Integer durationDays,
        @Nullable @Size(max = 20) String route,
        @Nullable @Min(1) @Max(100000) Integer quantity,
        @Nullable @Size(max = 500) String instructions) {}
