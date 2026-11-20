package ke.sauti.hms.ai;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of one summary request.
 *
 * @param requestId the ai_request row
 * @param available false when the model failed twice ("Summary unavailable")
 * @param claims the checked claims, empty when unavailable
 */
public record SummaryResult(UUID requestId, boolean available, List<CheckedClaim> claims) {}
