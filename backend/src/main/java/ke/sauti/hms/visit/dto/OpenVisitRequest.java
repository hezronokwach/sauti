package ke.sauti.hms.visit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Opening a visit at reception.
 *
 * @param patientId the registered patient
 * @param reasonForVisit what the patient says brings them in; stored encrypted
 * @param ticketId the arrival ticket reception called for this patient, if any; the visit
 *     then counts its waiting time from the ticket
 */
public record OpenVisitRequest(
        @NotNull UUID patientId,
        @Nullable @Size(max = 500) String reasonForVisit,
        @Nullable UUID ticketId) {}
