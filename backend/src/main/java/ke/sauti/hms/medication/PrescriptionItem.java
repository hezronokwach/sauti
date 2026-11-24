package ke.sauti.hms.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import ke.sauti.hms.common.crypto.EncryptedStringConverter;
import ke.sauti.hms.common.persistence.BaseEntity;
import ke.sauti.hms.medication.dto.PrescriptionItemRequest;

/** One medicine on a prescription. Details may be empty until the prescription is signed. */
@Entity
@Table(name = "prescription_item")
public class PrescriptionItem extends BaseEntity {

    @Column(name = "prescription_id", nullable = false, updatable = false)
    private UUID prescriptionId;

    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;

    @Column(name = "dose_value")
    private BigDecimal doseValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "dose_unit")
    private DoseUnit doseUnit;

    @Enumerated(EnumType.STRING)
    @Column
    private Frequency frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column
    private String route;

    @Column
    private Integer quantity;

    @Convert(converter = EncryptedStringConverter.class)
    @Column
    private String instructions;

    protected PrescriptionItem() {
        // for JPA
    }

    PrescriptionItem(UUID prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    /** Replaces every detail with what the doctor sent. */
    void write(PrescriptionItemRequest request) {
        this.medicineId = request.medicineId();
        this.doseValue = request.doseValue();
        this.doseUnit = request.doseUnit();
        this.frequency = request.frequency();
        this.durationDays = request.durationDays();
        this.route = request.route();
        this.quantity = request.quantity();
        String text = request.instructions();
        this.instructions = text == null || text.isBlank() ? null : text.strip();
    }

    public UUID getPrescriptionId() {
        return prescriptionId;
    }

    public UUID getMedicineId() {
        return medicineId;
    }

    public BigDecimal getDoseValue() {
        return doseValue;
    }

    public DoseUnit getDoseUnit() {
        return doseUnit;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public String getRoute() {
        return route;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getInstructions() {
        return instructions;
    }
}
