package ke.sauti.hms.security.dto;

import org.jspecify.annotations.Nullable;

/**
 * The reply to the password step: what the person must send next.
 *
 * @param codeRequired send an authenticator code next
 * @param setupRequired scan {@code otpauthUri} into an authenticator app, then send a code
 * @param otpauthUri the setup QR content; only present when setup is required
 */
public record LoginNextStepResponse(
        boolean codeRequired,
        boolean setupRequired,
        @Nullable String otpauthUri) {

    public static LoginNextStepResponse askForCode() {
        return new LoginNextStepResponse(true, false, null);
    }

    public static LoginNextStepResponse askForSetup(String otpauthUri) {
        return new LoginNextStepResponse(true, true, otpauthUri);
    }
}
