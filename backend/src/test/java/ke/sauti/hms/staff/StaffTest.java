package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StaffTest {

    private static Staff receptionist() {
        return new Staff("Wanjiru Kamau", "wanjiru@example.test", null, StaffRole.RECEPTIONIST, null, "first-hash");
    }

    @Test
    void newStaffAreActiveAndMustChangeTheirPassword() {
        Staff staff = receptionist();

        assertThat(staff.getStatus()).isEqualTo(StaffStatus.ACTIVE);
        assertThat(staff.isMustChangePassword()).isTrue();
    }

    @Test
    void disablingStopsTheAccount() {
        Staff staff = receptionist();

        staff.disable();

        assertThat(staff.getStatus()).isEqualTo(StaffStatus.DISABLED);
    }

    @Test
    void changingThePasswordReplacesTheHashAndEndsTheForcedChange() {
        Staff staff = receptionist();

        staff.changePassword("new-hash");

        assertThat(staff.getPasswordHash()).isEqualTo("new-hash");
        assertThat(staff.isMustChangePassword()).isFalse();
    }

    @Test
    void aPasswordHashIsRequired() {
        Staff staff = receptionist();

        assertThatThrownBy(() -> staff.changePassword(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void startingAuthenticatorSetupStoresTheSecretButDoesNotRequireCodesYet() {
        Staff staff = receptionist();

        staff.startAuthenticatorSetup("JBSWY3DPEHPK3PXP");

        assertThat(staff.getMfaSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(staff.isMfaEnabled()).isFalse();
    }

    @Test
    void confirmingTheAuthenticatorTurnsCodesOn() {
        Staff staff = receptionist();
        staff.startAuthenticatorSetup("JBSWY3DPEHPK3PXP");

        staff.confirmAuthenticator();

        assertThat(staff.isMfaEnabled()).isTrue();
    }

    @Test
    void anAuthenticatorCannotBeConfirmedWithoutASecret() {
        Staff staff = receptionist();

        assertThatThrownBy(staff::confirmAuthenticator).isInstanceOf(IllegalStateException.class);
        assertThat(staff.isMfaEnabled()).isFalse();
    }

    @Test
    void onlyANewerCodeStepIsAccepted() {
        Staff staff = receptionist();

        assertThat(staff.acceptCodeStep(100)).isTrue();
        assertThat(staff.acceptCodeStep(100)).isFalse();
        assertThat(staff.acceptCodeStep(99)).isFalse();
        assertThat(staff.acceptCodeStep(101)).isTrue();
        assertThat(staff.getLastCodeStep()).isEqualTo(101);
    }

    @Test
    void theFifthFailureLocksForFifteenMinutesAndRestartsTheCount() {
        Staff staff = receptionist();
        Instant now = Instant.parse("2026-01-01T08:00:00Z");

        for (int i = 1; i < Staff.MAX_FAILED_LOGINS; i++) {
            assertThat(staff.recordFailedLogin(now)).isFalse();
        }
        assertThat(staff.getFailedLogins()).isEqualTo(4);
        assertThat(staff.isLocked(now)).isFalse();

        assertThat(staff.recordFailedLogin(now)).isTrue();

        assertThat(staff.getFailedLogins()).isZero();
        assertThat(staff.isLocked(now)).isTrue();
        assertThat(staff.isLocked(now.plus(Duration.ofMinutes(15)).minusSeconds(1)))
                .isTrue();
        assertThat(staff.isLocked(now.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void aSuccessfulLoginClearsFailuresAndTheLock() {
        Staff staff = receptionist();
        Instant now = Instant.parse("2026-01-01T08:00:00Z");
        staff.recordFailedLogin(now);
        for (int i = 0; i < Staff.MAX_FAILED_LOGINS; i++) {
            staff.recordFailedLogin(now);
        }

        staff.recordSuccessfulLogin();

        assertThat(staff.getFailedLogins()).isZero();
        assertThat(staff.getLockedUntil()).isNull();
    }

    @Test
    void onlyAnActiveAccountIsActive() {
        Staff staff = receptionist();
        assertThat(staff.isActive()).isTrue();

        staff.disable();

        assertThat(staff.isActive()).isFalse();
    }

    @Test
    void onlyDoctorsAndNursesNeedALicence() {
        assertThat(StaffRole.DOCTOR.requiresLicence()).isTrue();
        assertThat(StaffRole.NURSE.requiresLicence()).isTrue();
        assertThat(StaffRole.RECEPTIONIST.requiresLicence()).isFalse();
        assertThat(StaffRole.CASHIER.requiresLicence()).isFalse();
        assertThat(StaffRole.ADMIN.requiresLicence()).isFalse();
    }
}
