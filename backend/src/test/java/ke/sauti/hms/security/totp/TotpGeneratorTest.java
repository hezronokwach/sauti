package ke.sauti.hms.security.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.bouncycastle.util.encoders.Base32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpGeneratorTest {

    /** The SHA-1 seed from RFC 6238 Appendix B: the ASCII string "12345678901234567890". */
    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static TotpGenerator at(long epochSecond) {
        return new TotpGenerator(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    /** RFC 6238 Appendix B, SHA-1 rows: Unix time and the published 8-digit code. */
    @ParameterizedTest
    @CsvSource({
        "59, 94287082",
        "1111111109, 07081804",
        "1111111111, 14050471",
        "1234567890, 89005924",
        "2000000000, 69279037",
        "20000000000, 65353130"
    })
    void matchesTheRfc6238TestVectors(long unixTime, String expected) {
        long step = at(unixTime).currentStep();

        assertThat(TotpGenerator.codeFor(RFC_SEED, step, 8)).isEqualTo(expected);
        assertThat(TotpGenerator.codeFor(RFC_SEED, step, 6)).isEqualTo(expected.substring(2));
    }

    @Test
    void sixDigitCodesComeFromTheBase32Secret() {
        String secret = Base32.toBase32String(RFC_SEED);

        assertThat(at(59).codeFor(secret, 1)).isEqualTo("287082");
    }

    @Test
    void stepsAreThirtySecondsLong() {
        assertThat(at(0).currentStep()).isZero();
        assertThat(at(29).currentStep()).isZero();
        assertThat(at(30).currentStep()).isEqualTo(1);
    }

    @Test
    void aNewSecretIsTwentyRandomBytesInBase32() {
        TotpGenerator generator = at(0);

        String secret = generator.newSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Base32.decode(secret)).hasSize(20);
        assertThat(generator.newSecret()).isNotEqualTo(secret);
    }

    @Test
    void theSetupUriNamesTheIssuerAndEncodesTheEmail() {
        String uri = at(0).setupUri("amina+clinic@example.test", "JBSWY3DPEHPK3PXP");

        assertThat(uri)
                .isEqualTo("otpauth://totp/Sauti%3Aamina%2Bclinic%40example.test"
                        + "?secret=JBSWY3DPEHPK3PXP&issuer=Sauti");
    }
}
