package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CodeVerifierTest {

    static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private final TotpGenerator totp = new TotpGenerator(Clock.fixed(FixedClockConfiguration.NOW, ZoneOffset.UTC));
    private final CodeVerifier verifier = new CodeVerifier(totp);
    private final long now = totp.currentStep();

    static Staff withSecret() {
        Staff staff = new Staff("Amina Otieno", "amina@example.test", null, StaffRole.NURSE, "NCK-1", "hash");
        staff.startAuthenticatorSetup(SECRET);
        return staff;
    }

    private String codeAt(long step) {
        return totp.codeFor(SECRET, step);
    }

    @Test
    void acceptsTheCurrentCodeAndRecordsItsStep() {
        Staff staff = withSecret();

        assertThat(verifier.verify(staff, codeAt(now))).isTrue();
        assertThat(staff.getLastCodeStep()).isEqualTo(now);
    }

    @Test
    void allowsOneStepOfClockDriftEitherWay() {
        assertThat(verifier.verify(withSecret(), codeAt(now - 1))).isTrue();
        assertThat(verifier.verify(withSecret(), codeAt(now + 1))).isTrue();
    }

    @Test
    void refusesACodeTwoStepsAway() {
        assertThat(verifier.verify(withSecret(), codeAt(now - 2))).isFalse();
        assertThat(verifier.verify(withSecret(), codeAt(now + 2))).isFalse();
    }

    @Test
    void aCodeWorksOnlyOnce() {
        Staff staff = withSecret();
        String code = codeAt(now);

        assertThat(verifier.verify(staff, code)).isTrue();
        assertThat(verifier.verify(staff, code)).isFalse();
    }

    @Test
    void anOlderCodeIsRefusedAfterANewerOneWasUsed() {
        Staff staff = withSecret();

        assertThat(verifier.verify(staff, codeAt(now))).isTrue();
        assertThat(verifier.verify(staff, codeAt(now - 1))).isFalse();
        assertThat(staff.getLastCodeStep()).isEqualTo(now);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345", "1234567", "12a456", " 123456", "１２３４５６"})
    void refusesCodesThatAreNotSixDigits(String code) {
        assertThat(verifier.verify(withSecret(), code)).isFalse();
    }

    @Test
    void refusesAWrongCode() {
        String right = codeAt(now);
        String wrong = right.equals("000000") ? "000001" : "000000";

        assertThat(verifier.verify(withSecret(), wrong)).isFalse();
    }

    @Test
    void refusesAStaffMemberWithoutASecret() {
        Staff noSecret = new Staff("No Secret", "none@example.test", null, StaffRole.CASHIER, null, "hash");

        assertThat(verifier.verify(noSecret, codeAt(now))).isFalse();
        assertThat(noSecret.getLastCodeStep()).isNull();
    }
}
