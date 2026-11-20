package ke.sauti.hms.triage.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.triage.Vitals;
import org.jspecify.annotations.Nullable;

/**
 * One set of vital signs.
 *
 * @param id the reading id
 * @param visitId the visit
 * @param recordedBy the nurse
 * @param recordedAt when
 * @param temperatureC degrees Celsius
 * @param pulseBpm beats per minute
 * @param respiratoryRate breaths per minute
 * @param systolicMmhg mmHg
 * @param diastolicMmhg mmHg
 * @param spo2Percent percent
 * @param weightKg kg, if measured
 * @param heightCm cm, if measured
 */
public record VitalsResponse(
        UUID id,
        UUID visitId,
        UUID recordedBy,
        Instant recordedAt,
        BigDecimal temperatureC,
        int pulseBpm,
        int respiratoryRate,
        int systolicMmhg,
        int diastolicMmhg,
        int spo2Percent,
        @Nullable BigDecimal weightKg,
        @Nullable BigDecimal heightCm) {

    public static VitalsResponse of(Vitals vitals) {
        Vitals.Readings r = vitals.readings();
        return new VitalsResponse(
                vitals.getId(),
                vitals.getVisitId(),
                vitals.getRecordedBy(),
                vitals.getRecordedAt(),
                r.temperatureC(),
                r.pulseBpm(),
                r.respiratoryRate(),
                r.systolicMmhg(),
                r.diastolicMmhg(),
                r.spo2Percent(),
                r.weightKg(),
                r.heightCm());
    }
}
