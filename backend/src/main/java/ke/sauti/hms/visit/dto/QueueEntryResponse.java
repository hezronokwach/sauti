package ke.sauti.hms.visit.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.visit.VisitStatus;

/**
 * One patient in a queue: who, which number, and since when. Nothing clinical, so the
 * queue screens can show it to anyone working that queue.
 *
 * @param visitId the visit
 * @param queueNumber the day's queue number
 * @param status the queue
 * @param patientId the patient
 * @param patientName the patient's name
 * @param patientNumber for example SH-000123
 * @param waitingSince when the patient joined this queue
 */
public record QueueEntryResponse(
        UUID visitId,
        int queueNumber,
        VisitStatus status,
        UUID patientId,
        String patientName,
        String patientNumber,
        Instant waitingSince) {}
