package ke.sauti.hms.capture;

import java.util.UUID;

/**
 * Consent to record was withdrawn. Published inside the recording transaction; capture
 * stops any recording for this visit and purpose once it commits.
 *
 * @param visitId the visit
 * @param purpose what the recording was for
 */
public record ConsentWithdrawnEvent(UUID visitId, ConsentPurpose purpose) {}
