package ke.sauti.hms.common.error;

import java.time.Duration;
import java.util.Objects;

/** The caller sent too many requests (429); the response says when to retry. */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(String message, Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, message);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
