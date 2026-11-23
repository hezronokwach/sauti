package ke.sauti.hms.medication.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.medication.Prescription;
import ke.sauti.hms.medication.PrescriptionStatus;
import org.jspecify.annotations.Nullable;

/**
 * A visit's prescription.
 *
 * @param id the prescription id
 * @param visitId the visit
 * @param status DRAFT, SIGNED or CANCELLED
 * @param rulesVersion the medicine list and rules it is checked against
 * @param revision send this back with the next change
 * @param signedAt when it was signed
 * @param items its medicines, in the order added
 */
public record PrescriptionResponse(
        UUID id,
        UUID visitId,
        PrescriptionStatus status,
        String rulesVersion,
        int revision,
        @Nullable Instant signedAt,
        List<PrescriptionItemResponse> items) {

    public static PrescriptionResponse of(Prescription prescription, List<PrescriptionItemResponse> items) {
        return new PrescriptionResponse(
                prescription.getId(),
                prescription.getVisitId(),
                prescription.getStatus(),
                prescription.getRulesVersion(),
                prescription.getRevision(),
                prescription.getSignedAt(),
                items);
    }
}
