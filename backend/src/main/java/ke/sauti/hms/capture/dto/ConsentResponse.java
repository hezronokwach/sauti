package ke.sauti.hms.capture.dto;

import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.capture.Consent;
import ke.sauti.hms.capture.ConsentDecision;
import ke.sauti.hms.capture.ConsentGivenBy;
import ke.sauti.hms.capture.ConsentPurpose;

/**
 * One recorded consent decision.
 *
 * @param id the decision id
 * @param visitId the visit
 * @param purpose what the recording is for
 * @param decision agreed, refused or withdrawn
 * @param givenBy who decided
 * @param wordingVersion the wording shown, for example v1-sw
 * @param recordedBy the staff member who recorded it
 * @param recordedAt when
 */
public record ConsentResponse(
        UUID id,
        UUID visitId,
        ConsentPurpose purpose,
        ConsentDecision decision,
        ConsentGivenBy givenBy,
        String wordingVersion,
        UUID recordedBy,
        Instant recordedAt) {

    public static ConsentResponse of(Consent consent) {
        return new ConsentResponse(
                consent.getId(),
                consent.getVisitId(),
                consent.getPurpose(),
                consent.getDecision(),
                consent.getGivenBy(),
                consent.getWordingVersion(),
                consent.getRecordedBy(),
                consent.getRecordedAt());
    }
}
