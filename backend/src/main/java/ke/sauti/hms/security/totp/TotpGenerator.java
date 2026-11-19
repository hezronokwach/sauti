package ke.sauti.hms.security.totp;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;
import org.springframework.stereotype.Component;

/**
 * Authenticator secrets and codes as authenticator apps expect them (RFC 6238 with
 * HMAC-SHA1, 30-second steps, 6 digits). Checking a code against a person, including the
 * single-use rule, is {@link ke.sauti.hms.security.CodeVerifier}'s job.
 */
@Component
public class TotpGenerator {

    static final int SECRET_BYTES = 20;
    static final long STEP_SECONDS = 30;
    static final int DIGITS = 6;
    private static final String ISSUER = "Sauti";
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TotpGenerator(Clock clock) {
        this.clock = clock;
    }

    /** A new secret: 20 random bytes as 32 Base32 characters. */
    public String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return Base32.toBase32String(secret);
    }

    /** What the setup QR code holds; an authenticator app reads the secret from it. */
    public String setupUri(String email, String secret) {
        String label =
                URLEncoder.encode(ISSUER + ":" + email, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER;
    }

    /** The 30-second step the clock is in now. */
    public long currentStep() {
        return Math.floorDiv(clock.instant().getEpochSecond(), STEP_SECONDS);
    }

    /** The 6-digit code for a Base32 secret at a step. */
    public String codeFor(String secret, long step) {
        return codeFor(Base32.decode(secret), step, DIGITS);
    }

    static String codeFor(byte[] key, long step, int digits) {
        byte[] mac;
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(key, "HmacSHA1"));
            mac = hmac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", e);
        }
        int offset = mac[mac.length - 1] & 0x0f;
        int binary = ((mac[offset] & 0x7f) << 24)
                | ((mac[offset + 1] & 0xff) << 16)
                | ((mac[offset + 2] & 0xff) << 8)
                | (mac[offset + 3] & 0xff);
        String code = Integer.toString(binary % POWERS_OF_TEN[digits]);
        return "0".repeat(digits - code.length()) + code;
    }
}
