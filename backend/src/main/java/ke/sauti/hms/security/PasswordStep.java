package ke.sauti.hms.security;

import java.util.UUID;

/** What the password step decided. Returned rather than thrown, so its audit row commits. */
public sealed interface PasswordStep {

    /** Refused, for whatever reason; the caller answers with one generic message. */
    record Failed() implements PasswordStep {}

    /** Password right; the authenticator code comes next. */
    record CodeRequired(UUID staffId) implements PasswordStep {}

    /** Password right, no authenticator yet; the person scans this setup URI, then sends a code. */
    record SetupRequired(UUID staffId, String otpauthUri) implements PasswordStep {}
}
