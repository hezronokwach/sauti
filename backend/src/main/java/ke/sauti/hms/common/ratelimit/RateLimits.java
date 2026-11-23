package ke.sauti.hms.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import java.time.Duration;
import ke.sauti.hms.common.error.RateLimitedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Request limits, held in this app's memory. Each limit and key (an address or an account) has
 * its own bucket. The cache is bounded, so many fake keys cannot exhaust memory. With several app
 * instances each one counts separately.
 */
@Component
public class RateLimits {

    private static final String TOO_MANY = "Too many attempts; try again shortly";
    private static final int MAX_KEYS = 100_000;

    /**
     * Every limit in one place. Address limits are generous because a whole hospital can share
     * one public address at the start of a shift.
     */
    public enum Limit {
        LOGIN_ADDRESS(60, Duration.ofMinutes(15)),
        LOGIN_ACCOUNT(10, Duration.ofMinutes(15)),
        CODE_ADDRESS(60, Duration.ofMinutes(15)),
        CODE_ACCOUNT(10, Duration.ofMinutes(15)),
        /** Wrong codes when signing or overriding; right ones never use this up. */
        WRONG_SIGNING_CODE(10, Duration.ofMinutes(15)),
        UPLOAD_ADDRESS(1_200, Duration.ofMinutes(1)),
        UPLOAD_ACCOUNT(300, Duration.ofMinutes(1)),
        SUMMARY_ADDRESS(60, Duration.ofMinutes(1)),
        SUMMARY_ACCOUNT(10, Duration.ofMinutes(1)),
        QUEUE_BOARD_ADDRESS(240, Duration.ofMinutes(1)),
        PRE_REGISTRATION_ADDRESS(20, Duration.ofHours(1)),
        MPESA_CALLBACK_ADDRESS(120, Duration.ofMinutes(1));

        private final long capacity;
        private final Duration period;

        Limit(long capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }

        public long capacity() {
            return capacity;
        }
    }

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterAccess(Duration.ofHours(2))
            .build();
    private final boolean enabled;

    public RateLimits(@Value("${sauti.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Uses up one request, or refuses with 429 and when to retry. */
    public void check(Limit limit, String key) {
        if (!enabled) {
            return;
        }
        ConsumptionProbe probe = bucket(limit, key).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitedException(TOO_MANY, Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    /** Refuses with 429 if this limit is used up, without using any of it. */
    public void requireAvailable(Limit limit, String key) {
        if (!enabled) {
            return;
        }
        EstimationProbe probe = bucket(limit, key).estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw new RateLimitedException(TOO_MANY, Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    /** Counts one failed attempt against a limit checked with {@link #requireAvailable}. */
    public void recordFailure(Limit limit, String key) {
        if (enabled) {
            bucket(limit, key).tryConsume(1);
        }
    }

    private Bucket bucket(Limit limit, String key) {
        return buckets.get(
                limit.name() + ":" + key,
                ignored -> Bucket.builder()
                        .addLimit(bandwidth ->
                                bandwidth.capacity(limit.capacity).refillGreedy(limit.capacity, limit.period))
                        .build());
    }
}
