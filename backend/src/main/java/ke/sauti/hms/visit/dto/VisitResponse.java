package ke.sauti.hms.visit.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitStatus;
import org.jspecify.annotations.Nullable;

/**
 * A visit's place in the workflow. Nothing clinical: the reason for the visit is read
 * through the access policy (issue 18).
 *
 * @param id the visit id
 * @param patientId the patient
 * @param visitDate the facility's local day it was opened
 * @param queueNumber the day's queue number
 * @param status where the visit is now
 * @param arrivedAt when reception opened it
 * @param statusChangedAt when the current status began
 * @param assignedDoctorId the doctor who took the patient, once one has
 */
public record VisitResponse(
        UUID id,
        UUID patientId,
        LocalDate visitDate,
        int queueNumber,
        VisitStatus status,
        Instant arrivedAt,
        Instant statusChangedAt,
        @Nullable UUID assignedDoctorId) {

    public static VisitResponse of(Visit visit) {
        return new VisitResponse(
                visit.getId(),
                visit.getPatientId(),
                visit.getVisitDate(),
                visit.getQueueNumber(),
                visit.getStatus(),
                visit.getArrivedAt(),
                visit.getStatusChangedAt(),
                visit.getAssignedDoctorId());
    }
}
