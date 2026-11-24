package ke.sauti.hms.jobs;

import java.io.Serial;
import java.time.Duration;
import java.util.Objects;

/** How a {@link JobHandler} says a job did not succeed, when the default (retry) is wrong. */
public abstract class JobFailure extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private JobFailure(String message, Throwable cause) {
        super(message, cause);
    }

    /** Retrying cannot help; the job is marked FAILED at once. */
    public static final class Permanent extends JobFailure {

        @Serial
        private static final long serialVersionUID = 1L;

        public Permanent(String message) {
            super(message, null);
        }

        public Permanent(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The provider asked to wait (rate limit); the job runs again after the delay, not counting as an attempt. */
    public static final class RetryAfter extends JobFailure {

        @Serial
        private static final long serialVersionUID = 1L;

        private final Duration delay;

        public RetryAfter(Duration delay, String message) {
            super(message, null);
            this.delay = Objects.requireNonNull(delay, "delay");
        }

        public Duration getDelay() {
            return delay;
        }
    }
}
