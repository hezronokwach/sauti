package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;
import ke.sauti.hms.common.error.ErrorCode;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;

class CodeCheckTest {

    private final TotpGenerator totp = new TotpGenerator(Clock.fixed(FixedClockConfiguration.NOW, ZoneOffset.UTC));
    private final CodeCheck check = new CodeCheck(new CodeVerifier(totp));

    private String currentCode() {
        return totp.codeFor(CodeVerifierTest.SECRET, totp.currentStep());
    }

    private static Staff confirmed() {
        Staff staff = CodeVerifierTest.withSecret();
        staff.confirmAuthenticator();
        return staff;
    }

    @Test
    void passesWithAGoodCode() {
        assertThatCode(() -> check.require(confirmed(), currentCode())).doesNotThrowAnyException();
    }

    @Test
    void refusesABadCodeWith403() {
        assertThatThrownBy(() -> check.require(confirmed(), "abc"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("The authenticator code is not valid")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void refusesWhenTheAuthenticatorIsNotSetUp() {
        Staff unconfirmed = CodeVerifierTest.withSecret();

        assertThatThrownBy(() -> check.require(unconfirmed, currentCode()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("The authenticator is not set up");
    }
}
