package ke.sauti.hms.registry.dto;

import java.time.Instant;
import ke.sauti.hms.registry.PatientEligibility;
import org.jspecify.annotations.Nullable;

/**
 * A patient's SHA eligibility for the badge.
 *
 * @param status NONE (no national ID, never checked), PENDING, ELIGIBLE, INELIGIBLE, NOT_FOUND
 *     or UNAVAILABLE
 * @param checkedAt when the service last answered
 */
public record EligibilityResponse(String status, @Nullable Instant checkedAt) {

    public static EligibilityResponse of(PatientEligibility answer) {
        return new EligibilityResponse(answer.getStatus().name(), answer.getCheckedAt());
    }

    public static EligibilityResponse none() {
        return new EligibilityResponse("NONE", null);
    }
}
