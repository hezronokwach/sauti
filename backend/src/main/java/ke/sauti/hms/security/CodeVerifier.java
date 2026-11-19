package ke.sauti.hms.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Checks an authenticator code for a staff member. A code from the previous, current or
 * next 30-second step is accepted (30 seconds of clock drift either way), but only if
 * that step is newer than the last one used. On success the step is recorded on the
 * entity, and the caller's transaction saves it.
 */
@Component
public class CodeVerifier {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");
    private static final int DRIFT_STEPS = 1;

    private final TotpGenerator totp;

    public CodeVerifier(TotpGenerator totp) {
        this.totp = totp;
    }

    public boolean verify(Staff staff, @Nullable String code) {
        String secret = staff.getMfaSecret();
        if (code == null || secret == null || !SIX_DIGITS.matcher(code).matches()) {
            return false;
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long now = totp.currentStep();
        for (long step = now - DRIFT_STEPS; step <= now + DRIFT_STEPS; step++) {
            byte[] expected = totp.codeFor(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, given) && staff.acceptCodeStep(step)) {
                return true;
            }
        }
        return false;
    }
}
