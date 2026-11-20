package ke.sauti.hms.security;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The session note between the password step and the code step. Holding it does not log
 * anyone in; it only says whose code to expect, and until when. The authenticator secret
 * is never put here, because session data is stored unencrypted.
 *
 * @param staffId whose password was right
 * @param setup whether this code also confirms a new authenticator
 * @param expiresAt after this, the login starts again
 */
public record PendingLogin(UUID staffId, boolean setup, Instant expiresAt) implements Serializable {

    /** The session attribute that holds the note. */
    public static final String ATTRIBUTE = PendingLogin.class.getName();

    /** How long the code step stays open after the password step. */
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    public static PendingLogin startingAt(UUID staffId, boolean setup, Instant now) {
        return new PendingLogin(staffId, setup, now.plus(LIFETIME));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
