package ke.sauti.hms.security;

/** What the code step decided. Returned rather than thrown, so its audit row commits. */
public sealed interface CodeStep {

    /**
     * Refused.
     *
     * @param loginEnded whether the pending login is over (the account is now locked or
     *     disabled), so the caller drops the session note
     */
    record Failed(boolean loginEnded) implements CodeStep {}

    /** Logged in; the security context already holds this person. */
    record LoggedIn(StaffPrincipal principal) implements CodeStep {}
}
