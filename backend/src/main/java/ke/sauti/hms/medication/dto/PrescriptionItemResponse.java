package ke.sauti.hms.medication.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ke.sauti.hms.medication.DoseUnit;
import ke.sauti.hms.medication.Frequency;
import ke.sauti.hms.medication.Medicine;
import ke.sauti.hms.medication.PrescriptionItem;
import org.jspecify.annotations.Nullable;

/**
 * One line of a prescription.
 *
 * @param id the item id
 * @param medicine the medicine
 * @param doseValue the amount per dose
 * @param doseUnit the unit of the dose
 * @param frequency how often it is taken
 * @param durationDays for how many days
 * @param route how it is given
 * @param quantity how many units to dispense
 * @param instructions extra directions for the patient
 */
public record PrescriptionItemResponse(
        UUID id,
        MedicineResponse medicine,
        @Nullable BigDecimal doseValue,
        @Nullable DoseUnit doseUnit,
        @Nullable Frequency frequency,
        @Nullable Integer durationDays,
        @Nullable String route,
        @Nullable Integer quantity,
        @Nullable String instructions) {

    public static PrescriptionItemResponse of(PrescriptionItem item, Medicine medicine) {
        return new PrescriptionItemResponse(
                item.getId(),
                MedicineResponse.of(medicine),
                item.getDoseValue(),
                item.getDoseUnit(),
                item.getFrequency(),
                item.getDurationDays(),
                item.getRoute(),
                item.getQuantity(),
                item.getInstructions());
    }
}
