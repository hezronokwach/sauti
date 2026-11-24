package ke.sauti.hms.triage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.common.persistence.BaseEntity;

/** One set of vital signs taken at triage. Never edited: a new reading is a new row. */
@Entity
@Table(name = "vitals")
public class Vitals extends BaseEntity {

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "temperature_c", nullable = false, updatable = false, precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "pulse_bpm", nullable = false, updatable = false)
    private int pulseBpm;

    @Column(name = "respiratory_rate", nullable = false, updatable = false)
    private int respiratoryRate;

    @Column(name = "systolic_mmhg", nullable = false, updatable = false)
    private int systolicMmhg;

    @Column(name = "diastolic_mmhg", nullable = false, updatable = false)
    private int diastolicMmhg;

    @Column(name = "spo2_percent", nullable = false, updatable = false)
    private int spo2Percent;

    @Column(name = "weight_kg", updatable = false, precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "height_cm", updatable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    protected Vitals() {
        // for JPA
    }

    Vitals(UUID visitId, UUID recordedBy, Instant recordedAt, Readings readings) {
        this.visitId = visitId;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.temperatureC = readings.temperatureC();
        this.pulseBpm = readings.pulseBpm();
        this.respiratoryRate = readings.respiratoryRate();
        this.systolicMmhg = readings.systolicMmhg();
        this.diastolicMmhg = readings.diastolicMmhg();
        this.spo2Percent = readings.spo2Percent();
        this.weightKg = readings.weightKg();
        this.heightCm = readings.heightCm();
    }

    /**
     * The measured values.
     *
     * @param temperatureC body temperature, degrees Celsius
     * @param pulseBpm heart rate, beats per minute
     * @param respiratoryRate breaths per minute
     * @param systolicMmhg systolic blood pressure
     * @param diastolicMmhg diastolic blood pressure
     * @param spo2Percent oxygen saturation
     * @param weightKg weight, if measured
     * @param heightCm height, if measured
     */
    public record Readings(
            BigDecimal temperatureC,
            int pulseBpm,
            int respiratoryRate,
            int systolicMmhg,
            int diastolicMmhg,
            int spo2Percent,
            BigDecimal weightKg,
            BigDecimal heightCm) {}

    public Readings readings() {
        return new Readings(
                temperatureC, pulseBpm, respiratoryRate, systolicMmhg, diastolicMmhg, spo2Percent, weightKg, heightCm);
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
