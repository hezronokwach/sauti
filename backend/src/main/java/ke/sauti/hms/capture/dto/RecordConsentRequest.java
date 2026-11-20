package ke.sauti.hms.capture.dto;

import jakarta.validation.constraints.NotNull;
import ke.sauti.hms.capture.ConsentDecision;
import ke.sauti.hms.capture.ConsentGivenBy;
import ke.sauti.hms.capture.ConsentPurpose;

/**
 * A consent decision, after the wording was read to the patient.
 *
 * @param purpose what the recording is for
 * @param decision agreed, refused or withdrawn
 * @param givenBy the patient, or who decided for them
 */
public record RecordConsentRequest(
        @NotNull ConsentPurpose purpose,
        @NotNull ConsentDecision decision,
        @NotNull ConsentGivenBy givenBy) {}
