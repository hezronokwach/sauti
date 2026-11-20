package ke.sauti.hms.ai.dto;

import java.util.List;
import java.util.UUID;
import ke.sauti.hms.ai.SummaryResult;
import org.jspecify.annotations.Nullable;

/**
 * A summary: the checked claims, or a message that it is unavailable.
 *
 * @param requestId the ai_request row
 * @param available false when the model could not be reached
 * @param message "Summary unavailable" when not available
 * @param claims the checked claims
 */
public record SummaryResponse(
        UUID requestId, boolean available, @Nullable String message, List<ClaimResponse> claims) {

    public static SummaryResponse of(SummaryResult result) {
        return new SummaryResponse(
                result.requestId(),
                result.available(),
                result.available() ? null : "Summary unavailable; write the note yourself or try again later",
                result.claims().stream().map(ClaimResponse::of).toList());
    }
}
